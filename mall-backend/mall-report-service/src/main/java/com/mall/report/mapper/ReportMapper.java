package com.mall.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表聚合（只读直连，参数 merchantId 区分全局/本店看板）：
 * 口径（database-design §5）：营收 = order.status IN (1,2,3) 的 pay_amount 合计；
 * 进账 = pay_info 成功金额；出账 = refund 成功 + withdrawal 待处理/成功。
 * 全部走 idx_create_time 索引，未启用中间凉表（无 finance_log 决策）。
 */
@Mapper
public interface ReportMapper {

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM `order` WHERE status IN (1,2,3)"
            + " AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})")
    BigDecimal revenue(@Param("merchantId") Long merchantId);

    @Select("SELECT COUNT(*) FROM merchant WHERE apply_status = 1"
            + " AND (#{merchantId} IS NULL OR id = #{merchantId})")
    Long merchantCount(@Param("merchantId") Long merchantId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM pay_info WHERE status = 1" +
            " AND (#{merchantId} IS NULL OR order_no IN " +
            " (SELECT order_no FROM `order` WHERE merchant_id = #{merchantId}))")
    BigDecimal incoming(@Param("merchantId") Long merchantId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM refund WHERE status = 1" +
            " AND (#{merchantId} IS NULL OR order_no IN " +
            " (SELECT order_no FROM `order` WHERE merchant_id = #{merchantId}))")
    BigDecimal refundOut(@Param("merchantId") Long merchantId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM withdrawal WHERE status IN (0,1)" +
            " AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})")
    BigDecimal withdrawalOut(@Param("merchantId") Long merchantId);

    @Select("SELECT COUNT(*) FROM withdrawal WHERE status IN (0,1)" +
            " AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})")
    Long withdrawalCount(@Param("merchantId") Long merchantId);

    /**
     * 近 N 个自然日逐日订单金额（7 日趋势）。
     * startDay 由 Service 按"含今天在内的 N 个自然日"算好（前端图表 x 轴同为自然日，口径保持一致）。
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, " +
            "       COALESCE(SUM(pay_amount),0) AS amount, COUNT(*) AS cnt " +
            "FROM `order` WHERE status IN (1,2,3)" +
            " AND create_time >= #{startDay}" +
            " AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})" +
            " GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY day")
    List<Map<String, Object>> dailyTrend(@Param("merchantId") Long merchantId,
                                         @Param("startDay") LocalDate startDay);

    /** 入驻统计：商家数/待审数/驳回数/上月新增 */
    @Select("SELECT SUM(CASE WHEN apply_status=1 THEN 1 ELSE 0 END) AS approved," +
            "       SUM(CASE WHEN apply_status=0 THEN 1 ELSE 0 END) AS pending," +
            "       SUM(CASE WHEN apply_status=2 THEN 1 ELSE 0 END) AS rejected" +
            " FROM merchant")
    Map<String, Object> merchantStats();

    /** 进出账明细（近 20 条混合标签：订单进 / 退款出 / 提现出） */
    @Select("SELECT 'order_in' AS biz, order_no AS ref, pay_amount AS amount, create_time " +
            "FROM `order` WHERE status IN (1,2,3)" +
            " AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})" +
            " UNION ALL " +
            "SELECT 'refund_out', order_no, amount, create_time FROM refund WHERE status=1" +
            " AND (#{merchantId} IS NULL OR order_no IN (SELECT order_no FROM `order` WHERE merchant_id = #{merchantId}))" +
            " UNION ALL " +
            "SELECT 'withdrawal_out', withdrawal_no, amount, create_time FROM withdrawal WHERE status IN (0,1)" +
            " AND (#{merchantId} IS NULL OR merchant_id = #{merchantId})" +
            " ORDER BY create_time DESC LIMIT 20")
    List<Map<String, Object>> financeFlow(@Param("merchantId") Long merchantId);
}
