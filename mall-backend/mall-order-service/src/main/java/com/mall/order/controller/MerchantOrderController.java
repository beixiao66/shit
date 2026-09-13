package com.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.MallConstants;
import com.mall.common.Result;
import com.mall.order.dto.ShipRequest;
import com.mall.order.service.OrderService;
import com.mall.order.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 商家订单（网关已验证 type=2；仅操作本店订单） */
@RestController
@RequestMapping("/api/merchant/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderService orderService;

    /** keyword 支持订单号/收货人/商品名模糊搜索 */
    @GetMapping
    public Result<Page<OrderVO>> list(@RequestHeader(MallConstants.HEADER_USER_ID) Long merchantId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.pageByMerchant(merchantId, status, keyword, page, size));
    }

    @PutMapping("/{orderNo}/ship")
    public Result<Void> ship(@RequestHeader(MallConstants.HEADER_USER_ID) Long merchantId,
                             @PathVariable String orderNo,
                             @Valid @RequestBody ShipRequest req) {
        orderService.ship(merchantId, orderNo, req.getLogisticsCompany(), req.getTrackingNo());
        return Result.ok();
    }
}
