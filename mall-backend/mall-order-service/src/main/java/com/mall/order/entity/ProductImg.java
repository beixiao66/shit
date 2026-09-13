package com.mall.order.entity;

import lombok.Data;

/** 只读商品主图（来自 product 表，仅用于订单明细展示） */
@Data
public class ProductImg {

    private Long id;

    private String mainImg;
}
