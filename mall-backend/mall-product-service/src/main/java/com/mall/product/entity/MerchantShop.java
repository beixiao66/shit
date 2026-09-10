package com.mall.product.entity;

import lombok.Data;

/** 只读商家店铺信息（来自 merchant 表，仅用于前台展示） */
@Data
public class MerchantShop {

    private Long id;

    private String merchantName;

    private String shopName;

    private String shopLogo;

    private String shopDesc;

    private String shopAddress;

    private Integer shopStatus;
}
