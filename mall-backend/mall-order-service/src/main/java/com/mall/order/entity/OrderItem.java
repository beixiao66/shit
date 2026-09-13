package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 订单明细（下单快照，物理保留） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseDO {

    private Long orderId;

    private String orderNo;

    private Long skuId;

    private Long productId;

    private String title;

    private String specJson;

    private BigDecimal price;

    private Integer count;

    private BigDecimal amount;

    /** 商品主图（非表字段：列表展示时实时读 product.main_img，见 OrderService#fillItemImg） */
    @TableField(exist = false)
    private String mainImg;
}
