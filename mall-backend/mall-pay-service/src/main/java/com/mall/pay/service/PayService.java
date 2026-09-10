package com.mall.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BizException;
import com.mall.pay.entity.PayInfo;
import com.mall.pay.entity.Refund;
import com.mall.pay.gateway.AlipayGatewayClient;
import com.mall.pay.mapper.OrderPayMapper;
import com.mall.pay.mapper.PayInfoMapper;
import com.mall.pay.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 支付核心。
 *
 * 回调幂等（database-design §5 支付单 + 大纲任务 15 三件套）：
 *  ① Redisson 分布式锁 pay:notify:{payNo}（并发回调只进一个）
 *  ② pay_info 状态机：仅"待支付"可置成功（markSuccess 条件更新，0 行=重复回调直接返回成功）
 *  ③ out_trade_no 唯一索引兜底（入库层）
 *  前置：金额核对 amount == pay_info.amount（不一致拒绝入账）
 *
 * 单库演示约定（PayApplication 注释同步）：
 *  - 支付成功 / 退款状态同步直连同库 order/merchant 条件更新+入账，与支付单同一事务；
 *  - 生产演进：改发"订单已支付/退款成功/余额变动"事件，由 order/merchant 服务消费（TODO）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final PayInfoMapper payInfoMapper;
    private final RefundMapper refundMapper;
    private final OrderPayMapper orderPayMapper;
    private final AlipayGatewayClient alipayGatewayClient;
    private final RedissonClient redissonClient;

    // -----------------------------------------------------
    // 支付单创建（一单一支付单）
    // -----------------------------------------------------

    public String create(Long userId, String orderNo) {
        OrderPayMapper.OrderPayView order = orderPayMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BizException("订单当前状态不可支付");
        }
        // 幂等：按 order_no 查已有支付单（uk_order_no 兜底，重试返回原单）
        PayInfo exist = payInfoMapper.selectOne(new LambdaQueryWrapper<PayInfo>()
                .eq(PayInfo::getOrderNo, orderNo));
        if (exist != null && exist.getStatus() == 0) {
            return exist.getPayNo();
        }
        PayInfo pay = new PayInfo();
        pay.setPayNo(genNo("P"));
        pay.setOrderNo(orderNo);
        pay.setUserId(userId);
        pay.setAmount(order.getPayAmount());
        pay.setChannel(0);
        pay.setOutTradeNo(pay.getPayNo());
        pay.setStatus(0);
        payInfoMapper.insert(pay);
        // 渠道侧创建（Mock 同步返回；真实沙箱返回跳转 URL）
        alipayGatewayClient.createPay(pay.getPayNo(), pay.getOutTradeNo(), pay.getAmount());
        return pay.getPayNo();
    }

    // -----------------------------------------------------
    // 回调处理（Mock 触发或沙箱网关回调，统一入口）
    // -----------------------------------------------------

    @Transactional
    public void handleCallback(String payNo, String tradeNo, String body) {
        PayInfo pay = payInfoMapper.selectOne(new LambdaQueryWrapper<PayInfo>()
                .eq(PayInfo::getPayNo, payNo));
        if (pay == null) {
            throw new BizException("支付单不存在");
        }
        // 金额核对：渠道实付必须与支付单一致（不一致拒绝入账）
        if (body == null) {
            throw new BizException("回调参数缺失");
        }
        String notifyAmount = extractAmount(body);
        if (notifyAmount != null
                && new BigDecimal(notifyAmount).compareTo(pay.getAmount()) != 0) {
            throw new BizException("回调金额不一致，拒绝入账");
        }
        // 分布式锁（幂等三件套之一）
        RLock lock = redissonClient.getLock("pay:notify:" + payNo);
        boolean locked = false;
        try {
            try {
                locked = lock.tryLock(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("支付回调处理被中断");
            }
            if (!locked) {
                throw new BizException("支付回调处理中，稍后重试");
            }
            // 状态机：仅待支付可置成功；0 行=已处理（重复回调幂等返回）
            payInfoMapper.markSuccess(pay.getId(), tradeNo, body);
            // 订单同步 0→1（0 行=订单已取消：演示告警日志，真实系统应触发整单退款，见 database-design §4）
            int n = orderPayMapper.markPaid(pay.getOrderNo());
            if (n == 0) {
                log.warn("[PAY] 支付成功但订单状态同步失败(可能已取消): orderNo={} payNo={}", pay.getOrderNo(), payNo);
            } else {
                // 商家余额入账（balance 规则：支付成功+，database-design §4）
                Long merchantId = orderPayMapper.selectMerchantId(pay.getOrderNo());
                if (merchantId != null) {
                    orderPayMapper.addMerchantBalance(merchantId, pay.getAmount());
                }
                // 支付成功累加商品销量/已售（仅首次状态流转成功才执行，天然幂等）
                changeSaleCount(pay.getOrderNo(), true);
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    // -----------------------------------------------------
    // 退款（最小实现：申请即由 Mock 渠道立即成功；真实沙箱走渠道退款接口）
    // -----------------------------------------------------

    @Transactional
    public String refund(Long userId, String orderNo, String reason) {
        OrderPayMapper.OrderPayView order = orderPayMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        // 已支付(1)/已发货(2)/已收货(3) 均可申请退款（状态机合法线见 database-design §4）
        Integer st = order.getStatus();
        if (st == null || (st != 1 && st != 2 && st != 3)) {
            throw new BizException("当前状态不能申请退款");
        }
        // 一单一次退款：同单已有处理中/成功的退款单则拒绝
        long exists = refundMapper.selectCount(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderNo, orderNo)
                .in(Refund::getStatus, 0, 1));
        if (exists > 0) {
            throw new BizException("该订单已发起退款，请稍候");
        }
        PayInfo pay = payInfoMapper.selectOne(new LambdaQueryWrapper<PayInfo>()
                .eq(PayInfo::getOrderNo, orderNo));
        if (pay == null) {
            throw new BizException("支付单不存在");
        }
        Refund refund = new Refund();
        refund.setRefundNo(genNo("R"));
        refund.setPayNo(pay.getPayNo());
        refund.setOrderNo(orderNo);
        refund.setAmount(pay.getAmount());
        refund.setOutRequestNo(refund.getRefundNo());
        refund.setReason(reason);
        refund.setStatus(0);
        refundMapper.insert(refund);
        // 订单 1→5（退款中）条件更新
        orderPayMapper.markRefunding(orderNo);
        // Mock 渠道立即成功（真实渠道：异步回调 + 失败重试，结果置 refund 状态）
        String trade = alipayGatewayClient.refundPay(pay.getOutTradeNo(), refund.getOutRequestNo(), refund.getAmount());
        boolean ok = trade != null && trade.startsWith("MOCK-REFUND");
        if (ok) {
            refundMapper.markSuccess(refund.getId());
            int n = orderPayMapper.markRefunded(orderNo);
            if (n == 1) {
                // 退款扣回商家余额（balance 规则：支付+/退款-，见 database-design §4）
                Long merchantId = orderPayMapper.selectMerchantId(orderNo);
                if (merchantId != null) {
                    orderPayMapper.deductMerchantBalance(merchantId, refund.getAmount());
                }
                // 退款成功扣减商品销量/已售（仅首次状态流转成功才执行，天然幂等）
                changeSaleCount(orderNo, false);
            }
        } else {
            refundMapper.markFailed(refund.getId());
            throw new BizException("渠道退款失败，请稍后重试");
        }
        return refund.getRefundNo();
    }

    public boolean isPaid(String orderNo) {
        return payInfoMapper.selectCount(new LambdaQueryWrapper<PayInfo>()
                .eq(PayInfo::getOrderNo, orderNo).eq(PayInfo::getStatus, 1)) > 0;
    }

    // -----------------------------------------------------
    // 私有
    // -----------------------------------------------------

    /** Mock 回调体格式："amount=<数值>"，仅取纯数字部分 */
    private String extractAmount(String body) {
        int eq = body.indexOf('=');
        if (eq < 0) {
            return null;
        }
        String raw = body.substring(eq + 1).trim();
        // 丢弃任何非数字字符（如逗号/货币符号），演示渠道只带纯数字
        String cleaned = raw.replaceAll("[^0-9.]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String genNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** 按订单明细调整商品销量/已售：increase=true 支付成功累加，false 退款成功扣减 */
    private void changeSaleCount(String orderNo, boolean increase) {
        for (OrderPayMapper.OrderItemSale it : orderPayMapper.selectItems(orderNo)) {
            if (it.getProductId() == null || it.getCount() == null) {
                continue;
            }
            if (increase) {
                orderPayMapper.addSaleCount(it.getProductId(), it.getCount());
            } else {
                orderPayMapper.deductSaleCount(it.getProductId(), it.getCount());
            }
        }
    }
}
