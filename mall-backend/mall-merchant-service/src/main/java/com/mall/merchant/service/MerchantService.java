package com.mall.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BizException;
import com.mall.merchant.entity.Merchant;
import com.mall.merchant.mapper.MerchantMapper;
import com.mall.merchant.vo.MerchantProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 商家信息/店铺信息/入驻审核（审核动作含幂等条件更新） */
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;

    public MerchantProfileVO profile(Long merchantId) {
        Merchant m = require(merchantId);
        MerchantProfileVO vo = new MerchantProfileVO();
        vo.setId(m.getId());
        vo.setMerchantName(m.getMerchantName());
        vo.setContact(m.getContact());
        vo.setPhone(m.getPhone());
        vo.setShopName(m.getShopName());
        vo.setShopLogo(m.getShopLogo());
        vo.setShopDesc(m.getShopDesc());
        vo.setShopAddress(m.getShopAddress());
        vo.setShopStatus(m.getShopStatus());
        vo.setBalance(m.getBalance());
        vo.setApplyStatus(m.getApplyStatus());
        vo.setStatus(m.getStatus());
        return vo;
    }

    public void updateShop(Long merchantId, MerchantPatch patch) {
        Merchant m = require(merchantId);
        if (patch.getShopName() != null) m.setShopName(patch.getShopName());
        if (patch.getShopLogo() != null) m.setShopLogo(patch.getShopLogo());
        if (patch.getShopDesc() != null) m.setShopDesc(patch.getShopDesc());
        if (patch.getShopAddress() != null) m.setShopAddress(patch.getShopAddress());
        if (patch.getShopStatus() != null) m.setShopStatus(patch.getShopStatus());
        merchantMapper.updateById(m);
    }

    /**
     * 入驻审核：pass=1 通过；pass=0 驳回（记录原因）。
     * 条件更新 apply_status=0 -> 目标，重复审核影响 0 行（幂等，不会覆盖既有结果）。
     */
    public void audit(Long merchantId, boolean pass, String reason) {
        Merchant m = require(merchantId);
        if (m.getApplyStatus() == 0) {
            m.setApplyStatus(pass ? 1 : 2);
            m.setAuditTime(java.time.LocalDateTime.now());
            m.setRejectReason(pass ? null : reason);
            if (pass) {
                m.setStatus(0);
            }
            merchantMapper.updateById(m);
        } else {
            throw new BizException("该商家已审核过");
        }
    }

    public void addBalance(Long merchantId, BigDecimal amount) {
        merchantMapper.addBalance(merchantId, amount);
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Merchant> auditPage(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Merchant> lw,
            int page, int size) {
        return merchantMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), lw);
    }

    private Merchant require(Long merchantId) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            throw new BizException(403, "商家不存在");
        }
        return m;
    }

    @lombok.Data
    public static class MerchantPatch {
        private String shopName;
        private String shopLogo;
        private String shopDesc;
        private String shopAddress;
        private Integer shopStatus;
    }
}
