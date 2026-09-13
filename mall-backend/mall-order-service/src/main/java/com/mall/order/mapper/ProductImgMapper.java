package com.mall.order.mapper;

import com.mall.order.entity.ProductImg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 只读 product.main_img（订单明细展示商品图，读模型约定，同 ProductTitleMapper） */
@Mapper
public interface ProductImgMapper {

    @Select("<script>" +
            "SELECT id, main_img FROM product WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>" +
            "</script>")
    List<ProductImg> selectImgByKeys(@Param("ids") List<Long> ids);
}
