package com.mall.pay.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mall.common.BizException;
import com.mall.pay.entity.PayInfo;
import com.mall.pay.entity.Refund;
import com.mall.pay.gateway.AlipayGatewayClient;
import com.mall.pay.mapper.OrderPayMapper;
import com.mall.pay.mapper.PayInfoMapper;
import com.mall.pay.mapper.RefundMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayServiceTest {

    @Mock private PayInfoMapper payInfoMapper;
    @Mock private RefundMapper refundMapper;
    @Mock private OrderPayMapper orderPayMapper;
    @Mock private AlipayGatewayClient alipayGatewayClient;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;

    @InjectMocks
    private PayService payService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        TableInfoHelper.initTableInfo(assistant, PayInfo.class);
        TableInfoHelper.initTableInfo(assistant, Refund.class);
    }

    // ----------------------------------------------------------
    // create
    // ----------------------------------------------------------

    @Test
    void create_orderNotFound_throws() {
        when(orderPayMapper.selectByOrderNo("ORD_X")).thenReturn(null);
        assertThatThrownBy(() -> payService.create(1001L, "ORD_X"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    void create_orderStatusNotZero_throws() {
        OrderPayMapper.OrderPayView order = new OrderPayMapper.OrderPayView();
        order.setStatus(1); order.setPayAmount(new BigDecimal("100"));
        when(orderPayMapper.selectByOrderNo("ORD001")).thenReturn(order);
        assertThatThrownBy(() -> payService.create(1001L, "ORD001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可支付");
    }

    @Test
    void create_existingPendingPayInfo_returnsExisting() {
        OrderPayMapper.OrderPayView order = new OrderPayMapper.OrderPayView();
        order.setStatus(0); order.setPayAmount(new BigDecimal("100"));
        when(orderPayMapper.selectByOrderNo("ORD001")).thenReturn(order);
        PayInfo exist = new PayInfo();
        exist.setPayNo("P_EXISTING"); exist.setStatus(0);
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);
        String payNo = payService.create(1001L, "ORD001");
        assertThat(payNo).isEqualTo("P_EXISTING");
        verify(payInfoMapper, never()).insert(any());
    }

    @Test
    void create_success_insertsPayInfo() {
        OrderPayMapper.OrderPayView order = new OrderPayMapper.OrderPayView();
        order.setStatus(0); order.setPayAmount(new BigDecimal("100"));
        when(orderPayMapper.selectByOrderNo("ORD001")).thenReturn(order);
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(payInfoMapper.insert(any(PayInfo.class))).thenReturn(1);
        when(alipayGatewayClient.createPay(anyString(), anyString(), any(BigDecimal.class)))
                .thenReturn("MOCK-URL");
        String payNo = payService.create(1001L, "ORD001");
        assertThat(payNo).startsWith("P");
        verify(payInfoMapper).insert(any(PayInfo.class));
        verify(alipayGatewayClient).createPay(anyString(), anyString(), any(BigDecimal.class));
    }

    // ----------------------------------------------------------
    // handleCallback
    // ----------------------------------------------------------

    @Test
    void handleCallback_payInfoNotFound_throws() {
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> payService.handleCallback("P001", "TRADE001", "amount=100"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("支付单不存在");
    }

    @Test
    void handleCallback_bodyNull_throws() {
        PayInfo pay = mockPayInfo(1L, 0);
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pay);
        assertThatThrownBy(() -> payService.handleCallback("P001", "TRADE001", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回调参数缺失");
    }

    @Test
    void handleCallback_amountMismatch_throws() {
        PayInfo pay = mockPayInfo(1L, 0);
        pay.setAmount(new BigDecimal("100"));
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pay);
        assertThatThrownBy(() -> payService.handleCallback("P001", "TRADE001", "amount=99.99"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("金额不一致");
    }

    @Test
    void handleCallback_lockFails_throws() throws InterruptedException {
        PayInfo pay = mockPayInfo(1L, 0);
        pay.setAmount(new BigDecimal("100"));
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pay);
        when(redissonClient.getLock("pay:notify:P001")).thenReturn(rLock);
        when(rLock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(false);
        assertThatThrownBy(() -> payService.handleCallback("P001", "TRADE001", "amount=100"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("稍后重试");
    }

    @Test
    void handleCallback_success_orderSynced() throws InterruptedException {
        PayInfo pay = mockPayInfo(1L, 0);
        pay.setOrderNo("ORD001");
        pay.setAmount(new BigDecimal("100"));
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pay);
        when(redissonClient.getLock("pay:notify:P001")).thenReturn(rLock);
        when(rLock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(payInfoMapper.markSuccess(1L, "TRADE001", "amount=100")).thenReturn(1);
        when(orderPayMapper.markPaid("ORD001")).thenReturn(1);
        when(orderPayMapper.selectMerchantId("ORD001")).thenReturn(100L);
        when(orderPayMapper.selectItems("ORD001")).thenReturn(Collections.emptyList());
        payService.handleCallback("P001", "TRADE001", "amount=100");
        verify(orderPayMapper).markPaid("ORD001");
        verify(orderPayMapper).addMerchantBalance(eq(100L), any(BigDecimal.class));
    }

    @Test
    void handleCallback_idempotent_noOrderUpdate() throws InterruptedException {
        PayInfo pay = mockPayInfo(1L, 0);
        pay.setOrderNo("ORD001");
        pay.setAmount(new BigDecimal("100"));
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pay);
        when(redissonClient.getLock("pay:notify:P001")).thenReturn(rLock);
        when(rLock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(payInfoMapper.markSuccess(1L, "TRADE001", "amount=100")).thenReturn(0);
        when(orderPayMapper.markPaid("ORD001")).thenReturn(0);
        payService.handleCallback("P001", "TRADE001", "amount=100");
        verify(orderPayMapper).markPaid("ORD001");
        verify(orderPayMapper, never()).addMerchantBalance(anyLong(), any());
    }

    // ----------------------------------------------------------
    // refund
    // ----------------------------------------------------------

    @Test
    void refund_orderNotFound_throws() {
        when(orderPayMapper.selectByOrderNo("ORD_X")).thenReturn(null);
        assertThatThrownBy(() -> payService.refund(1001L, "ORD_X", "不想要了"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    void refund_statusInvalid_throws() {
        OrderPayMapper.OrderPayView order = mockOrderPayView(0);
        when(orderPayMapper.selectByOrderNo("ORD001")).thenReturn(order);
        assertThatThrownBy(() -> payService.refund(1001L, "ORD001", "不想要了"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能申请退款");
    }

    @Test
    void refund_alreadyRefunding_throws() {
        OrderPayMapper.OrderPayView order = mockOrderPayView(1);
        when(orderPayMapper.selectByOrderNo("ORD001")).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> payService.refund(1001L, "ORD001", "不想要了"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已发起退款");
    }

    @Test
    void refund_success_insertsRefundAndMarksOrder() {
        OrderPayMapper.OrderPayView order = mockOrderPayView(1);
        when(orderPayMapper.selectByOrderNo("ORD001")).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        PayInfo pay = mockPayInfo(1L, 1);
        pay.setPayNo("P001"); pay.setOrderNo("ORD001");
        pay.setAmount(new BigDecimal("100")); pay.setOutTradeNo("P001");
        when(payInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pay);
        when(refundMapper.insert(any(Refund.class))).thenReturn(1);
        when(orderPayMapper.markRefunding("ORD001")).thenReturn(1);
        when(alipayGatewayClient.refundPay(eq("P001"), anyString(), any(BigDecimal.class)))
                .thenReturn("MOCK-REFUND-001");
        when(refundMapper.markSuccess(any())).thenReturn(1);
        when(orderPayMapper.markRefunded("ORD001")).thenReturn(1);
        when(orderPayMapper.selectMerchantId("ORD001")).thenReturn(100L);
        when(orderPayMapper.selectItems("ORD001")).thenReturn(Collections.emptyList());
        String refundNo = payService.refund(1001L, "ORD001", "不想要了");
        assertThat(refundNo).startsWith("R");
        verify(orderPayMapper).markRefunding("ORD001");
        verify(orderPayMapper).markRefunded("ORD001");
        verify(orderPayMapper).deductMerchantBalance(eq(100L), any(BigDecimal.class));
    }

    // ----------------------------------------------------------
    // isPaid
    // ----------------------------------------------------------

    @Test
    void isPaid_true() {
        when(payInfoMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThat(payService.isPaid("ORD001")).isTrue();
    }

    @Test
    void isPaid_false() {
        when(payInfoMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertThat(payService.isPaid("ORD001")).isFalse();
    }

    // ----------------------------------------------------------
    // helpers
    // ----------------------------------------------------------

    private PayInfo mockPayInfo(Long id, Integer status) {
        PayInfo pay = new PayInfo();
        pay.setId(id); pay.setPayNo("P001"); pay.setStatus(status);
        pay.setAmount(new BigDecimal("100"));
        return pay;
    }

    private OrderPayMapper.OrderPayView mockOrderPayView(Integer status) {
        OrderPayMapper.OrderPayView order = new OrderPayMapper.OrderPayView();
        order.setStatus(status); order.setPayAmount(new BigDecimal("100"));
        return order;
    }
}
