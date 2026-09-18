package com.mall.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙箱配置。
 *
 * <p>密钥获取步骤（沙箱控制台 → https://open.alipay.com/develop/sandbox/app）：
 * <ol>
 *   <li>appId / 支付宝网关：控制台「沙箱应用」页直接可见</li>
 *   <li>应用私钥：用「支付宝密钥生成工具」生成 RSA2 密钥对，把<b>应用私钥</b>填到
 *       {@code private-key}（PKCS8，单行 Base64，不含头尾）</li>
 *   <li>支付宝公钥：把上一步生成的<b>应用公钥</b>填进控制台，控制台会给出
 *       <b>支付宝公钥</b>，填到 {@code alipay-public-key}（PKIX，单行 Base64）</li>
 * </ol>
 *
 * <p>建议用环境变量注入，不要把私钥提交进仓库：
 * <pre>
 * MALL_ALIPAY_APP_ID / MALL_ALIPAY_PRIVATE_KEY / MALL_ALIPAY_PUBLIC_KEY / MALL_ALIPAY_NOTIFY_BASE
 * </pre>
 * 或本地覆盖文件 {@code application-local.yml}（已加入 .gitignore）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.alipay")
public class AlipayProperties {

    /** 总开关：false 时走 Mock 渠道（本地无密钥也能跑通全流程） */
    private boolean enabled = false;

    /** 应用 APPID（沙箱应用页可见） */
    private String appId;

    /** 应用私钥（RSA2 / PKCS8，单行 Base64） */
    private String privateKey;

    /** 支付宝公钥（RSA2 / PKIX，单行 Base64，用于回调验签） */
    private String alipayPublicKey;

    /** 支付宝网关：沙箱用 openapi-sandbox，生产用 openapi */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 签名算法 */
    private String signType = "RSA2";

    /** 字符集 */
    private String charset = "UTF-8";

    /** 支付成功同步跳转地址（用户付款后浏览器跳回本系统） */
    private String returnUrl = "http://localhost:5173/pay/result";

    /**
     * 异步通知基地址：必须是<b>支付宝服务器能访问到</b>的地址。
     * 本机开发用内网穿透（ngrok / cpolar）把 8090 暴露出去后填公网地址，
     * 例如 {@code https://xxxx.ngrok-free.app}，实际通知地址为
     * {@code {notifyBase}/api/pay/alipay/notify}。
     */
    private String notifyBase = "http://localhost:8090";

    /** 支付宝异步通知地址（留空则用 notifyBase 拼接） */
    private String notifyUrl;

    /** 发起支付时默认的商品标题前缀 */
    private String subjectPrefix = "Mall-X 订单 ";

    /** 是否已具备真实渠道条件（三个必填项齐全） */
    public boolean configured() {
        return notBlank(appId) && notBlank(privateKey) && notBlank(alipayPublicKey);
    }

    /** 真实沙箱渠道是否生效 */
    public boolean effective() {
        return enabled && configured();
    }

    public String resolveNotifyUrl() {
        if (notBlank(notifyUrl)) {
            return notifyUrl;
        }
        String base = notifyBase == null ? "" : notifyBase.replaceAll("/+$", "");
        return base + "/api/pay/alipay/notify";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
