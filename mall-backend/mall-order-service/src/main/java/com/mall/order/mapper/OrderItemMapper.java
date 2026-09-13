package com.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /** 商品名模糊匹配的订单号（订单搜索：订单号 OR 商品名） */
    @Select("SELECT DISTINCT order_no FROM order_item WHERE title LIKE CONCAT('%', #{keyword}, '%')")
    List<String> selectOrderNosByTitleLike(@Param("keyword") String keyword);
}
