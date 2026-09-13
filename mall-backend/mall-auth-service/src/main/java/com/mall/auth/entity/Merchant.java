package com.mall.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家（账号+入驻审核+店铺合并，物理保留）。
 * 仅本服务需要认证字段；业务维度（店铺信息）由 merchant-service 持有。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseDO {

    private String username;

    private String password;

    /** 入驻审核：0 待审核 1 通过 2 驳回 */
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

    /** 账号状态：0 正常 1 禁用 */
    private Integer status;

    /** 可提现余额：支付成功+/提现申请-/提现驳回+ */
    private BigDecimal balance;
}
