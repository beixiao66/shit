package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.BizException;
import com.mall.product.dto.ProductForm;
import com.mall.product.dto.ProductQuery;
import com.mall.product.dto.SkuForm;
import com.mall.product.entity.MerchantShop;
import com.mall.product.entity.Product;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.MerchantShopMapper;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.vo.ProductDetailVO;
import com.mall.product.vo.ProductVO;
import com.mall.product.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final MerchantShopMapper merchantShopMapper;

    /** 前台分页列表：商品 + 最低价 + 店铺名 */
    public Page<ProductVO> page(ProductQuery q) {
        Page<Product> page = productMapper.selectPage(
                new Page<>(q.getPage(), q.getSize()),
                new LambdaQueryWrapper<Product>()
                        .eq(q.getCategoryId() != null, Product::getCategoryId, q.getCategoryId())
                        .eq(q.getMerchantId() != null, Product::getMerchantId, q.getMerchantId())
                        .eq(Product::getStatus, 0)
                        .and(q.getKeyword() != null && !q.getKeyword().isBlank(),
                                w -> w.like(Product::getTitle, q.getKeyword()))
                        .orderByDesc(Product::getCreateTime));
        List<ProductVO> vos = new ArrayList<>();
        if (!page.getRecords().isEmpty()) {
            Map<Long, BigDecimal> minPriceByProduct = minPriceMap(page.getRecords());
            for (Product p : page.getRecords()) {
                ProductVO vo = new ProductVO();
                vo.setId(p.getId());
                vo.setMerchantId(p.getMerchantId());
                vo.setCategoryId(p.getCategoryId());
                vo.setTitle(p.getTitle());
                vo.setSubtitle(p.getSubtitle());
                vo.setMainImg(p.getMainImg());
                vo.setMinPrice(minPriceByProduct.get(p.getId()));
                vo.setStatus(p.getStatus());
                vo.setSaleCount(p.getSaleCount());
                vo.setShopName(shopName(p.getMerchantId()));
                vos.add(vo);
            }
        }
        Page<ProductVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    /** 商品详情：SKU 全量返回（规格选择由前端过滤，见数据库文档注 1） */
    public ProductDetailVO detail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException("商品不存在或已下架");
        }
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, id)
                .orderByAsc(Sku::getId));
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(p.getId());
        vo.setMerchantId(p.getMerchantId());
        vo.setCategoryId(p.getCategoryId());
        vo.setTitle(p.getTitle());
        vo.setSubtitle(p.getSubtitle());
        vo.setMainImg(p.getMainImg());
        vo.setDetail(p.getDetail());
        vo.setMinPrice(skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        vo.setShopName(shopName(p.getMerchantId()));
        vo.setSaleCount(p.getSaleCount());
        vo.setCreateTime(p.getCreateTime());
        vo.setSkus(skus);
        return vo;
    }

    /** 商家：我的商品列表 */
    public Page<ProductVO> merchantPage(Long merchantId, int page, int size) {
        Page<Product> p = productMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .orderByDesc(Product::getCreateTime));
        Map<Long, BigDecimal> minMap = minPriceMap(p.getRecords());
        List<ProductVO> records = p.getRecords().stream().map(pr -> {
            ProductVO vo = new ProductVO();
            vo.setId(pr.getId());
            vo.setMerchantId(pr.getMerchantId());
            vo.setCategoryId(pr.getCategoryId());
            vo.setTitle(pr.getTitle());
            vo.setMainImg(pr.getMainImg());
            vo.setMinPrice(minMap.get(pr.getId()));
            vo.setStatus(pr.getStatus());
            vo.setSaleCount(pr.getSaleCount());
            return vo;
        }).collect(Collectors.toList());
        Page<ProductVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(records);
        return result;
    }

    /** 新建商品（连同 SKU）；merchantId 来自网关 X-User-Id */
    @Transactional
    public Long create(Long merchantId, ProductForm form) {
        Product p = new Product();
        p.setMerchantId(merchantId);
        p.setCategoryId(form.getCategoryId());
        p.setTitle(form.getTitle());
        p.setSubtitle(form.getSubtitle());
        p.setMainImg(form.getMainImg());
        p.setDetail(form.getDetail());
        p.setStatus(form.getStatus() == null ? 0 : form.getStatus());
        p.setSaleCount(0);
        productMapper.insert(p);
        saveSkus(p.getId(), form.getSkus());
        return p.getId();
    }

    /** 编辑：更新属性，SKU 重建（先物理删旧再插新；演示规模可行） */
    @Transactional
    public void update(Long merchantId, Long id, ProductForm form) {
        Product p = requireOwner(merchantId, id);
        p.setCategoryId(form.getCategoryId());
        p.setTitle(form.getTitle());
        p.setSubtitle(form.getSubtitle());
        p.setMainImg(form.getMainImg());
        p.setDetail(form.getDetail());
        if (form.getStatus() != null) {
            p.setStatus(form.getStatus());
        }
        productMapper.updateById(p);
        skuMapper.delete(new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, id));
        saveSkus(id, form.getSkus());
    }

    public void updateStatus(Long merchantId, Long id, Integer status) {
        Product p = requireOwner(merchantId, id);
        p.setStatus(status);
        productMapper.updateById(p);
    }

    /** 删除：商品逻辑删，SKU 物理删（唯一约束互斥说明见数据库文档） */
    @Transactional
    public void delete(Long merchantId, Long id) {
        requireOwner(merchantId, id);
        productMapper.deleteById(id);
        skuMapper.delete(new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, id));
    }

    private Product requireOwner(Long merchantId, Long id) {
        Product p = productMapper.selectById(id);
        if (p == null || !p.getMerchantId().equals(merchantId)) {
            throw new BizException(403, "商品不存在或无权限");
        }
        return p;
    }

    private void saveSkus(Long productId, List<SkuForm> skuForms) {
        if (skuForms == null || skuForms.isEmpty()) {
            throw new BizException("至少一个规格");
        }
        for (SkuForm sf : skuForms) {
            Sku sku = new Sku();
            sku.setProductId(productId);
            sku.setSpecJson(sf.getSpecJson());
            sku.setPrice(sf.getPrice());
            sku.setStock(sf.getStock() == null ? 0 : sf.getStock());
            sku.setImg(null);
            sku.setStatus(sf.getStatus() == null ? 0 : sf.getStatus());
            sku.setRemark(sf.getRemark());
            skuMapper.insert(sku); // uk_product_spec 唯一索引兜底重复规格，异常由全局异常处理返回
        }
    }

    /** 商品集最低价：group by product_id */
    private Map<Long, BigDecimal> minPriceMap(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .in(Sku::getProductId, ids));
        return skus.stream()
                .collect(Collectors.toMap(Sku::getProductId, Sku::getPrice, BigDecimal::min));
    }

    /** 店铺名只读查询（读模型例外，见 MerchantShopMapper 注释） */
    private String shopName(Long merchantId) {
        MerchantShop merchant = merchantShopMapper.selectNameById(merchantId);
        String name = merchant == null ? "" : merchant.getShopName();
        if (name == null || name.isBlank()) {
            name = merchant == null ? "" : merchant.getMerchantName();
        }
        return name == null ? "" : name;
    }

    /** 店铺信息（前台店铺页头部） */
    public ShopVO shop(Long merchantId) {
        MerchantShop m = merchantShopMapper.selectShopById(merchantId);
        if (m == null) {
            throw new BizException("店铺不存在");
        }
        ShopVO vo = new ShopVO();
        vo.setMerchantId(m.getId());
        String name = (m.getShopName() != null && !m.getShopName().isBlank()) ? m.getShopName() : m.getMerchantName();
        vo.setShopName(name == null ? "" : name);
        vo.setShopLogo(m.getShopLogo());
        vo.setShopDesc(m.getShopDesc());
        vo.setShopAddress(m.getShopAddress());
        vo.setShopStatus(m.getShopStatus());
        return vo;
    }
}
