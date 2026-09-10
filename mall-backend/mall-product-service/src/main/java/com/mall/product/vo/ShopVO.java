package com.mall.product.vo;

import lombok.Data;

/** 店铺信息（前台展示：店铺页头部） */
@Data
public class ShopVO {

    private Long merchantId;

    private String shopName;

    private String shopLogo;

    private String shopDesc;

    private String shopAddress;

    private Integer shopStatus;
}
