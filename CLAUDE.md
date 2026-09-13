# CLAUDE.md — Mall-X 多商家高并发电商平台（网页版）

> 本文件依据《行业工程实践》教学大纲（2026-08，6 周 / 6 学分）编写，是项目开发的总纲。
> 大纲原始载体为「多商家入驻高并发电商平台 Mall-X」，覆盖用户下单、支付创建、渠道回调、订单状态同步、物流发货、用户确认收货的完整业务闭环。

## 关键决策（相对大纲的调整）

| 决策项 | 大纲原文 | 本项目决定 |
|---|---|---|
| 移动端 | uni-app 跨端开发（用户端 / 商家端 / 管理员业务） | **去掉 uni-app**，移动端业务全部以 Web 网页实现 |
| 前端形态 | PC 管理后台（Vue3+TS+Element Plus）+ uni-app 移动端 | **Web 双端**：① 商城前台（消费者/游客，响应式网页）② 管理后台（平台管理员 + 商家） |
| 后端架构 | SpringBoot3 + Spring Cloud Alibaba 微服务 | **保留完整微服务**：服务拆分 + Nacos + Gateway + Redis + RocketMQ + Docker |
| 目标指标 | QPS ≥ 1000、高可用 99% | 保留为架构设计约束 |

## 技术栈

### 前端（Web，全部 TypeScript）

- **框架**：Vue 3（组合式 API，`<script setup>`）+ TypeScript + Vite
- **UI 组件库**：Element Plus（管理后台）；商城前台响应式布局，可配合自定义样式/轻量组件
- **状态管理**：Pinia（用户态、购物车态）
- **路由**：Vue Router（两级路由空间：商城前台 `/` 与 管理后台 `/admin`，可拆单应用或单应用双布局）
- **HTTP**：Axios（请求拦截器带 token、统一错误处理、401 跳登录）
- **数据可视化**：ECharts（统计报表：营收、入驻、进出账、数据看板）
- **本地缓存**：localStorage（游客离线购物车，登录后与 Redis 端购物车合并）

### 后端（SpringBoot3 + Spring Cloud Alibaba 微服务）

- **基础**：Spring Boot 3.x、JDK 17+、Maven
- **微服务框架**：Spring Cloud Alibaba + Spring Cloud
  - Nacos（服务注册发现 + 配置中心）
  - Spring Cloud Gateway（统一网关：路由、鉴权过滤、**限流**）
  - Sentinel（接口限流、**熔断降级**）
  - OpenFeign（服务间调用）
- **ORM**：MyBatis-Plus + 数据库连接池（HikariCP/Druid）
- **数据与中间件**：
  - MySQL（用户、商家、商品、订单、支付、物流核心数据表；**Sharding-JDBC 分库分表**）
  - Redis（缓存；**Lua 脚本实现库存原子扣减**；Redisson 分布式锁）
  - RocketMQ（**延迟消息实现订单 30 分钟超时自动取消**、消息幂等消费）
- **第三方**：支付宝开放平台沙箱（支付创建、回调、签名验签）
- **部署运维**：Docker 容器化（Nacos / Redis Cluster / RocketMQ / 各微服务镜像）、告警基础配置

### 核心工程要求（答辩/考核重点）

- 下单幂等：分布式锁 + 唯一索引 + 状态机
- 支付回调幂等（同一模式），回调验签
- Redis Lua 库存原子扣减；下单 → 预扣库存 → 超时回补库存完整链路
- 消息消费幂等；订单状态机流转（下单→待支付→已支付→已发货→已收货/超时取消）

## 角色与网页端功能

大纲四类角色：**游客 / 普通用户 / 商家 / 平台管理员**。

### 一、商城前台（游客 + 普通用户，网页商城）

| 模块 | 功能点 |
|---|---|
| 首页与浏览 | 平台首页（轮播广告、服务保障、热销推荐）、商品列表、类目筛选、商品搜索（商品 / 店铺）、商品详情（含"猜你喜欢"同类推荐） |
| 认证 | 游客注册、登录 |
| 购物车 | 游客本地（localStorage）离线购物车；**登录后本地购物车与线上购物车合并** |
| 交易 | 结算下单、订单号生成、支付宝沙箱支付创建、订单状态跟踪 |
| 订单 | 我的订单（列表/详情/状态）、取消订单、**确认收货**、**申请退款**、物流信息查看 |
| 个人中心 | 个人资料、收货地址等基础信息 |

### 二、管理后台（平台管理员 + 商家）

两种角色登录同一管理后台，菜单按 RBAC 权限区分显示。

#### 平台管理员
| 模块 | 功能点 |
|---|---|
| 平台首页/数据看板 | 平台整体数据概览 |
| 类目管理 | 商品类目的增删改查 |
| 广告管理 | 首页广告/轮播位管理 |
| 商家管理 | **商家入驻审核**、商家信息管理 |
| 员工管理 | 员工信息管理 |
| 订单管理 | 商家订单管理（查看全局订单）、**物流发货**、**退款处理（审核退款申请）** |
| 资金管理 | 进出账、提现日志 |
| 统计报表 | 营收统计、入驻统计、数据看板（ECharts 可视化） |

#### 商家
| 模块 | 功能点 |
|---|---|
| 入驻 | 商家注册、提交入驻申请 |
| 店铺 | 店铺首页、店铺信息管理 |
| 商品 | 店铺商品上架/下架/库存维护（CRUD） |
| 收款 | 银行卡管理（提现卡快照） |
| 资金 | **提现申请**、提现记录 |
| 订单 | 我的订单（查看/发货处理） |

## 后端微服务拆分建议（按业务域）

| 服务 | 职责 |
|---|---|
| mall-auth-service | 用户/商家/管理员登录注册、JWT 签发校验（独立认证微服务，大纲任务 13） |
| mall-user-service | 用户资料、收货地址；平台员工账号（user.role=1） |
| mall-merchant-service | 商家入驻申请与审核、商家信息、店铺、提现、**商家资金余额 balance** |
| mall-product-service | 类目、商品、SKU、广告、库存（Redis 预热 + 同步条件扣减） |
| mall-order-service | 购物车、下单、订单状态机、取消/超时取消、确认收货、物流发货 |
| mall-pay-service | 支付单创建（支付宝沙箱）、回调验签与幂等、退款 |
| mall-report-service | 营收/入驻/进出账/提现统计聚合查询（数据看板） |
| mall-gateway | 统一网关：路由、**按 JWT type 鉴权**、限流 |

> 共 7 个业务服务 + mall-gateway；`mall-common` 为公共库（响应体、异常、BaseDO、幂等工具），**非独立微服务**。

### 身份与鉴权（三元身份）

- JWT claims：`{ type: 0 用户 / 1 平台管理员 / 2 商家, id, username }`
- 统一登录：用户/管理员查 user 表、商家查 merchant 表，签发对应 type
- 网关注销过滤按 **type + 路径前缀**：
  - `/api/auth/**`、`/api/portal/**`（商城前台）→ type 0 或游客
  - `/api/admin/**` → type 1（平台管理员）
  - `/api/merchant/**` → type 2（商家）
- 管理后台菜单按 type 区分显示（前端静态菜单，不做 RBAC 三表——实训裁剪）

## 工程规范

- 前端：TypeScript 严格模式；组件封装与复用；代码注释与命名规范（考核项）
- 后端：RESTful 接口规范、统一响应体、全局异常处理、OpenFeign 调用超时与降级兜底
- 环境：Docker Compose 编排中间件，避免手工逐项部署
- Git：分支模型简单化（feature 分支 → master），提交粒度对应迭代任务
- 迭代记录：**每完成一轮功能迭代，在 `docs/改良计划/` 下按日期新建 md 记录**（如 `2026-09-13.md`），写明本轮做了什么 / 修了什么 / 清理了什么 / 遗留什么（模板见已有的 2026-09-13.md）；`docs/implementation.md` 只承载里程碑 1-9 的基础搭建阶段，此后不再追加

## 项目目录结构规划

```
狗屎/
├── mall-frontend/          # Web 前端（商城前台 + 管理后台，Vue3+TS）
│   ├── src/views/mall/     #   商城前台页面
│   ├── src/views/admin/    #   管理后台页面（admin/platform、admin/merchant）
│   ├── src/stores/         #   Pinia：user / cart
│   ├── src/api/            #   Axios 接口层
│   └── src/utils/          #   请求封装、token、本地购物车
├── mall-backend/           # SpringCloudAlibaba 微服务
│   ├── mall-gateway/
│   ├── mall-auth-service/
│   ├── mall-user-service/
│   ├── mall-merchant-service/
│   ├── mall-product-service/
│   ├── mall-order-service/
│   ├── mall-pay-service/
│   ├── mall-report-service/
│   └── mall-common/        # 公共模块（响应体、异常、幂等、分布式锁）
├── docs/                   # 需求文档、数据库设计、架构图、接口文档、init.sql
└── docker/                 # Docker Compose：nacos / redis-cluster / rocketmq
```
