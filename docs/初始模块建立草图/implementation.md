# Mall-X 功能实施文档

> 功能看板：每完成一项，状态改为 ✅ 并更新"完成日期"与说明；进行中 ▶；未开始 □；不做的直接标 ✖ 并写原因。
> 约定：**先不做测试**（单元测试/联调/性能验证全部推迟到里程碑 9 之后）；中间件地址 **192.168.193.131**（Nacos/Redis/RocketMQ），MySQL 为 **localhost**。
> 说明：本文件记录里程碑 1-9 的**基础搭建阶段**，已归档不再追加；后续每轮迭代记录见 `docs/改良计划/`（按日期命名，如 2026-09-13.md）。

## 里程碑 1：项目骨架

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 1.1 | 后端父工程 mall-backend（POM 依赖管理：SpringBoot3.2 + Cloud2023 + Alibaba2023） | ✅ | 2026-09-06 | 父 POM + 9 模块结构，版本兼容组合已锁定 |
| 1.2 | mall-common 公共模块（BaseDO/BaseLogicDO、Result、异常、JwtUtil、常量） | ✅ | 2026-09-06 | 含 MyMetaObjectHandler 自动填充；deleted 仅 4 内容表（BaseLogicDO） |
| 1.3 | mall-gateway（Nacos 注册 + 路由 + JWT 鉴权过滤器） | ✅ | 2026-09-06 | AuthGlobalFilter：白名单 + token 校验 + 转发 X-User-* 头 + /api/admin/** 要求 type=1 |
| 1.4 | mall-auth-service（登录/注册/商家入驻申请，用户+商家双表认证） | ✅ | 2026-09-06 | JWT 签发含 type；登录成功商家校验 applyStatus=1；密码 BCrypt |
| 1.5 | mall-user / merchant / product / order / pay / report 六个服务最小骨架（启动类+yml） | ✅ | 2026-09-06 | 各含 /ping；Nacos 192.168.193.131、MySQL localhost |
| 1.6 | 前端 mall-frontend 骨架（Vue3+Vite+TS+Element Plus+Pinia+Router+Axios+ECharts，调 frontend-design 定视觉方向） | ✅ | 2026-09-06 | 视觉方向：午夜蓝+琥珀金价签+mono 票据数字；等级：dev 时 /api 代理到 localhost:8090 |

## 里程碑 2：认证与网关

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 2.1 | 前台登录/注册页（用户） | ✅ | 2026-09-06 | Login/Register 页完成，http.ts 对接 /auth/**（未联调测试，按约定延后） |
| 2.2 | 商家注册+入驻申请页 | ✅ | 2026-09-06 | /register?role=merchant 复用注册页，走 /auth/register/merchant |
| 2.3 | 后台登录页（管理员/商家共用，type 区分） | ✅ | 2026-09-06 | 登录后按 type 跳转（0 前台 / 1、2 后台），后台菜单按 type 切换 |
| 2.4 | 前端路由守卫 + token 存储 + Axios 拦截器（401 跳登录） | ✅ | 2026-09-06 | 守卫：/admin、/orders 需登录（回跳 redirect），后台仅 type 1/2；token 存储 + 401 拦截先已完 |

## 里程碑 3：商品域（product-service + 前台/后台页面）

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 3.1 | 类目 CRUD 接口 + 管理后台页面 | ✅ | 2026-09-06 | /api/admin/categories（type=1）+ /api/portal/categories（游客）；删除校验无子类目/无在售商品 |
| 3.2 | 商品/SKU CRUD 接口 + 商家商品管理页（上架/下架） | ✅ | 2026-09-06 | /api/product/**（type=2，X-User-Id 校验归属）；新建/编辑含 SKU 行编辑、重建规格；商品逻辑删+SKU 物理删；库存 Redis 预热留里程碑 5/8 |
| 3.3 | 广告位 CRUD + 管理后台页面 | ✅ | 2026-09-06 | /api/admin/adverts（type=1）+ /api/portal/adverts 返回启用位 |
| 3.4 | 前台首页（轮播广告+商品推荐）、商品列表（分类/搜索/分页）、商品详情（SKU 选择） | ✅ | 2026-09-06 | Home + ProductDetail 页；类目走廊/商品网格接真实接口；规格前端过滤；搜索 LIKE、分页 12/页（2026-09-13 迭代：移除轮播下方广告条、轮播改纯展示，新增服务保障条/热销推荐/页脚/回到顶部，搜索支持店铺名） |

## 里程碑 4：商家域（merchant-service）

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 4.1 | 入驻审核 API + 后台审核页 | ✅ | 2026-09-06 | /api/admin/merchants + PUT audit（apply_status 0→1/2，重复审核拒绝）；审核页筛选/通过/驳回原因 |
| 4.2 | 店铺信息管理 API + 商家页 | ✅ | 2026-09-06 | GET/PUT /api/merchant/shop（店铺名/logo/介绍/地址/状态）+ 店铺设置页 + balance 展示（收款码字段已于 2026-09-13 迭代移除） |
| 4.3 | 银行卡绑定（提现卡快照） | ✅ | 2026-09-06 | 银行卡信息为提现卡快照（与数据库设计 v2 决策一致，无独立 bank_card 表）；收款码字段与店铺表单入口已于 2026-09-13 迭代移除 |
| 4.4 | 提现申请/审核 API（balance 校验：申请减、驳回加）+ 提现日志页 | ✅ | 2026-09-06 | 申请：原子扣 balance（`balance>=amount` 条件更新，0 行=余额不足）；驳回：回补余额；审核页 + 商家提现记录页 |

## 里程碑 5：交易域（order-service）

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 5.1 | 购物车 API（本地 localStorage 游客车 + 登录合并 + cart 表读改写） | ✅ | 2026-09-06 | /api/portal/carts（GET/POST/merge/count/checked/删除）；页面：游客车 localStorage、登录后自动 merge 清空本地 |
| 5.2 | 下单 API（跨店拆单、服务端算价、Redis 预扣 + 同步条件扣库存） | ✅ | 2026-09-06 | /api/order/create：请求键幂等 + 按商家拆单 + SKU 现价算价（防改价）+ Lua 预扣 + 同事务条件扣 DB + 失败回补；清已下单购物车行 |
| 5.3 | 我的订单/取消订单/确认收货/物流查看 + 前台页面 | ✅ | 2026-09-06 | /api/order/list/detail/cancel/receive + Orders 页（状态 Tab、快照明细、物流、分页） |
| 5.4 | 商家发货 API + 商家订单管理页 | ✅ | 2026-09-06 | /api/merchant/orders/**（网关优先路由 type=2）：列表 + 发货（1→2 条件更新 + 物流信息） |
| 5.5 | 后台订单管理（全局查看/物流发货） | ✅ | 2026-09-06 | /api/admin/orders/**（type=1）：全局列表 + 代发货 |
| 5.6 | RocketMQ 延迟消息：订单 30 分钟超时取消 + 库存回补 | ✅ | 2026-09-06 | 下单投递 mall-order-timeout-topic（延迟等级配置 mall.order.timeout-delay-level 默认16=30min，演示调 3=10s）；消费端状态校验幂等；取消同步关 pay_info+双回补库存；Mq 运行验证属里程碑 9.1 |

## 里程碑 6：支付域（pay-service）

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 6.1 | 支付单创建（支付宝沙箱、一单一支付单）+ 前台支付页跳转 | ✅ | 2026-09-06 | /api/pay/create：订单状态/金额校验、uk_order_no 幂等返回、渠道 Mock 创建；PayPage（演示回调成功/失败） |
| 6.2 | 支付回调处理（验签+金额核对+锁+唯一索引+状态机）+ 退款 API（模拟/沙箱） | ✅ | 2026-09-06 | AlipayGatewayClient 渠道抽象：Mock 默认（演示闭环），真实沙箱 TODO 注释（密钥位 application.yml）；金额核对拒绝入账 + Redisson 锁 + markSuccess 条件更新 + out_trade_no 唯一索引；退款 out_request_no 幂等 + 渠道同步成功 + 订单 1→5→6 + 商家余额扣回；验签部分在真实沙箱实现（Mock 渠道无签名字段） |
| 6.3 | 前台申请退款入口 + 后台退款处理页 | ✅ | 2026-09-06 | 订单列表"申请退款"（原因 prompt）；/api/admin/refunds 记录查询页 |

## 里程碑 7：管理后台综合

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 7.1 | 后台框架（菜单按 type 区分：管理员/商家）、员工管理（user.role=1） | ✅ | 2026-09-06 | 壳沿用 1.6；员工管理：/api/admin/users（type=1）列表/创建（BCrypt）/禁用启用；前台 AdminEmployees 页 |
| 7.2 | 统计报表：营收/入驻/进出账/提现日志/数据看板（ECharts） | ✅ | 2026-09-06 | /api/report/dashboard（type=1 全平台 / type=2 传 merchantId 本店口径）：营收（order IN 1,2,3）、进账（pay_info 成功）、出账（refund+withdrawal）、入驻统计、近 7 日趋势、进出账流水（20 笔）；看板卡片+ECharts 趋势线；报表明细页 |
| 7.3 | 前台个人中心（资料/地址管理） | ✅ | 2026-09-06 | /api/user/profile + /api/user/addresses（默认地址单事务；逻辑删除）；ProfilePage（资料表单 + 地址列表/增删改/默认）；守卫保护 /profile |

## 里程碑 8：微服务治理（大纲任务 12-14）

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 8.1 | 网关限流/熔断降级配置（Sentinel） | ✅ | 2026-09-06 | 网关固定窗口限流：/api/order/create QPS20（演示阈值低）、portal 商品 100、admin 50；服务侧 @SentinelResource("orderCreate") + 静态 QPS 规则 + createBlocked/createFallback 兜底。注：SCG 专用适配器（sentinel-gateway-scg-adapter）私有镜像暂缺，已用内嵌实现等价替代并注释生产切换方式 |
| 8.2 | 认证服务拆分落地（商家/管理员/用户登录) + Redis Lua 库存原子扣减 + 分布式锁 | ✅ | 2026-09-06 | 三项均已在前序落地：认证服务独立 mall-auth-service（1.4，双表认证+JWT type 三元身份）；库存 Redis Lua 原子预扣/回补（5.2 REDIS_STOCK_KEY DESC/INCR）；分布式锁 Redisson pay:notify（6.2 支付回调，锁+唯一索引+状态机三件套） |

## 里程碑 9：交付收尾（大纲任务 15-17）

| # | 任务 | 状态 | 完成日期 | 说明 |
|---|---|---|---|---|
| 9.1 | 全模块接口联调（**此时才启用测试**） | ▶ | 2026-09-06 | **全链路冒烟已通过：scripts/smoke.py 23/23 PASS**（登录3身份/游客浏览/加购合并/拆单下单/库存预扣/支付回调幂等(金额拒收预期)/发货/确认收货/退款/提现驳回回补/双端看板）。运行修复：jar repackage 绑定、-parameters 编译参数、JDBC characterEncoding=UTF-8、网关 portal/adverts 路由与回调白名单、管理员登录 type=1、库存 Redis 预热、MQ 失败降级。待环境项：**RocketMQ broker 上报地址**（docker 内网 IP 导致延迟消息发送失败——下单已降级不阻断，超时取消需在 192.168.193.131 确认 broker.conf 挂载生效后重启 broker 复测）；JUnit/压测用例待联调完成后补 |
| 9.2 | Docker 镜像打包 + 部署 | ✅ | 2026-09-06 | docker/docker-compose.yml（nacos2.3/redis7.2/rocketmq5.1.4+dashboard/sentinel-dashboard）、broker.conf（brokerIP1 宿主）、通用 Dockerfile（JDK17）；部署步骤与演示参数（延迟等级 3=10s）见根 README.md |
| 9.3 | 全套项目文档（需求/数据库/概要/实施） | ✅ | 2026-09-06 | docs/需求文档/requirements.md（角色/功能/非功能/验收）、docs/架构图/architecture.md（拓扑/服务边界/三大时序/鉴权）、docs/数据库设计/database-design.md（13 表设计）、docs/初始模块建立草图/implementation.md（本文件）、docs/init.sql（建表+种子）；根目录 README.md（环境上手）+ CLAUDE.md 总纲 |

## 环境参数（写死在各服务 application.yml）

| 配置项 | 值 |
|---|---|
| Nacos 地址 | `192.168.193.131:8848` |
| Redis 地址 | `192.168.193.131:6379` |
| RocketMQ | `192.168.193.131:9876` |
| MySQL | `localhost:3306/mall_x`（库名 mall_x，脚本见 docs/init.sql） |
| 支付宝沙箱 | 待配置（密钥留白，处理支付时填写）|
