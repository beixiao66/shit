package com.mall.product.mapper;

import com.mall.product.entity.MerchantShop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 只读查询商家店铺信息（读模型约定）。
 * 说明：微服务下写路径不跨服务；此处仅为商品列表/详情/店铺页展示"店铺"信息，用原生只读 SQL。
 */
@Mapper
public interface MerchantShopMapper {

    @Select("SELECT id, merchant_name, shop_name FROM merchant WHERE id = #{id}")
    MerchantShop selectNameById(Long id);

    @Select("SELECT id, merchant_name, shop_name, shop_logo, shop_desc, shop_address, shop_status "
            + "FROM merchant WHERE id = #{id}")
    MerchantShop selectShopById(Long id);
}
