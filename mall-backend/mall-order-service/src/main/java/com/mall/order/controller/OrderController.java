package com.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.MallConstants;
import com.mall.common.Result;
import com.mall.order.dto.CreateOrderRequest;
import com.mall.order.service.OrderService;
import com.mall.order.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 用户订单（网关已验证 type=0） */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单：返回拆单后的订单号列表（跨店购物车一单多单） */
    @PostMapping("/create")
    public Result<List<String>> create(@RequestHeader(MallConstants.HEADER_USER_ID) Long userId,
                                       @Valid @RequestBody CreateOrderRequest req) {
        return Result.ok(orderService.create(userId, req));
    }

    /** 我的订单：keyword 支持订单号/商品名模糊搜索，sort=asc|desc 按下单时间排序 */
    @GetMapping("/list")
    public Result<Page<OrderVO>> list(@RequestHeader(MallConstants.HEADER_USER_ID) Long userId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "desc") String sort,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.pageByUser(userId, status, keyword, sort, page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @PutMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@RequestHeader(MallConstants.HEADER_USER_ID) Long userId,
                               @PathVariable String orderNo) {
        orderService.cancel(orderNo, userId);
        return Result.ok();
    }

    @PutMapping("/{orderNo}/receive")
    public Result<Void> receive(@RequestHeader(MallConstants.HEADER_USER_ID) Long userId,
                                @PathVariable String orderNo) {
        orderService.confirmReceive(userId, orderNo);
        return Result.ok();
    }
}
