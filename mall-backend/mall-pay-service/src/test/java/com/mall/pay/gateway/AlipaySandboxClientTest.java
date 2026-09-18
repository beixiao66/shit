package com.mall.pay.gateway;

import com.alipay.api.internal.util.AlipaySignature;
import com.mall.pay.config.AlipayProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlipaySandboxClient} 验签测试。
 *
 * <p>不需要真实沙箱密钥：本地生成一对 RSA2048 密钥，用私钥按支付宝的规则签名，
 * 再用 {@link AlipayProperties#setAlipayPublicKey} 注入对应公钥来验签。
 * 覆盖"支付宝异步通知能否被正确接受/拒绝"这一回调安全的第一道门。
 */
class AlipaySandboxClientTest {

    private static String privateKey;
    private static String publicKey;

    @BeforeAll
    static void genKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        // 支付宝要求 Base64 单行、不含 PEM 头尾
        privateKey = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    }

    private AlipaySandboxClient clientWith(String alipayPublicKey) {
        AlipayProperties props = new AlipayProperties();
        props.setEnabled(true);
        props.setAppId("2021000000000000");
        props.setPrivateKey(privateKey);
        props.setAlipayPublicKey(alipayPublicKey);
        props.setGatewayUrl("https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        return new AlipaySandboxClient(props);
    }

    /** 按支付宝规则构造通知参数：除 sign/sign_type 外全部参与签名，空值不参与 */
    private Map<String, String> signedNotify() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", "2021000000000000");
        params.put("out_trade_no", "P2609181119235789");
        params.put("trade_no", "2026091822001400000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "9897.00");
        params.put("gmt_payment", "2026-09-18 11:34:00");
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        params.put("notify_time", "2026-09-18 11:34:01");
        params.put("empty_should_be_ignored", "");   // 空值不参与签名，必须被正确忽略

        // 支付宝的待签串规则由 SDK 提供，确保与验签端一致
        String content = AlipaySignature.getSignCheckContentV1(new HashMap<>(params));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))));
        signer.update(content.getBytes("UTF-8"));
        params.put("sign", Base64.getEncoder().encodeToString(signer.sign()));
        return params;
    }

    @Test
    void verifyNotify_validSignature_passes() throws Exception {
        AlipaySandboxClient client = clientWith(publicKey);
        assertThat(client.verifyNotify(signedNotify())).isTrue();
    }

    @Test
    void verifyNotify_tamperedAmount_fails() throws Exception {
        AlipaySandboxClient client = clientWith(publicKey);
        Map<String, String> params = signedNotify();
        // 篡改金额（典型攻击：把 9897 改成 0.01）
        params.put("total_amount", "0.01");
        assertThat(client.verifyNotify(params)).isFalse();
    }

    @Test
    void verifyNotify_missingSign_fails() throws Exception {
        AlipaySandboxClient client = clientWith(publicKey);
        Map<String, String> params = signedNotify();
        params.remove("sign");
        assertThat(client.verifyNotify(params)).isFalse();
    }

    @Test
    void verifyNotify_wrongPublicKey_fails() throws Exception {
        // 换一对无关密钥的公钥：模拟"支付宝公钥填成了应用公钥"这个高频配错
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String otherPublicKey = Base64.getEncoder().encodeToString(gen.generateKeyPair().getPublic().getEncoded());
        AlipaySandboxClient client = clientWith(otherPublicKey);
        assertThat(client.verifyNotify(signedNotify())).isFalse();
    }

    @Test
    void channelMetadata_isAlipay() {
        AlipaySandboxClient client = clientWith(publicKey);
        assertThat(client.channelCode()).isZero();
        assertThat(client.isRealChannel()).isTrue();
    }

    /**
     * 回归测试：支付宝密钥生成工具导出的是 PEM（多行 + BEGIN/END 标记），
     * 而 SDK 的 AlipaySignature 只接受单行 Base64 —— 直接粘贴 PEM 会导致验签静默失败
     * （实测确认）。客户端构造函数必须做归一化。
     */
    @Test
    void verifyNotify_pemFormattedKeys_areNormalizedAndPass() throws Exception {
        AlipayProperties props = new AlipayProperties();
        props.setEnabled(true);
        props.setAppId("2021000000000000");
        // 模拟用户直接粘贴密钥工具的导出内容
        props.setPrivateKey(pem("PRIVATE KEY", privateKey));
        props.setAlipayPublicKey(pem("PUBLIC KEY", publicKey));
        AlipaySandboxClient client = new AlipaySandboxClient(props);

        // 用原始单行私钥签名，用 PEM 公钥（已在客户端内归一化）验签
        assertThat(client.verifyNotify(signedNotify())).isTrue();
    }

    @Test
    void normalizeKey_stripsPemHeadersAndWhitespace() {
        assertThat(AlipaySandboxClient.normalizeKey(pem("PUBLIC KEY", publicKey))).isEqualTo(publicKey);
        assertThat(AlipaySandboxClient.normalizeKey("  " + publicKey + "\n")).isEqualTo(publicKey);
        // 已经是单行 Base64 时保持原样
        assertThat(AlipaySandboxClient.normalizeKey(publicKey)).isEqualTo(publicKey);
        assertThat(AlipaySandboxClient.normalizeKey(null)).isNull();
    }

    /**
     * 回归测试：SDK 返回的 HTML 表单里 action 参数分隔符是 {@code &amp;}，
     * 不反转义会导致支付宝收到 {@code &amp;method=...} 当成参数名而返回 404（实测踩过）。
     */
    @Test
    void htmlUnescape_convertsEntitiesInGatewayUrl() {
        String escaped = "https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                + "?charset=UTF-8&amp;method=alipay.trade.page.pay&amp;sign=abc%3D%3D";
        String unescaped = AlipaySandboxClient.htmlUnescape(escaped);
        assertThat(unescaped).isEqualTo("https://openapi-sandbox.dl.alipaydev.com/gateway.do"
                + "?charset=UTF-8&method=alipay.trade.page.pay&sign=abc%3D%3D");
        assertThat(unescaped).doesNotContain("&amp;");
        // 无实体时原样返回；URL 编码的 %3D 等不能被误改
        assertThat(AlipaySandboxClient.htmlUnescape("a=1&b=2%3D%3D")).isEqualTo("a=1&b=2%3D%3D");
        assertThat(AlipaySandboxClient.htmlUnescape(null)).isNull();
    }

    /** 按密钥工具导出的样式格式化：PEM 头尾 + 每 64 字符换行 */
    private static String pem(String label, String base64) {
        StringBuilder sb = new StringBuilder("-----BEGIN ").append(label).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        return sb.append("-----END ").append(label).append("-----").toString();
    }

    @Test
    void properties_configuredAndEffective_onlyWhenAllKeysPresent() {
        AlipayProperties props = new AlipayProperties();
        // 默认 enabled=false：不生效
        assertThat(props.effective()).isFalse();
        props.setEnabled(true);
        props.setAppId("2021000000000000");
        // 只有 appId：仍不完整
        assertThat(props.configured()).isFalse();
        assertThat(props.effective()).isFalse();
        props.setPrivateKey(privateKey);
        props.setAlipayPublicKey(publicKey);
        assertThat(props.configured()).isTrue();
        assertThat(props.effective()).isTrue();
    }

    @Test
    void properties_resolveNotifyUrl_buildsGatewayPath() {
        AlipayProperties props = new AlipayProperties();
        props.setNotifyBase("https://demo.ngrok-free.app/");
        assertThat(props.resolveNotifyUrl()).isEqualTo("https://demo.ngrok-free.app/api/pay/alipay/notify");
        // 显式配置 notifyUrl 时优先使用
        props.setNotifyUrl("https://explicit.example.com/notify");
        assertThat(props.resolveNotifyUrl()).isEqualTo("https://explicit.example.com/notify");
    }
}
