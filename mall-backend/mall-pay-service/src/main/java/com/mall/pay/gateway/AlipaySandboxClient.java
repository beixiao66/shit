package com.mall.pay.gateway;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.mall.common.BizException;
import com.mall.pay.config.AlipayProperties;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支付宝开放平台沙箱渠道（真实接入，非 Mock）。
 *
 * <p>能力：
 * <ul>
 *   <li>{@code alipay.trade.page.pay} —— 电脑网站支付，生成跳转支付宝收银台的表单</li>
 *   <li>{@code alipay.trade.refund} —— 退款（out_request_no 幂等）</li>
 *   <li>{@code alipay.trade.close} —— 关单（订单超时取消后防止继续付款）</li>
 *   <li>{@link #verifyNotify} —— 异步通知 RSA2 验签（回调安全的第一道门）</li>
 * </ul>
 *
 * <p>线程安全：{@link AlipayClient} 内部持连接池，可单例复用。
 * 本类不依赖 Spring，方便单元测试直接 new（见 PayServiceTest 同目录测试）。
 */
@Slf4j
public class AlipaySandboxClient implements AlipayGatewayClient {

    private final AlipayProperties props;
    private final AlipayClient client;

    /** 从返回的表单 HTML 里抠出支付宝网关地址（action="..."） */
    private static final Pattern FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");

    public AlipaySandboxClient(AlipayProperties props) {
        this.props = props;
        this.client = new DefaultAlipayClient(
                props.getGatewayUrl(),
                props.getAppId(),
                props.getPrivateKey(),
                "json",
                props.getCharset(),
                props.getAlipayPublicKey(),
                props.getSignType());
        log.info("[ALIPAY] 沙箱渠道已启用: appId={}, gateway={}, notifyUrl={}",
                props.getAppId(), props.getGatewayUrl(), props.resolveNotifyUrl());
    }

    @Override
    public PayCreateResult createPay(String payNo, String orderNo, String subject, BigDecimal amount) {
        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        req.setReturnUrl(props.getReturnUrl());
        req.setNotifyUrl(props.resolveNotifyUrl());
        // 金额必须两位小数字符串，避免 1.0 被支付宝判为非法
        String amountStr = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String biz = "{"
                + "\"out_trade_no\":\"" + payNo + "\","
                + "\"total_amount\":\"" + amountStr + "\","
                + "\"subject\":\"" + escape(subject) + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\""
                + "}";
        req.setBizContent(biz);
        String payUrl = buildGatewayUrl(req);
        log.info("[ALIPAY] 创建沙箱支付: payNo={}, orderNo={}, amount={}", payNo, orderNo, amountStr);
        return new PayCreateResult(payNo, orderNo, amountStr, payUrl, true);
    }

    @Override
    public void closePay(String outTradeNo) {
        AlipayTradeCloseRequest req = new AlipayTradeCloseRequest();
        req.setBizContent("{\"out_trade_no\":\"" + outTradeNo + "\"}");
        try {
            AlipayTradeCloseResponse resp = client.execute(req);
            // 关单失败不阻断业务：可能已支付/已关闭，记日志即可
            if (!resp.isSuccess()) {
                log.warn("[ALIPAY] 关单未成功: outTradeNo={}, code={}, msg={}",
                        outTradeNo, resp.getSubCode(), resp.getSubMsg());
            } else {
                log.info("[ALIPAY] 关单成功: outTradeNo={}", outTradeNo);
            }
        } catch (AlipayApiException e) {
            log.warn("[ALIPAY] 关单异常: outTradeNo={}, err={}", outTradeNo, e.getMessage());
        }
    }

    @Override
    public String refundPay(String outTradeNo, String outRequestNo, BigDecimal amount, String reason) {
        AlipayTradeRefundRequest req = new AlipayTradeRefundRequest();
        String amountStr = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        StringBuilder biz = new StringBuilder("{")
                .append("\"out_trade_no\":\"").append(outTradeNo).append("\",")
                .append("\"refund_amount\":\"").append(amountStr).append("\",")
                // out_request_no 是支付宝侧退款幂等号：同号重复请求只退一次
                .append("\"out_request_no\":\"").append(outRequestNo).append("\"");
        if (reason != null && !reason.isBlank()) {
            biz.append(",\"refund_reason\":\"").append(escape(reason)).append("\"");
        }
        biz.append("}");
        req.setBizContent(biz.toString());
        try {
            AlipayTradeRefundResponse resp = client.execute(req);
            if (!resp.isSuccess()) {
                throw new BizException("支付宝退款失败: code=" + resp.getSubCode() + ", msg=" + resp.getSubMsg());
            }
            log.info("[ALIPAY] 退款成功: outTradeNo={}, outRequestNo={}, amount={}, tradeNo={}",
                    outTradeNo, outRequestNo, amountStr, resp.getTradeNo());
            // 支付宝未返回 refund_no 时用幂等号兜底，保证本系统有流水号可存
            return resp.getTradeNo() != null ? resp.getTradeNo() : outRequestNo;
        } catch (AlipayApiException e) {
            throw new BizException("支付宝退款异常: " + e.getMessage());
        }
    }

    @Override
    public int channelCode() {
        return 0;
    }

    @Override
    public boolean isRealChannel() {
        return true;
    }

    /**
     * 异步通知验签（回调安全第一道门）。
     * 必须用<b>原始参数 Map</b>（Spring 的 @RequestParam Map 会保留全部参数），
     * 且验签前不能对参数做任何加工，否则签名必然不通过。
     *
     * @return 验签通过返回 true
     */
    public boolean verifyNotify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params, props.getAlipayPublicKey(),
                    props.getCharset(), props.getSignType());
        } catch (AlipayApiException e) {
            log.warn("[ALIPAY] 验签异常: {}", e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------

    /** 生成支付宝网关跳转 URL（把 SDK 返回的表单 action 抽出来，便于前端 window.open） */
    private String buildGatewayUrl(AlipayTradePagePayRequest req) {
        try {
            String form = client.pageExecute(req).getBody();
            if (form == null || form.isBlank()) {
                throw new BizException("支付宝返回空表单，请检查 appId/私钥配置");
            }
            Matcher m = FORM_ACTION.matcher(form);
            if (m.find()) {
                return m.group(1);
            }
            // 兜底：返回原始表单（前端可用 form 提交方式打开）
            log.warn("[ALIPAY] 未从表单中解析出 action，返回原始表单");
            return form;
        } catch (AlipayApiException e) {
            throw new BizException("支付宝下单异常: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
