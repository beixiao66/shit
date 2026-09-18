package com.mall.pay.controller;

import com.mall.common.MallConstants;
import com.mall.common.Result;
import com.mall.pay.entity.PayInfo;
import com.mall.pay.gateway.PayCreateResult;
import com.mall.pay.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户支付接口（经网关访问，网关已验证 JWT type=0）。
 *
 * <p>渠道说明：
 * <ul>
 *   <li>沙箱模式（{@code mall.alipay.enabled=true}）：{@code /create} 返回支付宝收银台 URL，
 *       用户付款后支付宝回调 {@code /alipay/notify}（验签后入账）</li>
 *   <li>Mock 模式（默认）：{@code /create} 返回演示页 URL，由 {@code /mock/callback} 触发同一入账链路</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * 创建支付单：返回支付凭证（payNo + 支付跳转 URL + 是否真实渠道）。
     */
    @PostMapping("/create")
    public Result<PayCreateResult> create(@RequestHeader(MallConstants.HEADER_USER_ID) Long userId,
                                          @RequestBody Map<String, String> body) {
        return Result.ok(payService.create(userId, body.get("orderNo")));
    }

    /**
     * 查询支付凭证（前端刷新支付页时用）。
     * 注意：不返回可跳转 URL —— 待支付时前端应再调一次 {@code /create} 取渠道凭证，
     * 避免这里凭空拼出一个无效的支付宝地址。
     */
    @GetMapping("/info/{orderNo}")
    public Result<PayCreateResult> info(@PathVariable String orderNo) {
        PayInfo pay = payService.findByOrderNo(orderNo);
        if (pay == null) {
            return Result.ok(null);
        }
        return Result.ok(new PayCreateResult(pay.getPayNo(), orderNo,
                pay.getAmount() == null ? null : pay.getAmount().toPlainString(),
                null, payService.isRealChannel()));
    }

    /**
     * 演示用模拟回调（Mock 渠道；沙箱模式请用 /alipay/notify）。
     * 入参 {payNo, tradeNo, amount}：amount 与支付单不一致则拒绝入账（金额核对）。
     */
    @PostMapping("/mock/callback")
    public Result<Void> mockCallback(@RequestBody Map<String, String> body) {
        payService.handleCallback(body.get("payNo"), body.get("tradeNo"), "amount=" + body.get("amount"));
        return Result.ok();
    }

    /**
     * 支付宝异步通知（沙箱/生产通用入口）。
     *
     * <p>关键约束：
     * <ol>
     *   <li>必须是 {@code application/x-www-form-urlencoded} 的原始参数，用 Map 全量接收</li>
     *   <li>应答体必须是纯文本 {@code success}（支付宝据此判断是否重试），
     *       不能包成统一 JSON 响应体</li>
     *   <li>处理顺序：验签 → 状态校验 → 金额核对 → 幂等入账（见 PayService.handleAlipayNotify）</li>
     * </ol>
     */
    @PostMapping(value = "/alipay/notify", produces = "text/plain;charset=UTF-8")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        // 避免把签名等敏感参数整体打日志，仅记录关键字段
        log.info("[PAY] 收到支付宝异步通知: outTradeNo={}, tradeStatus={}",
                params.get("out_trade_no"), params.get("trade_status"));
        try {
            boolean ok = payService.handleAlipayNotify(new HashMap<>(params));
            return ok ? "success" : "failure";
        } catch (Exception e) {
            log.error("[PAY] 支付宝异步通知处理异常", e);
            return "failure";
        }
    }

    @GetMapping("/status/{orderNo}")
    public Result<Boolean> status(@PathVariable String orderNo) {
        return Result.ok(payService.isPaid(orderNo));
    }

    /** 退款申请（沙箱走 alipay.trade.refund；Mock 即时成功） */
    @PostMapping("/refund")
    public Result<String> refund(@RequestHeader(MallConstants.HEADER_USER_ID) Long userId,
                                 @RequestBody Map<String, String> body) {
        return Result.ok(payService.refund(userId, body.get("orderNo"), body.get("reason")));
    }
}
