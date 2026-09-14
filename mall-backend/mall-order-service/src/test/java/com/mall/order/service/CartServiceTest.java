package com.mall.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mall.common.BizException;
import com.mall.order.dto.CartMergeRequest;
import com.mall.order.entity.Cart;
import com.mall.order.entity.SkuOrder;
import com.mall.order.mapper.CartMapper;
import com.mall.order.mapper.CartViewMapper;
import com.mall.order.mapper.SkuOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartMapper cartMapper;
    @Mock private CartViewMapper cartViewMapper;
    @Mock private SkuOrderMapper skuOrderMapper;
    @InjectMocks private CartService cartService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        TableInfoHelper.initTableInfo(assistant, Cart.class);
        TableInfoHelper.initTableInfo(assistant, SkuOrder.class);
    }

    // ----------------------------------------------------------
    // add
    // ----------------------------------------------------------

    @Test
    void add_newItem_inserts() {
        when(skuOrderMapper.selectActive(100L)).thenReturn(mockSku());
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(cartMapper.insert(any(Cart.class))).thenReturn(1);
        cartService.add(1001L, 100L, 2);
        verify(cartMapper).insert(argThat(c ->
                c.getUserId().equals(1001L)
                        && c.getSkuId().equals(100L)
                        && c.getCount().equals(2)
                        && c.getProductId().equals(9001L)));
    }

    @Test
    void add_existingItem_increments() {
        when(skuOrderMapper.selectActive(100L)).thenReturn(mockSku());
        Cart exist = new Cart();
        exist.setId(1L); exist.setUserId(1001L); exist.setSkuId(100L); exist.setCount(1);
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);
        when(cartMapper.updateById(any())).thenReturn(1);
        cartService.add(1001L, 100L, 3);
        verify(cartMapper).updateById(argThat(c -> c.getCount().equals(4)));
    }

    @Test
    void add_skuOffSale_throws() {
        when(skuOrderMapper.selectActive(100L)).thenReturn(null);
        assertThatThrownBy(() -> cartService.add(1001L, 100L, 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品已下架");
    }

    // ----------------------------------------------------------
    // merge
    // ----------------------------------------------------------

    @Test
    void merge_guestToUser_cartMerged() {
        when(skuOrderMapper.selectActive(100L)).thenReturn(mockSku());
        when(cartMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(cartMapper.insert(any(Cart.class))).thenReturn(1);
        CartMergeRequest.Item item = new CartMergeRequest.Item();
        item.setSkuId(100L); item.setCount(1);
        cartService.merge(1001L, List.of(item));
        verify(cartMapper).insert(any(Cart.class));
    }

    // ----------------------------------------------------------
    // helpers
    // ----------------------------------------------------------

    private SkuOrder mockSku() {
        SkuOrder sku = new SkuOrder();
        sku.setId(100L); sku.setProductId(9001L); sku.setPrice(new BigDecimal("99.00"));
        sku.setSpecJson("{\"color\":\"black\"}"); sku.setStock(50); sku.setStatus(0);
        return sku;
    }
}
