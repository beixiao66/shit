# Mall-X 数据库设计文档（13 表精缩版）

> 依据《行业工程实践》教学大纲任务 3 编写。建表脚本：[docs/init.sql](init.sql)（DDL + 种子数据：10 商家 / 113 商品 / 270 SKU / 5 类目）。
> 导入务必带 `--default-character-set=utf8mb4`，否则中文种子数据报 Data too long：`mysql -uroot -p --default-character-set=utf8mb4 < docs/init.sql`。
> 修订记录：v2 从 22 表裁剪至 13 表（最小可完成业务）；公共字段抽 BaseDO（§1）；无平台佣金；分库分表仅设计文档。
> 约定：MySQL 8，utf8mb4；金额 DECIMAL(12,2)；时间 DATETIME；状态 TINYINT。

## 1. BaseDO —— 公共字段约定

所有表共用的字段抽为 Java 基类（MyBatis-Plus 下全表继承，两层结构）：

```java
public class BaseDO implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;                    // 雪花主键
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;   // 创建时间（MetaObjectHandler 自动填充）
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;   // 更新时间（自动填充）
}

public class BaseLogicDO extends BaseDO {   // 仅内容类表继承
    @TableLogic
    private Integer deleted;            // 逻辑删除 0=正常 1=删除
}
```

> 为什么拆两层：MyBatis-Plus 的 `@TableLogic` 逐类生效，若全表共用同一基类，账务表也会被拼上 `deleted=0` 过滤条件（而表里没有该列）导致 SQL 报错。逻辑删除只留给"可增删的内容类数据"。

**标注规则**（下文各表不再重复列公共字段，只列业务字段）：

| BaseDO 级别 | 字段 | 适用表 | 标注 |
|---|---|---|---|
| BaseDO（全表） | id / createTime / updateTime | **全部 13 张表** | ⚪ 全表默认 |
| BaseLogicDO（内容类） | deleted 逻辑删除 | category、product、advert、user_address | 🔵 仅 4 张内容表（无业务唯一约束，软删不冲突） |
| —（物理保留） | 不用逻辑删除 | user、merchant、withdrawal、order、order_item、pay_info、refund | ⚪ 账号/账务表物理保留 |
| —（内容但物理删） | 不用逻辑删除 | sku、cart | ⚪ 有业务唯一约束（`uk_product_spec` / `uk_user_sku`）：软删后重建同规格会撞唯一索引，与 cart 同理 |

> 注 1：sku、cart 与"逻辑删除"互斥——判据是**是否有业务唯一键**（软删行还在表里，重建同键值必然冲突）。
> 注 2：`status` **不抽** BaseDO——每张表状态枚举语义不同（用户启停、商家入驻、商品上下架、订单流转、支付状态……），单独定义，见各表。

## 2. 表清单（13 张）

| 域 | 表 | 说明 |
|---|---|---|
| 用户 | user | 普通用户 + 平台管理员（role 区分） |
| 用户 | user_address | 收货地址 |
| 商家 | merchant | 账号 + 入驻审核 + 店铺 + 收款码（合并原 4 表） |
| 商家 | withdrawal | 提现单（含银行卡快照，合并原 bank_card） |
| 商品 | category | 类目 |
| 商品 | product | 商品主表 |
| 商品 | sku | 规格（SKU 双表） |
| 商品 | advert | 广告位 |
| 订单 | cart | 购物车（登录后线上部分，物理删除） |
| 订单 | order | 订单主表（含物流字段、状态机、地址快照；合并原 delivery、order_status_log） |
| 订单 | order_item | 订单明细（下单快照） |
| 支付 | pay_info | 支付单（含回调幂等字段；合并原 pay_notify_log） |
| 支付 | refund | 退款单 |

## 3. 各表业务字段

### user（用户/管理员）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| username | VARCHAR(32) | UNIQUE NOT NULL | 登录名 |
| password | VARCHAR(128) | NOT NULL | BCrypt |
| nickname | VARCHAR(64) | | |
| avatar | VARCHAR(255) | | |
| phone | VARCHAR(20) | | |
| email | VARCHAR(64) | | |
| role | TINYINT | 0 用户 1 管理员 | **原 employee 表并入**：管理员登录后台靠 role 过滤 |
| status | TINYINT | 0 正常 1 禁用 | |

### user_address（收货地址）
🔵 BaseDO

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT, INDEX | |
| receiver | VARCHAR(32) NOT NULL | 收件人 |
| phone | VARCHAR(20) NOT NULL | |
| province / city / district | VARCHAR(32) | 文本存储，不做省市区表 |
| detail | VARCHAR(255) | 详细地址 |
| is_default | TINYINT | 0/1 |

### merchant（商家：账号+入驻审核+店铺+收款码）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| username | VARCHAR(32) | UNIQUE NOT NULL | 商家登录账号 |
| password | VARCHAR(128) | NOT NULL | |
| apply_status | TINYINT | 0 待审核 1 通过 2 驳回 | **原 merchant_apply 并入** |
| reject_reason | VARCHAR(200) | | 驳回原因 |
| audit_time | DATETIME | | 审核时间 |
| merchant_name | VARCHAR(64) | NOT NULL | 商家名 |
| contact / phone | VARCHAR(32) / (20) | | 联系人 |
| shop_name | VARCHAR(64) | | **原 shop 并入**（一商家一店） |
| shop_logo | VARCHAR(255) | | |
| shop_desc | TEXT | | |
| shop_address | VARCHAR(255) | | |
| shop_status | TINYINT | 0 营业 1 停业 | |
| status | TINYINT | 0 正常 1 禁用 | 账号状态（与 apply_status 区分） |
| balance | DECIMAL(12,2) | DEFAULT 0 | **可提现余额**：支付成功 +amount、提现申请 -amount、提现驳回 +amount（资金规则 §4） |

### withdrawal（提现单，含卡快照）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| withdrawal_no | VARCHAR(32) | UNIQUE | 提现单号 |
| merchant_id | BIGINT | NOT NULL, INDEX | |
| bank_name | VARCHAR(64) | NOT NULL | 开户行（**原 bank_card 并入：卡信息做快照**） |
| account_no | VARCHAR(64) | NOT NULL | 卡号 |
| holder | VARCHAR(32) | NOT NULL | 持卡人 |
| amount | DECIMAL(12,2) | NOT NULL | |
| status | TINYINT | 0 待处理 1 成功 2 驳回 | |
| reject_reason | VARCHAR(200) | | |
| handle_time | DATETIME | | 处理时间 |

🔒 索引：`idx_create_time(create_time)`（报表聚合）。

### category（类目）
🔵 BaseDO

| 字段 | 类型 | 说明 |
|---|---|---|
| parent_id | BIGINT, INDEX | 根为 0，两级即可（level 可由 parent_id 推断，不再冗余 level 字段） |
| name | VARCHAR(32) NOT NULL | 类目名 |
| icon | VARCHAR(255) | |
| sort | INT | 排序 |
| status | TINYINT | 0 启用 1 停用 |

> 注：类目删除前应用层校验无在用商品（一个 count 查询），防悬挂引用。

### product（商品）
🔵 BaseDO

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| merchant_id | BIGINT | NOT NULL, INDEX | |
| category_id | BIGINT | NOT NULL, INDEX | |
| title | VARCHAR(128) | NOT NULL | |
| subtitle | VARCHAR(255) | | |
| main_img | VARCHAR(255) | NOT NULL | |
| detail | TEXT | | 图文详情 |
| status | TINYINT | 0 上架 1 下架 | |
| sale_count | INT | 0 | 销量 |

### sku（规格）
⚪ BaseDO（**物理删除**：有 `uk_product_spec` 业务唯一约束，逻辑删除后同规格重建会撞键；与 cart 同类）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| product_id | BIGINT | NOT NULL, INDEX | |
| spec_json | VARCHAR(255) | NOT NULL | `{"颜色":"红","尺码":"L"}` |
| price | DECIMAL(12,2) | NOT NULL | |
| stock | INT | NOT NULL | DB 基准库存（Redis 预扣目标） |
| img | VARCHAR(255) | | 规格图 |
| status | TINYINT | 0 正常 1 停售 | |
| remark | VARCHAR(64) | | 描述文案 |

> 注 1：展示价不做冗余字段——查询时动态 `MIN(sku.price)`，由 product-service 组装返回。属性筛选（如"颜色=红"）也不建筛选索引：商品详情页全量加载该商品 SKU，前端过滤选择；搜索用 LIKE。
> 🔒 唯一约束：`uk_product_spec(product_id, spec_json)`，同一商品下同规格 SKU 只允许一行。

### advert（广告位）
🔵 BaseDO

| 字段 | 类型 | 说明 |
|---|---|---|
| title | VARCHAR(64) NOT NULL | |
| img_url | VARCHAR(255) NOT NULL | |
| sort | INT | |
| status | TINYINT | 0 启用 1 停用 |

### cart（线上购物车）
⚪ BaseDO（**物理删除**：购物车行无审计价值；不做逻辑删除，否则删后同 SKU 加购会撞 `uk_user_sku` 唯一索引）

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT NOT NULL | 索引组合见下 |
| sku_id | BIGINT NOT NULL | |
| product_id | BIGINT NOT NULL | 冗余，避免连表 |
| count | INT NOT NULL | |
| checked | TINYINT | 0/1 勾选结算 |

🔒 唯一约束：`uk_user_sku(user_id, sku_id)`；游客购物车存 localStorage，登录后按 sku_id 累加合并进此表。

### order（订单主表：状态机 + 物流 + 地址快照）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| order_no | VARCHAR(32) | UNIQUE NOT NULL | 业务订单号（§4） |
| user_id | BIGINT | NOT NULL, INDEX | |
| merchant_id | BIGINT | NOT NULL, INDEX | 一单一商家（跨店购物车拆单） |
| total_amount | DECIMAL(12,2) | NOT NULL | 商品总价 |
| pay_amount | DECIMAL(12,2) | NOT NULL | 实付 = total + freight（无优惠折扣） |
| freight | DECIMAL(12,2) | 0 | 运费（演示固定 0，预留字段） |
| status | TINYINT | NOT NULL | 状态机，见 §4 |
| last_status | TINYINT | | **原 order_status_log 并入**：上一步状态，仅留最后一步审计 |
| pay_time / cancel_time | DATETIME | | |
| remark | VARCHAR(255) | | 买家备注 |
| receiver_name / phone | VARCHAR(32)/(20) | NOT NULL | **收货地址快照**（下单时从 user_address 复制，防地址变更影响历史单） |
| receiver_address | VARCHAR(255) | NOT NULL | |
| logistics_company | VARCHAR(32) | | **原 delivery 并入**：物流公司 |
| tracking_no | VARCHAR(64) | | 运单号 |
| send_time / receive_time | DATETIME | | 发货 / 确认收货时间 |

🔒 索引：`idx_user_status(user_id, status)`、`idx_merchant_status(merchant_id, status)`、`idx_create_time(create_time)`（报表聚合）。

> 注：所有状态迁移一律条件更新 `UPDATE ... SET status=新 WHERE id=? AND status=旧`，影响 0 行即冲突拒绝——并发下状态机才原子；pay_info、refund、withdrawal 同理。

### order_item（订单明细，下单快照）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 说明 |
|---|---|---|
| order_id | BIGINT, INDEX | |
| order_no | VARCHAR(32) | |
| sku_id / product_id | BIGINT | |
| title | VARCHAR(128) | 商品名快照 |
| spec_json | VARCHAR(255) | 规格快照 |
| price | DECIMAL(12,2) | 下单时单价 |
| count | INT | 数量 |
| amount | DECIMAL(12,2) | price × count |

### pay_info（支付单：含回调幂等锚点）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| pay_no | VARCHAR(32) | UNIQUE | 本系统支付单号 |
| order_no | VARCHAR(32) | UNIQUE NOT NULL | 一单一支付单（每订单独立支付，不做合并支付） |
| user_id | BIGINT | NOT NULL | |
| amount | DECIMAL(12,2) | NOT NULL | |
| channel | TINYINT | 0 支付宝沙箱 | |
| out_trade_no | VARCHAR(64) | UNIQUE | **原 pay_notify_log 并入**：支付宝商户订单号，回调唯一索引兜底幂等 |
| trade_no | VARCHAR(64) | | 支付宝交易流水号 |
| notify_body | TEXT | | 回调原文摘要 |
| status | TINYINT | 0 待支付 1 成功 2 失败 3 关闭 | |
| notify_time | DATETIME | | 回调完成时间 |

🔒 幂等 = 验签 + **金额核对（`amount == pay_info.amount`，不等拒绝入账）** + 分布式锁 + `out_trade_no` 唯一索引 + 状态机（仅"待支付"可置"成功"）。
🔒 索引：`idx_create_time(create_time)`（报表聚合）。

### refund（退款单）
⚪ BaseDO（不用 deleted）

| 字段 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| refund_no | VARCHAR(32) | UNIQUE | |
| pay_no | VARCHAR(32) | NOT NULL, INDEX | |
| order_no | VARCHAR(32) | NOT NULL | |
| amount | DECIMAL(12,2) | NOT NULL | 整单退（≤ pay_amount） |
| out_request_no | VARCHAR(64) | UNIQUE | **退款幂等标识**：同单退款失败重试不重复扣款 |
| reason | VARCHAR(200) | | |
| status | TINYINT | 0 处理中 1 成功 2 失败 | |
| handle_time | DATETIME | | |

🔒 索引：`idx_create_time(create_time)`（报表聚合）。

## 4. 关键设计要点（与表结构配套）

### 订单状态机（合法流转线）
```
待支付(0) ─支付成功→ 已支付(1) ─商家发货→ 已发货(2) ─用户确认→ 已收货(3)  ⏹终态
   │30min超时/手动取消                │ 用户申请退款（1/2/3 均可）
   ▼                                  ▼
已取消(4) ⏹终态                    退款中(5) ─→ 已退款(6) ⏹终态
```
- 合法线：`0→1→2→3`、`0→4`、`{1,2,3}→5→6`；**已取消(4) 不可退款**。
- 实现级：所有状态迁移一律条件更新 `UPDATE ... SET status=新 WHERE id=? AND status=旧`，影响 0 行即冲突拒绝；pay_info、refund、withdrawal 同理。
- `status`/`last_status` 各存当前/上一步，配合条件更新即可审计。
- 超时取消：下单时发 RocketMQ 延迟消息 30 分钟，消费时同样走条件更新。

### 取消链路（最小实现）
1. 条件更新订单 `0→4`（非待支付则取消失败）；
2. 查本地 pay_info：仍"待支付" → 置"已关闭(3)"；已是"已成功" → 订单照常取消，**不做自动退款**——演示路径可控，真实系统需"晚到自动退款"，留 TODO 注释；
3. 回补库存（DB 条件更新回加 + Redis Lua 同值回加）。
> 不调支付宝关单接口（演示时取消后不会再去支付）；不建定时兜底扫单（本地 RocketMQ 不丢消息）。

### 库存链路（同步方案）
- 预热：SKU 上架/改库存 → Redis 写 `stock:{skuId}`（Lua）。
- 下单：① Redis Lua 原子预扣 `stock:{skuId}`（失败即库存不足）→ ② 事务内创建订单 **并同步条件扣 DB**（`UPDATE sku SET stock=stock-N WHERE id=? AND stock>=N`，条件更新天然幂等）→ ③ 任一步失败 → 补偿回补（幂等键 order_no）。
- 取消/回补：DB 条件更新回加 + Redis Lua 回加。
> DB 为唯一基准，Redis 为热点挡板；不做"异步扣 DB"（演示规模下同步扣更简单，也免去异步 vs 回补的时序问题）。

### 幂等设计
| 场景 | 方案 |
|---|---|
| 下单 | Redis 请求键 + order_no 唯一索引 |
| 支付回调 | 验签 + 金额核对 + 分布式锁 + out_trade_no 唯一索引 + pay_info 状态机 |
| 库存预扣 | Redis Lua 原子扣减，同键重复扣失败 |
| 消息消费 | 条件更新状态机，重复消息只记录不重放 |

### 商家资金规则（balance 随事件更新）
- 支付成功：merchant-service 消费支付成功消息 → `balance += pay_amount`
- 提现申请：校验 `balance >= amount` 后创建 withdrawal，申请即 `balance -= amount`（不做冻结）
- 提现驳回：`balance += amount`（与申请扣款成对出现）

### 订单号
`yyMMddHHmmss`(12) + userId 后 6 位 + 4 位随机；雪花主键 + 唯一索引兜底碰撞重试。

## 5. 报表（无 finance_log 表，直接聚合）

- 营收统计 / 数据看板：聚合 order（pay_amount、status IN (1,2,3)）。**最小实现口径：退款中(5)/已退款(6) 的单不计入营收**（不做 pay_info 聚合账目，演示时避免展示退款中的单，数字即自洽）
- 提现日志：直接查 withdrawal
- 进出账：order（进）+ refund（出）+ withdrawal（出）
- 入驻统计：merchant（apply_status + create_time）
- 均按 create_time 分组（order/pay_info/refund/withdrawal 均已建 `idx_create_time`），无需中间表。

## 6. 分库分表（设计文档，未启用）

Sharding-JDBC 分片对象 order + order_item，分片键 order_id（雪花），`order_id % 4`，4 库 `ds0..ds3`。表结构与唯一键均已预留分片友好形态；启用步骤与迁移风险见 v1 文档，当前不配置。

## 7. 旧表（v1 的 22 表 → 13 表）去向一览

| 旧表 | 去向 |
|---|---|
| employee | 并入 user（role 字段） |
| merchant_apply | 并入 merchant（apply_status 字段） |
| shop | 并入 merchant（shop_* 字段） |
| pay_code | 曾并入 merchant（pay_code_url 字段）；2026-09-13 随店铺表单去收款码一并移除 |
| bank_card | 并入 withdrawal（卡号快照字段） |
| delivery | 并入 order（logistics/tracking/send/receive 字段） |
| order_status_log | 并入 order（last_status）+ status 状态机校验 |
| pay_notify_log | 并入 pay_info（out_trade_no 唯一索引） |
| finance_log | 取消，改为 §5 聚合查询 |

## 8. ER 关系（文字版）

```
user 1─N user_address
user(role=1) ← 平台管理员（后台）
merchant 1─N product 1─N sku, merchant 1─N withdrawal
category 1─N product
user 1─N cart N─1 sku
user 1─N order 1─N order_item (sku 快照)、order ──→ pay_info 1─1 refund
merchant 1─N order（拆单后一单一商家）
```
