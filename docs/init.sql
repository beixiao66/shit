-- =============================================================
-- Mall-X 数据库初始化脚本（MySQL 8.0+）
-- 对应 docs/database-design.md（13 表精缩版）
-- 公共字段：id/create_time/update_time 全表；deleted 仅内容表（BaseLogicDO）
-- 注意：sku、cart 因有业务唯一约束，不用逻辑删除（软删后重建会撞唯一键）
-- 种子账号密码统一为 BCrypt("123456")
-- =============================================================

CREATE DATABASE IF NOT EXISTS mall_x DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mall_x;

-- -------------------------------------------------------------
-- 1. 用户域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    id           BIGINT       NOT NULL COMMENT '雪花主键',
    username     VARCHAR(32)  NOT NULL COMMENT '登录名',
    password     VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
    nickname     VARCHAR(64)           COMMENT '昵称',
    avatar       VARCHAR(255)          COMMENT '头像 URL',
    phone        VARCHAR(20)           COMMENT '手机号',
    email        VARCHAR(64)           COMMENT '邮箱',
    role         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 用户 1 平台管理员',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 正常 1 禁用',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户/平台管理员(物理保留)';

DROP TABLE IF EXISTS user_address;
CREATE TABLE user_address (
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    receiver    VARCHAR(32)  NOT NULL COMMENT '收件人',
    phone       VARCHAR(20)  NOT NULL,
    province    VARCHAR(32)           COMMENT '省(文本存储,不做省市区表)',
    city        VARCHAR(32)           COMMENT '市',
    district    VARCHAR(32)           COMMENT '区/县',
    detail      VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '0 否 1 默认',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0 正常 1 删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='收货地址(逻辑删除)';

-- -------------------------------------------------------------
-- 2. 商家域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS merchant;
CREATE TABLE merchant (
    id            BIGINT        NOT NULL,
    username      VARCHAR(32)   NOT NULL COMMENT '商家登录账号',
    password      VARCHAR(128)  NOT NULL,
    apply_status  TINYINT       NOT NULL DEFAULT 0 COMMENT '入驻审核 0 待审核 1 通过 2 驳回',
    reject_reason VARCHAR(200)           COMMENT '驳回原因',
    audit_time    DATETIME               COMMENT '审核时间',
    merchant_name VARCHAR(64)   NOT NULL COMMENT '商家名',
    contact       VARCHAR(32)            COMMENT '联系人',
    phone         VARCHAR(20)            COMMENT '联系电话',
    shop_name     VARCHAR(64)            COMMENT '店铺名',
    shop_logo     VARCHAR(255)           COMMENT '店铺 logo',
    shop_desc     TEXT                   COMMENT '店铺介绍',
    shop_address  VARCHAR(255)           COMMENT '店铺地址',
    shop_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '0 营业 1 停业',
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '账号状态 0 正常 1 禁用',
    balance       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '可提现余额:支付成功+/提现申请-/提现驳回+',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商家(账号+入驻审核+店铺+收款码合并,物理保留)';

DROP TABLE IF EXISTS withdrawal;
CREATE TABLE withdrawal (
    id            BIGINT        NOT NULL,
    withdrawal_no VARCHAR(32)   NOT NULL COMMENT '提现单号',
    merchant_id   BIGINT        NOT NULL,
    bank_name     VARCHAR(64)   NOT NULL COMMENT '开户行(卡信息快照)',
    account_no    VARCHAR(64)   NOT NULL COMMENT '银行卡号(快照)',
    holder        VARCHAR(32)   NOT NULL COMMENT '持卡人',
    amount        DECIMAL(12,2) NOT NULL COMMENT '提现金额',
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待处理 1 成功 2 驳回',
    reject_reason VARCHAR(200)           COMMENT '驳回原因',
    handle_time   DATETIME               COMMENT '处理时间',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_withdrawal_no (withdrawal_no),
    KEY idx_merchant (merchant_id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='提现单(卡信息快照,物理保留)';

-- -------------------------------------------------------------
-- 3. 商品域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS category;
CREATE TABLE category (
    id          BIGINT       NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父类目,0 为根',
    name        VARCHAR(32)  NOT NULL,
    icon        VARCHAR(255)          COMMENT '图标',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 启用 1 停用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0 正常 1 删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='类目(逻辑删除;删除前校验无在售商品)';

DROP TABLE IF EXISTS product;
CREATE TABLE product (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL,
    category_id BIGINT        NOT NULL,
    title       VARCHAR(128)  NOT NULL,
    subtitle    VARCHAR(255)           COMMENT '副标题',
    main_img    VARCHAR(255)  NOT NULL COMMENT '主图',
    detail      TEXT                   COMMENT '图文详情',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0 上架 1 下架',
    sale_count  INT           NOT NULL DEFAULT 0 COMMENT '销量',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0 正常 1 删除',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_merchant (merchant_id),
    KEY idx_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品(逻辑删除;展示价动态 MIN(sku.price),无冗余列)';

DROP TABLE IF EXISTS sku;
CREATE TABLE sku (
    id          BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    spec_json   VARCHAR(255)  NOT NULL COMMENT '规格组合JSON,如 {"颜色":"红","尺码":"L"}',
    price       DECIMAL(12,2) NOT NULL,
    stock       INT           NOT NULL COMMENT 'DB基准库存(Redis预扣目标)',
    img         VARCHAR(255)           COMMENT '规格图',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0 正常 1 停售',
    remark      VARCHAR(64)            COMMENT '描述文案',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_spec (product_id, spec_json),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='规格SKU(物理删除:有业务唯一约束,软删后同规格重建会撞键)';

DROP TABLE IF EXISTS advert;
CREATE TABLE advert (
    id          BIGINT       NOT NULL,
    title       VARCHAR(64)  NOT NULL,
    img_url     VARCHAR(255) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 启用 1 停用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0 正常 1 删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='首页广告位(逻辑删除)';

-- -------------------------------------------------------------
-- 4. 订单域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS cart;
CREATE TABLE cart (
    id          BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    sku_id      BIGINT   NOT NULL,
    product_id  BIGINT   NOT NULL COMMENT '冗余避免连表',
    count       INT      NOT NULL DEFAULT 1,
    checked     TINYINT  NOT NULL DEFAULT 1 COMMENT '0 未勾选 1 勾选结算',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sku (user_id, sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='线上购物车(物理删除,游客购物车在localStorage)';

DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
    id                 BIGINT        NOT NULL,
    order_no           VARCHAR(32)   NOT NULL COMMENT '业务订单号',
    user_id            BIGINT        NOT NULL,
    merchant_id        BIGINT        NOT NULL COMMENT '一单一商家(跨店购物车拆单)',
    total_amount       DECIMAL(12,2) NOT NULL COMMENT '商品总价',
    pay_amount         DECIMAL(12,2) NOT NULL COMMENT '实付=total+freight',
    freight            DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费(预设0)',
    status             TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已发货 3已收货 4已取消 5退款中 6已退款',
    last_status        TINYINT                COMMENT '上一状态(配合条件更新做审计)',
    pay_time           DATETIME               COMMENT '支付时间',
    cancel_time        DATETIME               COMMENT '取消时间',
    remark             VARCHAR(255)           COMMENT '买家备注',
    receiver_name      VARCHAR(32)   NOT NULL COMMENT '收货地址快照:收件人',
    receiver_phone     VARCHAR(20)   NOT NULL COMMENT '收货地址快照:电话',
    receiver_address   VARCHAR(255)  NOT NULL COMMENT '收货地址快照:省市区+详细地址',
    logistics_company  VARCHAR(32)            COMMENT '物流公司',
    tracking_no        VARCHAR(64)            COMMENT '运单号',
    send_time          DATETIME               COMMENT '发货时间',
    receive_time       DATETIME               COMMENT '确认收货时间',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status (user_id, status),
    KEY idx_merchant_status (merchant_id, status),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单主表(状态机+物流+地址快照,物理保留)';

DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item (
    id          BIGINT        NOT NULL,
    order_id    BIGINT        NOT NULL,
    order_no    VARCHAR(32)   NOT NULL,
    sku_id      BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    title       VARCHAR(128)  NOT NULL COMMENT '商品名快照',
    spec_json   VARCHAR(255)  NOT NULL COMMENT '规格快照',
    price       DECIMAL(12,2) NOT NULL COMMENT '下单时单价',
    count       INT           NOT NULL,
    amount      DECIMAL(12,2) NOT NULL COMMENT 'price*count',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细(下单快照,物理保留)';

-- -------------------------------------------------------------
-- 5. 支付域
-- -------------------------------------------------------------

DROP TABLE IF EXISTS pay_info;
CREATE TABLE pay_info (
    id            BIGINT        NOT NULL,
    pay_no        VARCHAR(32)   NOT NULL COMMENT '本系统支付单号',
    order_no      VARCHAR(32)   NOT NULL COMMENT '一单一支付单(不做合并支付)',
    user_id       BIGINT        NOT NULL,
    amount        DECIMAL(12,2) NOT NULL,
    channel       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 支付宝沙箱',
    out_trade_no  VARCHAR(64)            COMMENT '支付宝商户订单号(回调幂等锚点,可为NULL)',
    trade_no      VARCHAR(64)            COMMENT '支付宝交易流水号',
    notify_body   TEXT                   COMMENT '回调原文摘要',
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待支付 1 成功 2 失败 3 关闭',
    notify_time   DATETIME               COMMENT '回调完成时间',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付单(回调幂等:验签+金额核对+锁+唯一索引+状态机)';

DROP TABLE IF EXISTS refund;
CREATE TABLE refund (
    id             BIGINT        NOT NULL,
    refund_no      VARCHAR(32)   NOT NULL COMMENT '退款单号',
    pay_no         VARCHAR(32)   NOT NULL,
    order_no       VARCHAR(32)   NOT NULL,
    amount         DECIMAL(12,2) NOT NULL COMMENT '整单退(<=pay_amount)',
    out_request_no VARCHAR(64)            COMMENT '支付宝退款请求号(幂等标识,可为NULL)',
    reason         VARCHAR(200)           COMMENT '退款原因',
    status         TINYINT       NOT NULL DEFAULT 0 COMMENT '0 处理中 1 成功 2 失败',
    handle_time    DATETIME               COMMENT '处理时间',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_out_request_no (out_request_no),
    KEY idx_pay_no (pay_no),
    KEY idx_order_no (order_no),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='退款单(物理保留)';

-- =============================================================
-- 种子数据（密码均为 BCrypt("123456")）
-- =============================================================

-- 账号：admin(平台管理员) / user1(普通用户) / seller1、seller2(商家)
INSERT INTO `user` (id, username, password, nickname, role, status) VALUES
(1, 'admin', '$2a$12$AZM5/LhZZz1qJXa/NGCuaeJrSjNmYx8Hc45JmwGUTBvfsUwXAw/hK', '平台管理员', 1, 0),
(2, 'user1', '$2a$12$AZM5/LhZZz1qJXa/NGCuaeJrSjNmYx8Hc45JmwGUTBvfsUwXAw/hK', '王小明', 0, 0);

INSERT INTO merchant (id, username, password, apply_status, audit_time, merchant_name, contact, phone, shop_name, shop_desc, shop_address, shop_status, status, balance) VALUES
(1, 'seller1', '$2a$12$AZM5/LhZZz1qJXa/NGCuaeJrSjNmYx8Hc45JmwGUTBvfsUwXAw/hK', 1, '2026-08-20 10:00:00', '极客数码', '张三', '13800000001', '极客数码旗舰店', '正品数码,全场包邮', '广东省深圳市南山区科技园', 0, 0, 3299.00),
(2, 'seller2', '$2a$12$AZM5/LhZZz1qJXa/NGCuaeJrSjNmYx8Hc45JmwGUTBvfsUwXAw/hK', 1, '2026-08-21 10:00:00', '悦动服饰', '李四', '13800000002', '悦动服饰官方店', '潮流服饰,无理由退换', '浙江省杭州市西湖区文三路', 0, 0, 0.00);

-- 类目：两级（parent_id=0 为一级）
INSERT INTO category (id, parent_id, name, icon, sort, status) VALUES
(1, 0, '手机数码', '/img/cat/phone.png', 1, 0),
(2, 0, '家用电器', '/img/cat/appliance.png', 2, 0),
(3, 0, '服饰鞋帽', '/img/cat/clothes.png', 3, 0),
(4, 1, '手机通讯', NULL, 1, 0),
(5, 3, '男装', NULL, 1, 0);

-- 商品（卖家 1：数码；卖家 2：服饰）；13 商品 / 23 SKU，图片见 mall-frontend/public/img（scripts/gen_images.py 生成）
INSERT INTO product (id, merchant_id, category_id, title, subtitle, main_img, detail, status, sale_count) VALUES
(1, 1, 4, 'NovaX 5G 手机', '8+256G 双色现货', '/img/product/phone.jpg', '5G 旗舰,首发直降', 0, 120),
(2, 1, 1, '蓝牙耳机 Pro', '主动降噪,长续航', '/img/product/earphone.jpg', '黑白两色可选', 0, 300),
(3, 2, 5, '纯棉基础款白T恤', '100% 棉,透气舒适', '/img/product/tshirt.jpg', '男女同款', 0, 500),
(4, 1, 1, 'WatchX 智能手表', '全天候健康监测', '/img/product/watch.jpg', '心率/血氧/睡眠监测,两周续航', 0, 88),
(5, 1, 2, '无线快充充电座', '15W 无线快充', '/img/product/charger.jpg', '兼容主流机型', 0, 156),
(6, 1, 2, '便携蓝牙音箱', '360° 环绕声场', '/img/product/speaker.jpg', 'IPX6 防水,10 小时续航', 0, 212),
(7, 1, 2, '机械键盘 87 键', '红轴热插拔', '/img/product/keyboard.jpg', 'RGB 背光', 0, 74),
(8, 1, 4, 'NovaX K 至尊版', '12+512G 星空黑', '/img/product/phone.jpg', '商务旗舰,大内存长续航', 0, 45),
(9, 2, 3, '经典棒球帽', '水洗做旧质感', '/img/product/cap.jpg', '男女同款,可调节', 0, 260),
(10, 2, 3, '针织围巾', '柔软保暖', '/img/product/scarf.jpg', '秋冬新款', 0, 188),
(11, 2, 5, '连帽卫衣', '宽松版型', '/img/product/hoodie.jpg', '重磅纯棉,落肩袖', 0, 330),
(12, 2, 3, '卡通拖鞋', '防滑软底', '/img/product/slipper.jpg', '居家外穿两用', 0, 420),
(13, 2, 3, '简约双肩包', '15 寸电脑仓', '/img/product/backpack.jpg', '防泼水面料', 0, 140);

-- SKU（同商品多规格；库存即 DB 基准库存，Redis 预热后使用）
INSERT INTO sku (id, product_id, spec_json, price, stock, status, remark) VALUES
(1, 1, '{"颜色":"曜石黑","存储":"256G"}', 3299.00, 100, 0, '标准版'),
(2, 1, '{"颜色":"星光银","存储":"256G"}', 3299.00, 100, 0, '标准版'),
(3, 2, '{"颜色":"白色"}', 199.00, 500, 0, NULL),
(4, 3, '{"颜色":"白色","尺码":"L"}', 79.00, 300, 0, NULL),
(5, 3, '{"颜色":"黑色","尺码":"XL"}', 79.00, 300, 0, NULL),
(6, 4, '{"颜色":"曜石黑"}', 899.00, 200, 0, NULL),
(7, 4, '{"颜色":"星光银"}', 899.00, 200, 0, NULL),
(8, 5, '{"颜色":"白色"}', 129.00, 400, 0, NULL),
(9, 5, '{"颜色":"黑色"}', 129.00, 400, 0, NULL),
(10, 6, '{"颜色":"蓝色"}', 329.00, 300, 0, NULL),
(11, 7, '{"颜色":"深空灰","轴体":"红轴"}', 499.00, 150, 0, NULL),
(12, 7, '{"颜色":"白色","轴体":"茶轴"}', 499.00, 150, 0, NULL),
(13, 8, '{"颜色":"星空黑","存储":"512G"}', 4599.00, 60, 0, '限量'),
(14, 9, '{"颜色":"黑色"}', 59.00, 500, 0, NULL),
(15, 9, '{"颜色":"卡其"}', 59.00, 500, 0, NULL),
(16, 10, '{"颜色":"驼色"}', 89.00, 400, 0, NULL),
(17, 10, '{"颜色":"灰色"}', 89.00, 400, 0, NULL),
(18, 11, '{"颜色":"白色","尺码":"M"}', 199.00, 250, 0, NULL),
(19, 11, '{"颜色":"灰色","尺码":"L"}', 199.00, 250, 0, NULL),
(20, 12, '{"颜色":"黄色","尺码":"40"}', 49.00, 500, 0, NULL),
(21, 12, '{"颜色":"绿色","尺码":"41"}', 49.00, 500, 0, NULL),
(22, 13, '{"颜色":"黑色"}', 169.00, 350, 0, NULL),
(23, 13, '{"颜色":"灰色"}', 169.00, 350, 0, NULL);

-- 广告位
INSERT INTO advert (id, title, img_url, sort, status) VALUES
(1, '开学季数码专场', '/img/ad/ad1.jpg', 1, 0),
(2, '新品服饰上市', '/img/ad/ad2.jpg', 2, 0),
(3, '数码周大促', '/img/ad/ad3.jpg', 3, 0),
(4, '焕新穿搭季', '/img/ad/ad4.jpg', 4, 0);

-- 用户地址（user1 默认收货地址）
INSERT INTO user_address (id, user_id, receiver, phone, province, city, district, detail, is_default) VALUES
(1, 2, '王小明', '13800138000', '广东省', '深圳市', '南山区', '科技园路 1 号 5 栋 502', 1);

-- 样例业务数据：1 笔已发货订单（卖家 1 手机 x1），报表演示有数
-- 注：id/单号为固定值，可按需扩展；不生成已取消/退款单避免口径歧义
INSERT INTO `order` (id, order_no, user_id, merchant_id, total_amount, pay_amount, freight, status, last_status,
                     pay_time, receiver_name, receiver_phone, receiver_address,
                     logistics_company, tracking_no, send_time, create_time, update_time) VALUES
(1, 'O202609011120001', 2, 1, 3299.00, 3299.00, 0.00, 2, 1,
 '2026-09-01 11:01:00', '王小明', '13800138000', '广东省深圳市南山区科技园路 1 号 5 栋 502',
 '顺丰速运', 'SF1234567890', '2026-09-02 09:00:00', '2026-09-01 11:20:00', '2026-09-02 09:00:00');

INSERT INTO order_item (id, order_id, order_no, sku_id, product_id, title, spec_json, price, count, amount) VALUES
(1, 1, 'O202609011120001', 1, 1, 'NovaX 5G 手机', '{"颜色":"曜石黑","存储":"256G"}', 3299.00, 1, 3299.00);

INSERT INTO pay_info (id, pay_no, order_no, user_id, amount, channel, out_trade_no, trade_no, status, notify_time, create_time) VALUES
(1, 'P202609011121001', 'O202609011120001', 2, 3299.00, 0, 'ALI-202609011121001', '2026090122001400000001', 1, '2026-09-01 11:22:00', '2026-09-01 11:21:00');

-- 已发货样例单对应余额：卖家 1 +3299.00（与上面 merchant.balance 一致）
