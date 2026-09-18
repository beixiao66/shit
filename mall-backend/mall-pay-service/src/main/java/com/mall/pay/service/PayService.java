package com.mall.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BizException;
import com.mall.pay.entity.PayInfo;
import com.mall.pay.entity.Refund;
import com.mall.pay.gateway.AlipayGatewayClient;
import com.mall.pay.gateway.AlipaySandboxClient;
import com.mall.pay.gateway.PayCreateResult;
import com.mall.pay.mapper.OrderPayMapper;
import com.mall.pay.mapper.PayInfoMapper;
import com.mall.pay.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 支付核心。
 *
 * <p>支持两种渠道，业务链路完全复用（差异只在"谁证明钱到账"）：
 * <ul>
 *   <li><b>支付宝沙箱</b>（{@code mall.alipay.enabled=true}）：创建支付返回支付宝
 *       收银台 URL，用户付款后支付宝异步通知 → RSA2 验签 → 本类入账</li>
 *   <li><b>Mock</b>（默认）：本地无密钥演示，由 {@code /api/pay/mock/callback} 触发同一入账链路</li>
 * </ul>
 *
 * <p>回调幂等（database-design §5 支付单 + 大纲任务 15 三件套）：
 * <ol>
 *   <li>RSA2 验签（沙箱渠道）—— 证明通知确实来自支付宝，防伪造回调</li>
 *   <li>Redisson 分布式锁 {@code pay:notify:{payNo}}（并发回调只进一个）</li>
 *   <li>{@code pay_info} 状态机：仅"待支付"可置成功（{@code markSuccess} 条件更新，
 *       0 行=重复回调，直接返回成功）</li>
 *   <li>{@code out_trade_no} 唯一索引兜底（入库层）</li>
 * </ol>
 * 前置：金额核对 amount == pay_info.amount（不一致拒绝入账）。
 *
 * <p>单库演示约定（PayApplication 注释同步）：
 * 支付成功/退款状态同步直连同库 order/merchant 条件更新+入账，与支付单同一事务；
 * 生产演进：改发"订单已支付/退款成功/余额变动"事件，由 order/merchant 服务消费（TODO）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    /** 支付宝异步通知里的交易状态：仅这两个值代表钱真的到账了 */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";

    private final PayInfoMapper payInfoMapper;
    private final RefundMapper refundMapper;
    private final OrderPayMapper orderPayMapper;
    private final AlipayGatewayClient alipayGatewayClient;
    private final RedissonClient redissonClient;

    /**
     * 沙箱客户端（可选）：仅 {@code mall.alipay.enabled=true} 时存在。
     * 用 ObjectProvider 而非强注入，保证 Mock 模式（无沙箱 Bean）也能启动。
     */
    private final ObjectProvider<AlipaySandboxClient> sandboxProvider;

    /** 当前是否真实沙箱渠道（前端据此决定展示"跳转支付宝"还是"演示按钮"） */
    public boolean isRealChannel() {
        return alipayGatewayClient.isRealChannel();
    }

    // -----------------------------------------------------
    // 支付单创建（一单一支付单）
    // -----------------------------------------------------

    /**
     * 创建支付单并调用渠道下单。
     *
     * @return 支付凭证（含 payNo、支付跳转 URL、是否真实渠道）
     */
    public PayCreateResult create(Long userId, String orderNo) {
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
            // 已下单未支付：重新取一次渠道凭证（支付宝 URL 可重复使用，Mock 幂等）
            return alipayGatewayClient.createPay(
                    exist.getPayNo(), orderNo, subject(orderNo), exist.getAmount());
        }
        PayInfo pay = new PayInfo();
        pay.setPayNo(genNo("P"));
        pay.setOrderNo(orderNo);
        pay.setUserId(userId);
        pay.setAmount(order.getPayAmount());
        // 渠道号由渠道实现给出（沙箱/Mock 都是 0 支付宝）
        pay.setChannel(alipayGatewayClient.channelCode());
        // out_trade_no 用本系统支付单号：唯一索引 + 回调按它反查，幂等锚点
        pay.setOutTradeNo(pay.getPayNo());
        pay.setStatus(0);
        payInfoMapper.insert(pay);
        // 渠道侧下单：沙箱返回支付宝收银台 URL，Mock 返回演示页 URL
        return alipayGatewayClient.createPay(
                pay.getPayNo(), orderNo, subject(orderNo), pay.getAmount());
    }

    // -----------------------------------------------------
    // 回调处理（Mock 触发 或 支付宝异步通知，统一入口）
    // -----------------------------------------------------

    /**
     * 处理支付成功回调（渠道无关的统一入账链路）。
     *
     * @param payNo   本系统支付单号（out_trade_no）
     * @param tradeNo 渠道交易流水号
     * @param body    回调原文摘要（入库审计用）
     */
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
                log.warn("[PAY] 支付成功但订单状态同步失败(可能已取消): orderNo={} payNo={}",
                        pay.getOrderNo(), payNo);
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

    /**
     * 处理支付宝异步通知（{@code /api/pay/alipay/notify}）。
     *
     * <p>安全顺序（不可调换）：<b>先验签 → 再校验 app_id/金额 → 最后才入账</b>。
     * 任何一步不通过都返回 false（控制器回 "failure"，支付宝会按策略重试）。
     *
     * @param params 支付宝 POST 的原始参数（不能加工，否则验签必失败）
     * @return true=已正确处理（应答 "success"，支付宝停止重试）
     */
    @Transactional
    public boolean handleAlipayNotify(Map<String, String> params) {
        AlipaySandboxClient sandbox = sandboxProvider.getIfAvailable();
        if (sandbox == null) {
            log.warn("[PAY] 收到支付宝回调但当前未启用沙箱渠道，已忽略");
            return false;
        }
        // ① 验签：证明通知来自支付宝（防伪造回调）
        if (!sandbox.verifyNotify(params)) {
            log.warn("[PAY] 支付宝回调验签失败，已拒绝: outTradeNo={}", params.get("out_trade_no"));
            return false;
        }
        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        log.info("[PAY] 支付宝回调验签通过: outTradeNo={}, tradeStatus={}, tradeNo={}",
                outTradeNo, tradeStatus, tradeNo);
        // ② 交易状态：仅成功态才入账；其余（WAIT_BUYER_PAY/TRADE_CLOSED）直接应答成功
        if (!TRADE_SUCCESS.equals(tradeStatus) && !TRADE_FINISHED.equals(tradeStatus)) {
            log.info("[PAY] 支付宝回调非成功态，已应答但不入账: outTradeNo={}, status={}",
                    outTradeNo, tradeStatus);
            return true;
        }
        if (outTradeNo == null || outTradeNo.isBlank()) {
            log.warn("[PAY] 支付宝回调缺少 out_trade_no，已拒绝");
            return false;
        }
        // ③ 金额核对 + 幂等入账（复用与 Mock 完全相同的链路）
        //    回调体格式化为 "amount=<数值>"，与 extractAmount 的解析约定一致
        String body = "amount=" + params.getOrDefault("total_amount", "");
        try {
            handleCallback(outTradeNo, tradeNo, body);
        } catch (BizException e) {
            // 金额不一致等业务拒绝：记录并返回 failure，让支付宝重试/人工介入
            log.error("[PAY] 支付宝回调入账被拒: outTradeNo={}, reason={}", outTradeNo, e.getMessage());
            return false;
        }
        return true;
    }

    // -----------------------------------------------------
    // 退款
    // -----------------------------------------------------

    /**
     * 退款申请：本地建退款单 → 调渠道退款 → 成功则同步订单与商家余额。
     * 沙箱渠道的失败由 {@link AlipayGatewayClient#refundPay} 抛异常表达。
     */
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
        // out_request_no 是渠道侧幂等号（uk_out_request_no 唯一索引兜底）
        refund.setOutRequestNo(refund.getRefundNo());
        refund.setReason(reason);
        refund.setStatus(0);
        refundMapper.insert(refund);
        // 订单 1→5（退款中）条件更新
        orderPayMapper.markRefunding(orderNo);
        // 渠道退款：失败抛 BizException（沙箱异步结果由支付宝退款接口同步返回）
        String tradeNo = alipayGatewayClient.refundPay(
                pay.getOutTradeNo(), refund.getOutRequestNo(), refund.getAmount(), reason);
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
        return tradeNo;
    }

    public boolean isPaid(String orderNo) {
        return payInfoMapper.selectCount(new LambdaQueryWrapper<PayInfo>()
                .eq(PayInfo::getOrderNo, orderNo).eq(PayInfo::getStatus, 1)) > 0;
    }

    /** 按订单号查支付单（前端展示支付凭证用） */
    public PayInfo findByOrderNo(String orderNo) {
        return payInfoMapper.selectOne(new LambdaQueryWrapper<PayInfo>()
                .eq(PayInfo::getOrderNo, orderNo));
    }

    /** 订单超时取消/用户取消时关闭渠道支付单，避免对已取消订单继续付款 */
    public void closePay(String orderNo) {
        PayInfo pay = findByOrderNo(orderNo);
        if (pay == null || pay.getStatus() != 0) {
            return;
        }
        alipayGatewayClient.closePay(pay.getOutTradeNo());
        payInfoMapper.markClosed(pay.getId());
    }

    // -----------------------------------------------------
    // 私有
    // -----------------------------------------------------

    /** 商品标题：支付宝收银台展示用 */
    private String subject(String orderNo) {
        return "Mall-X 订单 " + orderNo;
    }

    /** 回调体格式："amount=<数值>"，仅取纯数字部分（兼容 Mock 与支付宝通知的包装格式） */
    private String extractAmount(String body) {
        int eq = body.indexOf('=');
        if (eq < 0) {
            return null;
        }
        String raw = body.substring(eq + 1).trim();
        // 丢弃任何非数字字符（如逗号/货币符号）
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
