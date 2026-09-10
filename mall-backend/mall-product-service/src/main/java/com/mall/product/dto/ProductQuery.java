package com.mall.product.dto;

import lombok.Data;

/** 商品列表查询（前台） */
@Data
public class ProductQuery {

    private Integer page = 1;

    private Integer size = 12;

    private Long categoryId;

    /** 按商家（店铺）筛选 */
    private Long merchantId;

    /** 关键字：LIKE 商品名（演示不引入 ES，解答口径见数据库文档） */
    private String keyword;
}
