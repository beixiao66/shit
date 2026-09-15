# Mall-X 微服务电商平台

多商家入驻高并发电商平台（课程设计项目，《行业工程实践》）。覆盖 **注册登录 → 浏览搜索 → 购物车 → 下单（跨店拆单）→ 支付（回调幂等）→ 发货 → 确认收货 / 退款 / 超时取消** 的完整电商闭环。

- **前端**：Vue 3 + TypeScript + Vite 双端（商城前台 `/` + 管理后台 `/admin`）
- **后端**：Spring Boot 3.2.4 / JDK 17 + Spring Cloud Alibaba 微服务（7 业务服务 + 网关）
- **核心工程实践**：JWT 网关鉴权、Redis Lua 原子预扣库存 + DB 条件扣减双层防超卖、RocketMQ 延迟消息超时取消、支付回调幂等（分布式锁 + 状态机 + 唯一索引）

架构详情见 [docs/架构图/architecture.md](docs/架构图/architecture.md)，数据库设计见 [docs/数据库设计/database-design.md](docs/数据库设计/database-design.md)，需求见 [docs/需求文档/requirements.md](docs/需求文档/requirements.md)。

## 1. 环境要求

| 依赖 | 版本要求 | 说明 |
|---|---|---|
| JDK | 17+ | 后端编译与运行 |
| Maven | 3.6.3+ | 后端构建 |
| Node.js | 18+ | 前端构建（Vite 5） |
| MySQL | 8.x | 本机安装（不在 docker-compose 内，演示约定） |
| MinIO | RELEASE.2024 前后均可 | 本机安装或使用已有实例（图片对象存储，桶 `mall-x` 自动创建） |
| Docker + Compose | 可用即可 | 一键启动 Nacos / Redis / RocketMQ / Sentinel 等中间件 |

## 2. 中间件配置

所有中间件地址写在各服务 `mall-backend/*/src/main/resources/application.yml` 中。
**yml 中默认的 `192.168.193.131` 是开发期虚拟机地址，克隆后请全局搜索替换为你自己的中间件主机 IP**（中间件与本机同机时替换为 `localhost`）。

| 中间件 | 默认地址 | 端口 | 配置项所在 yml |
|---|---|---|---|
| MySQL | localhost | 3306 | 除 gateway 外全部服务 `spring.datasource.url`，库 `mall_x`，账号 root/root（按本机实际密码修改） |
| Nacos | 192.168.193.131 | 8848/9848 | 全部 8 个服务 `spring.cloud.nacos.server-addr` |
| Redis | 192.168.193.131 | 6379 | mall-order-service / mall-pay-service `spring.data.redis` |
| RocketMQ | 192.168.193.131 | 9876 | mall-order-service `rocketmq.name-server` |
| MinIO | 192.168.193.131 | 9000 | mall-product-service / mall-merchant-service `mall.minio.*`（endpoint/access-key/secret-key/bucket，桶 `mall-x` 自动创建） |
| Sentinel | 192.168.193.131 | 8858 | mall-gateway `spring.cloud.sentinel`（可选，不启动不影响功能） |

## 3. 快速开始

### 3.1 启动中间件

```bash
cd docker
docker compose up -d     # nacos / redis / rocketmq(namesrv+broker) / rocketmq-dashboard / sentinel-dashboard
```

> MySQL 与 MinIO 不在 compose 编排内：MySQL 本机安装（演示约定），MinIO 可 [本机安装](https://min.io/download) 或指向已有实例。

### 3.2 初始化数据库

`docs/init.sql`：建 13 张表 + 种子数据（**10 商家 / 113 商品 / 270 SKU / 5 类目 / 4 广告位**）。

```bash
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS mall_x DEFAULT CHARACTER SET utf8mb4;"
# 必须带 --default-character-set=utf8mb4，否则中文种子数据会报 Data too long
mysql -uroot -proot --default-character-set=utf8mb4 mall_x < docs/init.sql
```

### 3.3 构建后端

```bash
cd mall-backend
# 1) 公共模块先安装进本地仓库（其余模块都依赖它）
mvn -pl mall-common install -DskipTests
# 2) 全量打包，jar 落在各模块 target/ 下
mvn package -DskipTests
```

> 只改了单个服务时，可用 `mvn -pl mall-order-service -am package -DskipTests` 联合构建（`-am` 会带上依赖模块，避免本地仓库里的 mall-common 过期）。

### 3.4 启动服务

业务服务（8010~8016）可任意顺序启动，**网关 8090 最后启动**：

```bash
java -jar mall-auth-service/target/mall-auth-service-0.1.0-SNAPSHOT.jar
java -jar mall-user-service/target/mall-user-service-0.1.0-SNAPSHOT.jar
java -jar mall-merchant-service/target/mall-merchant-service-0.1.0-SNAPSHOT.jar
java -jar mall-product-service/target/mall-product-service-0.1.0-SNAPSHOT.jar
java -jar mall-order-service/target/mall-order-service-0.1.0-SNAPSHOT.jar
java -jar mall-pay-service/target/mall-pay-service-0.1.0-SNAPSHOT.jar
java -jar mall-report-service/target/mall-report-service-0.1.0-SNAPSHOT.jar
java -jar mall-gateway/target/mall-gateway-0.1.0-SNAPSHOT.jar
```

端口：auth 8010 / user 8011 / merchant 8012 / product 8013 / order 8014 / pay 8015 / report 8016 / **gateway 8090**。

**启动后验证**：
- 浏览器打开 Nacos 控制台 `http://localhost:8848/nacos`，确认 8 个服务实例均已注册；
- 抽查健康检查：`curl http://localhost:8014/ping`（user/merchant/product/order/pay/report 六个服务均有 `/ping`）；
- 网关连通性：`curl http://localhost:8090/api/portal/products?page=1&size=1` 能返回商品 JSON。

### 3.5 启动前端

```bash
cd mall-frontend
npm install
npm run dev      # http://localhost:5173，/api 代理到网关 8090
```

### 3.6 演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 平台管理员 | admin | 123456 |
| 普通用户 | user1 | 123456 |
| 商家 | seller1 ~ seller10 | 123456 |

## 4. 运行测试

后端单元测试（JUnit 5 + Mockito，**不依赖任何中间件，离线可跑**）覆盖 mall-common（JWT/响应体）、order（订单状态机/下单幂等/库存扣减）、auth（登录注册）、pay（支付回调幂等/退款）核心逻辑：

```bash
cd mall-backend
mvn test                # 全量
mvn -pl mall-order-service test   # 单模块
```

## 5. 常见问题（FAQ）

| 现象 | 原因与解决 |
|---|---|
| SQL 导入报 `Data too long` | 未带 `--default-character-set=utf8mb4`，见 3.2 |
| Redis 连接报错 | `docker-compose.yml` 给 Redis 设了密码 `mall123`，而服务 yml 默认未配密码——二者对齐即可（compose 去掉 `--requirepass`，或各服务 yml 补 `spring.data.redis.password: mall123`） |
| 超时取消不生效 | RocketMQ broker 需向客户端上报宿主地址：确认 `docker/broker.conf` 的 `brokerIP1` 配的是宿主机 IP 且挂载生效；broker 故障不阻断下单主链路（仅失去自动取消，手动取消可用） |
| 商品/店铺图片 404 或 403 | MinIO 未启动或 `mall.minio.*` 未指向实际实例；桶 `mall-x` 首次上传时自动创建并设置公开读 |
| `java -jar` 报找不到依赖 / common 类过期 | 先执行 `mvn -pl mall-common install -DskipTests`，或改用 `mvn -pl <模块> -am package` 联合构建 |
| 前端登录后雪花 ID 报错 | 已内置 json-bigint 处理（`src/api/http.ts`），若自行改动请保留 `storeAsString` 配置 |

## 6. 迭代记录

每轮迭代在 [docs/改良计划/](docs/改良计划/) 下按日期新建 md 记录（做了什么 / 修了什么 / 清理了什么 / 遗留什么），最新：[2026-09-13.md](docs/改良计划/2026-09-13.md)。
