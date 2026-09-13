package com.mall.product.mapper;

import com.mall.product.entity.MerchantShop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /** 店铺名模糊匹配的商家 id（前台搜索"商品名 OR 店铺名"用） */
    @Select("SELECT id FROM merchant WHERE shop_name LIKE CONCAT('%', #{keyword}, '%')")
    List<Long> selectIdsByShopNameLike(@Param("keyword") String keyword);

    /** 店铺名模糊匹配的店铺（前台"相关店铺"直接进店用；仅审核通过且营业中） */
    @Select("SELECT id, merchant_name, shop_name, shop_logo, shop_desc, shop_address, shop_status "
            + "FROM merchant WHERE shop_name LIKE CONCAT('%', #{keyword}, '%') "
            + "AND apply_status = 1 AND shop_status = 0 ORDER BY id LIMIT #{limit}")
    List<MerchantShop> selectByShopNameLike(@Param("keyword") String keyword, @Param("limit") int limit);
}
