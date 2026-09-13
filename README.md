# Mall-X 微服务电商平台

## 1. 中间件在哪配置

所有中间件地址写在各服务 `mall-backend/*/src/main/resources/application.yml` 中（批量替换用搜索即可）。

| 中间件 | 地址 | 端口 | 配置项所在 yml |
|---|---|---|---|
| MySQL | localhost | 3306 | 各服务（除 gateway）`spring.datasource.url`，库 `mall_x`，账号 root/root |
| Nacos | 192.168.193.131 | 8848/9848 | 各服务（除 order 外均有）`spring.cloud.nacos` |
| Redis | 192.168.193.131 | 6379 | mall-order-service / mall-pay-service `spring.data.redis` |
| RocketMQ | 192.168.193.131 | 9876 | mall-order-service `rocketmq.name-server` |
| MinIO | 192.168.193.131 | 9000 | mall-product-service / mall-merchant-service `mall.minio.*`（endpoint/access-key/secret-key/bucket，桶 mall-x 自动创建） |
| Sentinel | 192.168.193.131 | 8858 | mall-gateway `spring.cloud.sentinel`（可选） |

> 中间件容器编排在 `docker/docker-compose.yml`（nacos/redis/rocketmq×3/sentinel），一键 `docker compose up -d`。

## 2. 服务在哪启动

- **后端**：`mall-backend/` 下每个模块一个 jar —— `java -jar mall-xxx-service/target/mall-xxx-service-0.1.0-SNAPSHOT.jar`。
  端口：auth 8010 / user 8011 / merchant 8012 / product 8013 / order 8014 / pay 8015 / report 8016 / **gateway 8090**（全部服务就绪后启动）。
  公共模块先装：`mvn -pl mall-common install -DskipTests`。
- **前端**：`mall-frontend/` —— `npm install` → `npm run dev`（5173，/api 代理到网关 8090）。

## 3. SQL 脚本在哪

`docs/init.sql`：建 13 张表 + 种子数据（**10 商家 / 113 商品 / 270 SKU / 5 类目 / 4 广告位**）。

```bash
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS mall_x DEFAULT CHARACTER SET utf8mb4;"
# 必须带 --default-character-set=utf8mb4，否则中文种子数据会报 Data too long
mysql -uroot -proot --default-character-set=utf8mb4 mall_x < docs/init.sql
```

演示账号：admin（平台管理员）/ user1（普通用户）/ seller1 ~ seller10（商家），密码均 123456。
