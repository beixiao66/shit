package com.mall.pay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 直连同库 order 表（支付链路状态同步，单库演示约定）：
 * 支付成功 -> 订单 0→1；退款申请 -> 订单 1→5；退款成功 -> 5→6。
 * 全部条件更新：并发下状态机原子；跨服务消息编排为生产演进项（TODO 注释）。
 */
@Mapper
public interface OrderPayMapper {

    @Select("SELECT status, pay_amount FROM `order` WHERE order_no = #{orderNo}")
    OrderPayView selectByOrderNo(@Param("orderNo") String orderNo);

    /** 订单归属商家（余额入账/扣回需要） */
    @Select("SELECT merchant_id FROM `order` WHERE order_no = #{orderNo}")
    Long selectMerchantId(@Param("orderNo") String orderNo);

    /** 支付成功同步订单：仅待支付可置已支付（0 行=订单已取消/已发货，不重复入账） */
    @Update("UPDATE `order` SET status = 1, last_status = 0, pay_time = NOW() " +
            "WHERE order_no = #{orderNo} AND status = 0")
    int markPaid(@Param("orderNo") String orderNo);

    /** 退款申请：已支付(1)/已发货(2)/已收货(3) 均可置退款中（状态机扩展线） */
    @Update("UPDATE `order` SET status = 5, last_status = status " +
            "WHERE order_no = #{orderNo} AND status IN (1, 2, 3)")
    int markRefunding(@Param("orderNo") String orderNo);

    /** 退款成功：仅退款中可置已退款 */
    @Update("UPDATE `order` SET status = 6, last_status = 5 WHERE order_no = #{orderNo} AND status = 5")
    int markRefunded(@Param("orderNo") String orderNo);

    /** 商家余额入账（同库直写；生产应发 balance 事件，见 MerchantBalanceMapper 注释） */
    @Update("UPDATE merchant SET balance = balance + #{amount} WHERE id = #{merchantId}")
    int addMerchantBalance(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);

    /** 退款扣回商家余额（0 行=金额不足时由日志告警，演示默认可扣） */
    @Update("UPDATE merchant SET balance = balance - #{amount} WHERE id = #{merchantId} AND balance >= #{amount}")
    int deductMerchantBalance(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);

    /** 订单明细（销量按商品累加/扣减需要 product_id + count） */
    @Select("SELECT product_id, `count` FROM order_item WHERE order_no = #{orderNo}")
    List<OrderItemSale> selectItems(@Param("orderNo") String orderNo);

    /** 支付成功累加商品销量（同库直写 product.sale_count，单库演示约定） */
    @Update("UPDATE product SET sale_count = sale_count + #{count} WHERE id = #{productId}")
    int addSaleCount(@Param("productId") Long productId, @Param("count") int count);

    /** 退款成功扣减商品销量（不小于 0） */
    @Update("UPDATE product SET sale_count = GREATEST(sale_count - #{count}, 0) WHERE id = #{productId}")
    int deductSaleCount(@Param("productId") Long productId, @Param("count") int count);

    @lombok.Data
    class OrderPayView {
        private Integer status;
        private BigDecimal payAmount;
    }

    @lombok.Data
    class OrderItemSale {
        private Long productId;
        private Integer count;
    }
}
