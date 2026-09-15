package com.mall.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BizException;
import com.mall.order.dto.CreateOrderRequest;
import com.mall.order.entity.*;
import com.mall.order.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private CartMapper cartMapper;
    @Mock private SkuOrderMapper skuOrderMapper;
    @Mock private ProductMerchantMapper productMerchantMapper;
    @Mock private ProductTitleMapper productTitleMapper;
    @Mock private ProductImgMapper productImgMapper;
    @Mock private PayInfoMapper payInfoMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DefaultRedisScript<Long> stockDeductScript;
    @Mock private DefaultRedisScript<Long> stockRefundScript;

    @InjectMocks
    private OrderService orderService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        TableInfoHelper.initTableInfo(assistant, OrderDO.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
    }

    private void setupIdempotent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(true);
    }

    // ----------------------------------------------------------
    // 下单幂等
    // ----------------------------------------------------------

    @Test
    void create_duplicateReqId_throwsBizException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(false);
        CreateOrderRequest req = buildOrderRequest();
        assertThatThrownBy(() -> orderService.create(1001L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请勿重复提交订单");
    }

    // ----------------------------------------------------------
    // 库存不足 - SKU 下架
    // ----------------------------------------------------------

    @Test
    void create_skuNotOnSale_throwsBizException() {
        setupIdempotent();
        when(skuOrderMapper.selectActiveBatch(anyList()))
                .thenReturn(Collections.emptyList());
        CreateOrderRequest req = buildOrderRequest();
        assertThatThrownBy(() -> orderService.create(1001L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("部分商品已下架");
    }

    // ----------------------------------------------------------
    // 库存不足 - Redis 预扣失败
    // ----------------------------------------------------------

    @Test
    void create_redisStockInsufficient_throwsBizException() {
        setupIdempotent();
        SkuOrder sku = mockSkuOrder(100L, 9001L, new BigDecimal("99.00"));
        when(skuOrderMapper.selectActiveBatch(anyList())).thenReturn(List.of(sku));
        ProductMerchant pm = new ProductMerchant();
        pm.setId(9001L); pm.setMerchantId(100L);
        when(productMerchantMapper.selectByKeys(anyList())).thenReturn(List.of(pm));
        when(productTitleMapper.selectTitle(9001L)).thenReturn("测试商品");
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenReturn(-2L);
        CreateOrderRequest req = buildOrderRequest();
        assertThatThrownBy(() -> orderService.create(1001L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
    }

    // ----------------------------------------------------------
    // 下单成功
    // ----------------------------------------------------------

    @Test
    void create_success_returnsOrderNoList() {
        setupIdempotent();
        SkuOrder sku = mockSkuOrder(100L, 9001L, new BigDecimal("99.00"));
        when(skuOrderMapper.selectActiveBatch(anyList())).thenReturn(List.of(sku));
        ProductMerchant pm = new ProductMerchant();
        pm.setId(9001L); pm.setMerchantId(100L);
        when(productMerchantMapper.selectByKeys(anyList())).thenReturn(List.of(pm));
        when(productTitleMapper.selectTitle(9001L)).thenReturn("测试商品");
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenReturn(9L);
        when(skuOrderMapper.deductStock(eq(100L), eq(2))).thenReturn(1);
        doAnswer(inv -> {
            OrderDO o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        }).when(orderMapper).insert(any(OrderDO.class));

        CreateOrderRequest req = buildOrderRequest();
        List<String> orderNos = orderService.create(1001L, req);
        assertThat(orderNos).hasSize(1);
        assertThat(orderNos.get(0)).isNotBlank();
        verify(orderMapper).insert(any(OrderDO.class));
        verify(orderItemMapper).insert(any(OrderItem.class));
    }

    // ----------------------------------------------------------
    // 取消订单状态机
    // ----------------------------------------------------------

    @Test
    void cancel_orderNotExist_throwsBizException() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> orderService.cancel("NONEXIST", 1001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    void cancel_paidOrder_notCancellable() {
        OrderDO order = mockOrderDO(1001L, 1);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        orderService.cancel("ORD001", 1001L);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void cancel_unpaidOrder_updatesStatus() {
        OrderDO order = mockOrderDO(1001L, 0);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(payInfoMapper.closePending("ORD001")).thenReturn(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        orderService.cancel("ORD001", 1001L);
        verify(orderMapper).update(any(), any());
    }

    // ----------------------------------------------------------
    // 发货 / 确认收货
    // ----------------------------------------------------------

    @Test
    void ship_statusNotPaid_throwsBizException() {
        when(orderMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> orderService.ship(100L, "ORD001", "顺丰", "SF123"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能发货");
    }

    @Test
    void confirmReceive_statusNotShipped_throwsBizException() {
        when(orderMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> orderService.confirmReceive(1001L, "ORD001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能确认收货");
    }

    @Test
    void confirmReceive_success() {
        when(orderMapper.update(any(), any())).thenReturn(1);
        orderService.confirmReceive(1001L, "ORD001");
        verify(orderMapper).update(any(), any());
    }

    // ----------------------------------------------------------
    // helpers
    // ----------------------------------------------------------

    private CreateOrderRequest buildOrderRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setReqId("req-" + System.nanoTime());
        CreateOrderRequest.Item item = new CreateOrderRequest.Item();
        item.setSkuId(100L);
        item.setCount(2);
        req.setItems(List.of(item));
        req.setReceiverName("张三");
        req.setReceiverPhone("13800138000");
        req.setReceiverAddress("测试地址");
        req.setFromCart(false);
        return req;
    }

    private SkuOrder mockSkuOrder(Long skuId, Long productId, BigDecimal price) {
        SkuOrder sku = new SkuOrder();
        sku.setId(skuId); sku.setProductId(productId);
        sku.setSpecJson("{\"color\":\"black\"}");
        sku.setPrice(price); sku.setStock(50); sku.setStatus(0);
        return sku;
    }

    private OrderDO mockOrderDO(Long userId, Integer status) {
        OrderDO order = new OrderDO();
        order.setId(1L); order.setOrderNo("ORD001");
        order.setUserId(userId); order.setMerchantId(100L); order.setStatus(status);
        order.setTotalAmount(new BigDecimal("99.00")); order.setPayAmount(new BigDecimal("99.00"));
        return order;
    }
}
