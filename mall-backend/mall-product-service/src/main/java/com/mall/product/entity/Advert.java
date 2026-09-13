package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.BaseLogicDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 首页轮播广告位（逻辑删除；纯展示无跳转链接） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("advert")
public class Advert extends BaseLogicDO {

    private String title;

    private String imgUrl;

    private Integer sort;

    /** 0 启用 1 停用 */
    private Integer status;
}
