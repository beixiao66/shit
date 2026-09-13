package com.mall.report.service;

import com.mall.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    /** 数据看板（商家传自己的 merchantId = 本店口径；管理员不传 = 全平台口径） */
    public Map<String, Object> dashboard(Long merchantId) {
        BigDecimal revenue = reportMapper.revenue(merchantId);
        BigDecimal incoming = reportMapper.incoming(merchantId);
        BigDecimal refundOut = reportMapper.refundOut(merchantId);
        BigDecimal withdrawalOut = reportMapper.withdrawalOut(merchantId);
        Map<String, Object> result = new HashMap<>();
        result.put("revenue", revenue);                       // 营收
        result.put("incoming", incoming);                     // 进账
        result.put("refundOut", refundOut);                   // 退款出账
        result.put("withdrawalOut", withdrawalOut);           // 提现出账
        result.put("outgoing", refundOut.add(withdrawalOut)); // 出账合计
        result.put("merchantCount", merchantId == null ? reportMapper.merchantCount(null) : 1L);
        result.put("withdrawalCount", reportMapper.withdrawalCount(merchantId));
        // 近 7 个自然日（含今天）：起始日与前端 x 轴补零的日期范围一致
        result.put("dailyTrend", reportMapper.dailyTrend(merchantId, LocalDate.now().minusDays(6)));
        result.put("merchantStats", merchantId == null ? reportMapper.merchantStats() : null);
        result.put("financeFlow", reportMapper.financeFlow(merchantId));
        return result;
    }
}
