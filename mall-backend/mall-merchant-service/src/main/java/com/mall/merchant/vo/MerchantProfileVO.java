package com.mall.merchant.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 商家资料（不含密码/敏感字段） */
@Data
public class MerchantProfileVO {

    private Long id;

    private String merchantName;

    private String contact;

    private String phone;

    private String shopName;

    private String shopLogo;

    private String shopDesc;

    private String shopAddress;

    private Integer shopStatus;

    private BigDecimal balance;

    private Integer applyStatus;

    private Integer status;
}
