package com.mall.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家（账号+入驻审核+店铺合并，物理保留）。写路径在本服务（认证字段 auth 侧只读校验）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseDO {

    private String username;

    private String password;

    /** 0 待审核 1 通过 2 驳回 */
    private Integer applyStatus;

    private String rejectReason;

    private LocalDateTime auditTime;

    private String merchantName;

    private String contact;

    private String phone;

    private String shopName;

    private String shopLogo;

    private String shopDesc;

    private String shopAddress;

    /** 0 营业 1 停业 */
    private Integer shopStatus;

    /** 0 正常 1 禁用 */
    private Integer status;

    /** 可提现余额：支付成功+/提现申请-/提现驳回+ */
    private BigDecimal balance;
}
