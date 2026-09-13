# Mall-X 概要设计（架构视图）

> 依据大纲任务 2（系统总体微服务架构设计）输出。

## 1. 系统拓扑

```
                       ┌─────────────────────────────────────────────┐
   浏览器 (Vue3 Web)   │             192.168.193.131 (Docker)          │
  ┌──────────────┐     │  Nacos(8848) Redis(6379) RocketMQ(9876/10911)│
  │ 商城前台 /   │     │  Sentinel Dash(8858)                          │
  │ 管理后台      │─────┤                       ┌──────────────┐        │
  └──────────────┘     │  ┌── mall-gateway 8090 │             │        │
       :5173 dev       │  │  路由/JWT鉴权/限流    │  MySQL localhost      │
       代理 /api→8090  │  └──┬────────────────┘  │  mall_x(13表)  │      │
                       │     ├─ mall-auth-service 8010                │
                       │     ├─ mall-user-service  8011                │
                       │     ├─ mall-merchant-service 8012             │
                       │     ├─ mall-product-service 8013              │
                       │     ├─ mall-order-service  8014               │
                       │     ├─ mall-pay-service    8015               │
                       │     └─ mall-report-service 8016               │
                       └─────────────────────────────────────────────┘
```

- 全服务注册 Nacos（192.168.193.131），网关服务发现负载（lb://）
- 中间件容器化（docker/docker-compose.yml）；MySQL 留 localhost（演示约定）
- mall-common 为公共库（BaseDO/BaseLogicDO、Result、全局异常、JwtUtil、常量），非独立微服务

## 2. 服务职责边界

| 服务 | 数据表 | 关键职责 |
|---|---|---|
| mall-gateway | - | 路由、JWT 鉴权（type+路径规则）、固定窗口限流、X-User-* 头转发 |
| mall-auth-service | user、merchant（账号列） | 登录/注册/入驻申请、BCrypt、JWT 签发（type 三元身份） |
| mall-user-service | user、user_address | 个人资料、地址；员工管理（role=1） |
| mall-merchant-service | merchant、withdrawal | 入驻审核、店铺、提现（balance 原子增减） |
| mall-product-service | category、product、sku、advert | 商品/类目/广告；展示价动态 MIN；merchant 读取店铺名（读模型） |
| mall-order-service | cart、order、order_item | 购物车、下单拆单、状态机、超时取消（RocketMQ 延迟）；同库直连 sku/pay_info（单库演示约定） |
| mall-pay-service | pay_info、refund | 支付单、回调幂等（锁+唯一索引+状态机+金额核对）、退款；同库直连 order/merchant（同上约定） |
| mall-report-service | -（只读聚合） | 营收/进出账/看板聚合（读模型例外，见 database-design §5） |

> 单库演示环境约定：order/pay 服务在本地事务内直连同库跨域表（库存、支付单、订单、余额），
> 均以条件更新保证原子与幂等；生产拆分时改为事件编排（各 Service 类注释已标注 TODO）。

## 3. 关键时序

### 3.1 下单（跨店拆单）
```
POST /api/order/create
1  Redis 请求键去重（order:req:{userId}:{reqId}）
2  加载 SKU 现价与商品归属（同库只读）
3  按 merchant 分组拆单
4  每个订单：Lua 预扣 stock:{skuId} → 创建订单+明细 → UPDATE sku SET stock=stock-N WHERE stock>=N
5  fromCart 清购物车已下单行
6  发送 RocketMQ 延迟消息（mall-order-timeout-topic，等级 16=30min）
失败补偿：任一步抛异常 → 事务回滚 + Lua 回加 Redis
```

### 3.2 支付回调（幂等三件套）
```
渠道/模拟回调 → ① 金额核对（amount != pay_info.amount 拒绝）
② Redisson 锁 pay:notify:{payNo} → ③ pay_info 0→1（条件更新）
→ 订单 0→1（markPaid）→ merchant.balance += amount
重复回调：②③ 任一失败即幂等返回
```

### 3.3 超时取消
```
延迟消息消费 → 查单：status≠0 幂等返回
条件更新 0→4 → pay_info 0→3（关闭）→ DB+Lua 双向回补库存
```

## 4. 安全与鉴权

- JWT claims：`{type: 0用户/1管理员/2商家, sub: id, username}`；HS256，网关/auth 共享 secret
- 网关规则表：前缀 → 允许 type（游客白名单 login/register/portal 浏览）
- 商家接口商品/订单/提现均以 X-User-Id 做归属校验；管理员接口 type=1

## 5. 可观测与部署

- 每服务自带 GET /ping 健康检查
- Nacos 控制台查看服务实例与下线；RocketMQ Dashboard（8180）观察延迟消息
- 部署：`docker/docker-compose.yml` 一键起中间件；各服务 `mvn package` 后 `java -jar` 启动（端口与顺序见根 `README.md`）
- Sentinel：网关内嵌限流 + 服务侧 @SentinelResource 静态规则；Dashboard（8858）可选接入热更新
