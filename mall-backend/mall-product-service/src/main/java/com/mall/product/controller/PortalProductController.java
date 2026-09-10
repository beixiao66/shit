package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.Result;
import com.mall.product.dto.ProductQuery;
import com.mall.product.entity.Advert;
import com.mall.product.entity.Category;
import com.mall.product.service.AdvertService;
import com.mall.product.service.CategoryService;
import com.mall.product.service.ProductService;
import com.mall.product.vo.ProductDetailVO;
import com.mall.product.vo.ProductVO;
import com.mall.product.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 商城前台浏览接口（网关游客白名单，无需 token） */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalProductController {

    private final CategoryService categoryService;
    private final AdvertService advertService;
    private final ProductService productService;

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(categoryService.list());
    }

    @GetMapping("/adverts")
    public Result<List<Advert>> adverts() {
        return Result.ok(advertService.listActive());
    }

    @GetMapping("/products")
    public Result<Page<ProductVO>> products(ProductQuery query) {
        return Result.ok(productService.page(query));
    }

    @GetMapping("/products/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    @GetMapping("/shops/{merchantId}")
    public Result<ShopVO> shop(@PathVariable Long merchantId) {
        return Result.ok(productService.shop(merchantId));
    }
}
