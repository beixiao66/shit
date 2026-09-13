package com.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.Result;
import com.mall.order.dto.ShipRequest;
import com.mall.order.service.OrderService;
import com.mall.order.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 平台订单管理（网关已验证 type=1）：全局查看 + 代发货（大纲任务 8 平台管理员物流发货） */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /** keyword 支持订单号/收货人/商品名模糊搜索 */
    @GetMapping
    public Result<Page<OrderVO>> list(@RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.adminPage(status, keyword, page, size));
    }

    @PutMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo,
                             @Valid @RequestBody ShipRequest req) {
        orderService.ship(null, orderNo, req.getLogisticsCompany(), req.getTrackingNo());
        return Result.ok();
    }
}
