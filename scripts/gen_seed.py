# -*- coding: utf-8 -*-
"""Mall-X 种子数据生成器：输出 merchant / product / sku 的 INSERT 语句（写入 docs/init.sql）

用法：python scripts/gen_seed.py > tmp_seed.sql   # 再把输出贴进 init.sql 对应段落
- 商家：id 3-10（8 个新商家，seller3~seller10 / 密码 123456）
- 商品：id 14-113（100 条，覆盖 5 个类目）
- SKU：id 24 起连续分配（每商品按 spec_type 生成 1-3 个规格）
"""
import json
import random

PWD = '$2a$12$AZM5/LhZZz1qJXa/NGCuaeJrSjNmYx8Hc45JmwGUTBvfsUwXAw/hK'  # BCrypt("123456")

# ---------------- 商家（id 3-10） ----------------
MERCHANTS = [
    (3, 'seller3', '星辰数码', '王星辰', '13800000003', '星辰数码专营店', '数码配件一站购齐', '广东省深圳市福田区华强北', 1200.00),
    (4, 'seller4', '智享家电', '赵智', '13800000004', '智享家电旗舰店', '品质家电,送货入户', '江苏省南京市江宁区将军大道', 860.00),
    (5, 'seller5', '潮流前线', '陈潮', '13800000005', '潮流前线服饰店', '每周上新,紧跟潮流', '浙江省杭州市江干区四季青', 2400.00),
    (6, 'seller6', '型格男装', '刘型', '13800000006', '型格男装官方店', '商务休闲,版型挺括', '福建省泉州市石狮市服装城', 1500.00),
    (7, 'seller7', '悠然家居', '孙悠', '13800000007', '悠然家居生活馆', '让家更舒适一点', '广东省佛山市顺德区乐从镇', 730.00),
    (8, 'seller8', '悦跑运动', '周悦', '13800000008', '悦跑运动装备店', '跑步装备,专业之选', '福建省晋江市运动产业园', 1980.00),
    (9, 'seller9', '简约衣橱', '吴简', '13800000009', '简约衣橱旗舰店', '基础款也能穿出质感', '广东省广州市白云区服装城', 620.00),
    (10, 'seller10', '极光影像', '郑光', '13800000010', '极光影像器材店', '影像器材,正品行货', '北京市海淀区中关村', 3100.00),
]

# ---------------- 商品（id 14-113） ----------------
# (merchant_id, category_id, title, subtitle, img, detail, sale, price, spec_type)
# spec_type: c2=双色 / c3=三色 / cloth=颜色+尺码 / single=单规格 / cap=颜色（通用）
P = [
    # ===== 手机数码（category 1）30 条 =====
    (10, 1, 'Aurora 15 Pro 轻薄本', '14 英寸 2.8K 屏 / 32G+1T', '/img/product/laptop.jpg', '全金属机身,1.29kg 轻至随行', 96, 6999.00, 'c2'),
    (10, 1, 'Aurora 14 Air 商务本', '14 英寸 / 16G+512G', '/img/product/laptop.jpg', '长续航商务本,支持快充', 143, 4599.00, 'c2'),
    (3, 1, '星云 Book 全能本', '15.6 英寸 / 16G+1T', '/img/product/laptop.jpg', '独显直连,办公创作两相宜', 68, 5299.00, 'c2'),
    (3, 1, '星耀 X1 平板电脑', '11 英寸 2.5K / 8G+256G', '/img/product/tablet.jpg', '轻薄机身,支持手写笔', 187, 2299.00, 'cl3'),
    (3, 1, '星耀 Pad Mini 平板', '8.4 英寸 / 6G+128G', '/img/product/tablet.jpg', '单手握持,随身娱乐', 254, 1299.00, 'cl3'),
    (3, 1, '绘影平板 11 寸', '11 英寸 / 8G+128G', '/img/product/tablet.jpg', '护眼屏,网课学习好帮手', 132, 1099.00, 'cl3'),
    (10, 1, '极光 A7 微单相机', '2400 万像素 / 套机', '/img/product/camera.jpg', '五轴防抖,4K 视频', 77, 5899.00, 'c2'),
    (10, 1, '极光 A9 全画幅相机', '4500 万像素 / 机身', '/img/product/camera.jpg', '专业级全画幅,眼控对焦', 41, 12999.00, 'single'),
    (10, 1, '旅拍 Vlog 相机', '4K 防抖 / 便携', '/img/product/camera.jpg', '轻巧便携,一键成片', 165, 2699.00, 'c2'),
    (3, 1, '20000mAh 快充充电宝', '22.5W 双向快充', '/img/product/powerbank.jpg', '大容量,可上飞机', 421, 149.00, 'c2'),
    (3, 1, '10000mAh 磁吸充电宝', '15W 磁吸无线', '/img/product/powerbank.jpg', '吸附即充,轻薄便携', 356, 129.00, 'c2'),
    (3, 1, '自带线充电宝', '10000mAh / 三合一', '/img/product/powerbank.jpg', '自带双线,出门不用带线', 289, 99.00, 'c2'),
    (3, 1, '静音无线鼠标', '2.4G 连接 / 静音按键', '/img/product/mouse.jpg', '办公静音,长续航', 512, 59.00, 'c2'),
    (3, 1, '电竞游戏鼠标', '8000DPI / RGB', '/img/product/mouse.jpg', '可编程按键,吃鸡利器', 268, 169.00, 'c2'),
    (3, 1, '人体工学鼠标', '垂直设计 / 护腕', '/img/product/mouse.jpg', '缓解手腕疲劳', 176, 129.00, 'c2'),
    (10, 1, '27 英寸 2K 显示器', '2K / 165Hz / IPS', '/img/product/monitor.jpg', '窄边框,低蓝光不闪屏', 209, 1299.00, 'single'),
    (10, 1, '24 英寸办公显示器', '1080P / 75Hz', '/img/product/monitor.jpg', '办公家用,护眼舒适', 341, 699.00, 'single'),
    (10, 1, '34 英寸带鱼屏显示器', '2K / 144Hz / 曲面', '/img/product/monitor.jpg', '带鱼屏比例,一屏多用', 87, 2399.00, 'single'),
    (1, 1, '蓝牙耳机 Air', '半入耳 / 长续航', '/img/product/earphone.jpg', '轻盈佩戴,通话降噪', 623, 299.00, 'c2'),
    (1, 1, '头戴式降噪耳机', '主动降噪 / 40 小时', '/img/product/earphone.jpg', '深度降噪,沉浸聆听', 318, 899.00, 'c2'),
    (1, 1, '运动蓝牙耳机', '挂耳式 / IPX5 防水', '/img/product/earphone.jpg', '跑步不掉落', 402, 199.00, 'c2'),
    (1, 1, '入耳式有线耳机', '3.5mm / 带麦', '/img/product/earphone.jpg', '高清通话,即插即用', 511, 79.00, 'c2'),
    (1, 1, '智能手表 GT', '血氧心率 / 14 天续航', '/img/product/watch.jpg', '全天候健康监测', 276, 899.00, 'c2'),
    (3, 1, '儿童电话手表', '定位 / 视频通话', '/img/product/watch.jpg', '家长放心,孩子喜欢', 389, 499.00, 'cl3'),
    (10, 1, '商务石英手表', '防水 / 简约表盘', '/img/product/watch.jpg', '商务通勤,百搭耐看', 148, 399.00, 'c2'),
    (1, 1, '机械键盘 104 键', '全尺寸 / 热插拔', '/img/product/keyboard.jpg', 'RGB 背光,手感扎实', 234, 459.00, 'c3'),
    (3, 1, '静音办公键盘', '无线双模 / 静音轴', '/img/product/keyboard.jpg', '办公不打扰同事', 197, 199.00, 'c2'),
    (3, 1, '无线双模键盘', '蓝牙+2.4G / 超薄', '/img/product/keyboard.jpg', '多设备切换', 263, 229.00, 'c2'),
    (1, 1, '桌面蓝牙音箱', '2.0 声道 / 木质箱体', '/img/product/speaker.jpg', '人声通透,桌面好物', 312, 399.00, 'c2'),
    (1, 1, '便携户外音箱', 'IPX7 防水 / 20 小时', '/img/product/speaker.jpg', '户外露营,声场开阔', 428, 299.00, 'c2'),

    # ===== 手机通讯（category 4）15 条 =====
    (3, 4, '星辰 S30 5G 手机', '8+256G / 6.7 英寸', '/img/product/phone.jpg', '大电池长续航,快充 33W', 356, 1999.00, 'c3'),
    (3, 4, '星辰 S30 Pro 旗舰', '12+512G / 曲面屏', '/img/product/phone.jpg', '旗舰影像,性能强悍', 187, 3299.00, 'c3'),
    (3, 4, '星辰 Note 12 大屏手机', '8+128G / 6.9 英寸', '/img/product/phone.jpg', '大屏大电量,追剧神器', 421, 1499.00, 'c3'),
    (10, 4, '极光 X5 影像手机', '12+256G / 潜望长焦', '/img/product/phone.jpg', '影像旗舰,夜拍出色', 233, 3999.00, 'c3'),
    (10, 4, '极光 X5 Pro', '16+1T / 卫星通信', '/img/product/phone.jpg', '顶配旗舰,商务之选', 98, 5999.00, 'c3'),
    (9, 4, '云图 C9 轻薄手机', '8+256G / 170g', '/img/product/phone.jpg', '轻薄手感,颜值在线', 312, 2299.00, 'c3'),
    (9, 4, '云图 C9 Max', '12+512G / 大电池', '/img/product/phone.jpg', '轻薄大电池兼得', 176, 2799.00, 'c3'),
    (4, 4, '曜石 Z1 电竞手机', '12+256G / 144Hz', '/img/product/phone.jpg', '电竞肩键,散热强劲', 265, 3499.00, 'c2'),
    (4, 4, '曜石 Z1 Pro', '16+512G / 主动散热', '/img/product/phone.jpg', '专业电竞,持久高帧', 134, 4299.00, 'c2'),
    (4, 4, '星耀 A6 入门机', '6+128G / 大音量', '/img/product/phone.jpg', '入门首选,简单好用', 528, 799.00, 'c3'),
    (4, 4, '星耀 A6 Plus', '8+256G / 大屏', '/img/product/phone.jpg', '大屏大字,适合长辈', 437, 1099.00, 'c3'),
    (6, 4, '简界 M3 长辈机', '4+64G / 大按键', '/img/product/phone.jpg', '一键呼叫,操作简单', 289, 399.00, 'c2'),
    (6, 4, '简界 M3 Pro', '6+128G / 超长待机', '/img/product/phone.jpg', '待机一周,安心耐用', 213, 599.00, 'c2'),
    (10, 4, '幻影 Fold 折叠屏', '16+512G / 大折叠', '/img/product/phone.jpg', '展开即平板,折叠即手机', 67, 8999.00, 'c2'),
    (10, 4, '幻影 Flip 小折叠', '12+256G / 竖折', '/img/product/phone.jpg', '小巧精致,掌心折叠', 112, 5999.00, 'c3'),

    # ===== 家用电器（category 2）20 条 =====
    (7, 2, '智能电饭煲 4L', '一键柴火饭 / 24h 预约', '/img/product/ricecooker.jpg', '不粘内胆,煮粥煲汤', 386, 399.00, 'c2'),
    (7, 2, '迷你电饭煲 2L', '小容量 / 宿舍适用', '/img/product/ricecooker.jpg', '一人食刚刚好', 512, 179.00, 'c2'),
    (4, 2, 'IH 电磁电饭煲', '5L / 大火力', '/img/product/ricecooker.jpg', 'IH 立体加热,米饭更香', 214, 899.00, 'c2'),
    (4, 2, '23L 平板微波炉', '家用 / 光波烧烤', '/img/product/microwave.jpg', '一键加热,解冻加热两用', 327, 599.00, 'c2'),
    (4, 2, '智能光波炉', '25L / 触控面板', '/img/product/microwave.jpg', '智能菜单,老人也会用', 189, 799.00, 'c2'),
    (7, 2, '迷你微波炉', '20L / 小户型', '/img/product/microwave.jpg', '小体积大容量', 246, 449.00, 'c2'),
    (4, 2, '滚筒洗衣机 10kg', '洗烘一体 / 变频', '/img/product/washer.jpg', '大容量,全家衣物一次洗', 176, 2699.00, 'c2'),
    (4, 2, '波轮洗衣机 8kg', '全自动 / 免清洗', '/img/product/washer.jpg', '操作简单,洗得干净', 298, 1299.00, 'c2'),
    (7, 2, '迷你洗衣机 3kg', '内衣专用 / 高温除菌', '/img/product/washer.jpg', '内衣分开洗更卫生', 367, 599.00, 'c2'),
    (7, 2, '静音落地扇', '7 叶 / 遥控定时', '/img/product/fan.jpg', '低噪送风,睡眠不扰', 445, 269.00, 'c2'),
    (7, 2, '空气循环扇', '涡轮增压 / 远距离送风', '/img/product/fan.jpg', '搭配空调更省电', 312, 349.00, 'c2'),
    (4, 2, '塔扇无叶风扇', '无叶设计 / 安全', '/img/product/fan.jpg', '家里有小孩更安心', 208, 419.00, 'c2'),
    (7, 2, '恒温电热水壶', '1.7L / 保温 6 段', '/img/product/kettle.jpg', '一键恒温,泡奶泡茶', 523, 199.00, 'c2'),
    (7, 2, '折叠旅行水壶', '600ml / 硅胶可折叠', '/img/product/kettle.jpg', '出差旅行好携带', 634, 129.00, 'c2'),
    (7, 2, '多功能养生壶', '1.8L / 炖煮一体', '/img/product/kettle.jpg', '煮茶炖汤,一壶多用', 289, 259.00, 'c2'),
    (4, 2, '保温电热水瓶', '5L / 双层防烫', '/img/product/kettle.jpg', '大容量,全家喝水', 176, 299.00, 'c2'),
    (4, 2, '32 英寸智能电视', '高清 / 智能系统', '/img/product/monitor.jpg', '卧室小电视,够用不贵', 234, 1099.00, 'single'),
    (10, 2, '43 英寸 4K 电视', '4K 超清 / 投屏', '/img/product/monitor.jpg', '客厅大屏,观影震撼', 187, 1999.00, 'single'),
    (3, 2, '65W 氮化镓充电头', '双口 / 小巧便携', '/img/product/charger.jpg', '一个头充遍全家', 612, 129.00, 'c2'),
    (3, 2, '多口桌面充电器', '65W / 三口输出', '/img/product/charger.jpg', '桌面整洁,多设备同充', 378, 169.00, 'c2'),

    # ===== 服饰鞋帽（category 3）25 条 =====
    (9, 3, '纯棉圆领 T 恤', '260g 重磅棉 / 不易变形', '/img/product/tshirt.jpg', '基础百搭,四季可穿', 892, 89.00, 'cloth'),
    (9, 3, '印花短袖 T 恤', '夏季新款 / 宽松版型', '/img/product/tshirt.jpg', '原创印花,不易撞衫', 534, 69.00, 'cloth'),
    (9, 3, 'POLO 衫短袖', '珠地网眼 / 商务休闲', '/img/product/tshirt.jpg', '通勤约会都得体', 412, 129.00, 'cloth'),
    (8, 3, '速干运动 T 恤', '吸湿排汗 / 轻盈', '/img/product/tshirt.jpg', '跑步健身不粘身', 623, 99.00, 'cloth'),
    (5, 3, '经典棒球帽', '水洗做旧 / 可调节', '/img/product/cap.jpg', '遮阳百搭,男女同款', 745, 59.00, 'cap'),
    (5, 3, '渔夫帽遮阳帽', '大檐 / 防晒 UPF50+', '/img/product/cap.jpg', '夏季防晒必备', 512, 69.00, 'cap'),
    (5, 3, '针织毛线帽', '秋冬保暖 / 弹力', '/img/product/cap.jpg', '柔软不扎头', 389, 49.00, 'cap'),
    (5, 3, '针织围巾', '加厚保暖 / 亲肤', '/img/product/scarf.jpg', '秋冬新款,送人自用', 467, 89.00, 'c2'),
    (9, 3, '真丝感丝巾', '桑蚕丝感 / 百搭', '/img/product/scarf.jpg', '点亮穿搭的小心机', 234, 119.00, 'c2'),
    (5, 3, '连帽卫衣', '宽松落肩 / 重磅纯棉', '/img/product/hoodie.jpg', '秋冬内搭外穿都行', 712, 199.00, 'cloth'),
    (5, 3, '抓绒保暖卫衣', '加绒加厚 / 抗起球', '/img/product/hoodie.jpg', '保暖不臃肿', 523, 229.00, 'cloth'),
    (9, 3, '拉链开衫卫衣', '拉链款 / 立领', '/img/product/hoodie.jpg', '运动休闲两相宜', 398, 209.00, 'cloth'),
    (7, 3, '卡通居家拖鞋', '防滑软底 / 静音', '/img/product/slipper.jpg', '居家外穿两用', 834, 39.00, 'cl2'),
    (7, 3, '居家棉拖鞋', '加绒保暖 / 厚底', '/img/product/slipper.jpg', '冬天脚不冷', 692, 49.00, 'cl2'),
    (7, 3, '浴室防滑拖鞋', '漏水速干 / 防滑', '/img/product/slipper.jpg', '洗澡不打滑', 578, 35.00, 'cl2'),
    (3, 3, '简约双肩包', '15 寸电脑仓 / 防泼水', '/img/product/backpack.jpg', '通勤上学都合适', 634, 169.00, 'c2'),
    (3, 3, '通勤笔记本电脑包', '14 寸 / 商务款', '/img/product/backpack.jpg', '分区收纳,取放方便', 423, 199.00, 'c2'),
    (8, 3, '户外登山背包', '40L / 防泼水', '/img/product/backpack.jpg', '徒步露营大容量', 287, 299.00, 'c2'),
    (8, 3, '轻跑运动鞋', '透气网面 / 缓震', '/img/product/sneaker.jpg', '轻量回弹,跑步舒适', 756, 299.00, 'cl2'),
    (8, 3, '休闲板鞋', '小白鞋 / 百搭', '/img/product/sneaker.jpg', '日常百搭不出错', 645, 199.00, 'cl2'),
    (8, 3, '经典帆布鞋', '硫化工艺 / 耐磨', '/img/product/sneaker.jpg', '青春潮流,四季可穿', 534, 159.00, 'cl2'),
    (9, 3, '直筒牛仔裤', '微弹舒适 / 显瘦', '/img/product/jeans.jpg', '经典直筒,修饰腿型', 623, 189.00, 'cloth'),
    (9, 3, '修身小脚牛仔裤', '弹力面料 / 显高', '/img/product/jeans.jpg', '修身不紧绷', 512, 199.00, 'cloth'),
    (9, 3, '法式碎花连衣裙', '收腰显瘦 / 垂感', '/img/product/dress.jpg', '春夏约会穿搭', 456, 259.00, 'cl2'),
    (9, 3, '基础款衬衫', '免烫抗皱 / 通勤', '/img/product/shirt.jpg', '职场百搭单品', 389, 169.00, 'cloth'),

    # ===== 男装（category 5）10 条 =====
    (6, 5, '商务免烫衬衫', '抗皱免烫 / 修身', '/img/product/shirt.jpg', '出差开会都体面', 412, 199.00, 'cloth'),
    (6, 5, '休闲格子衬衫', '法兰绒 / 加厚', '/img/product/shirt.jpg', '外穿内搭都好看', 356, 169.00, 'cloth'),
    (6, 5, '亚麻短袖衬衫', '透气亚麻 / 夏季', '/img/product/shirt.jpg', '清爽透气不闷汗', 289, 179.00, 'cloth'),
    (6, 5, '男士直筒牛仔裤', '直筒版型 / 弹力', '/img/product/jeans.jpg', '耐穿百搭', 467, 209.00, 'cloth'),
    (6, 5, '男士弹力休闲裤', '四面弹 / 舒适', '/img/product/jeans.jpg', '久坐不勒', 398, 189.00, 'cloth'),
    (6, 5, '男士连帽卫衣', '纯色简约 / 加绒', '/img/product/hoodie.jpg', '运动休闲都合适', 512, 219.00, 'cloth'),
    (6, 5, '男士拉链外套', '立领 / 防风', '/img/product/hoodie.jpg', '春秋外搭首选', 376, 239.00, 'cloth'),
    (6, 5, '男士纯棉 T 恤', '圆领 / 精梳棉', '/img/product/tshirt.jpg', '柔软亲肤不变形', 723, 89.00, 'cloth'),
    (6, 5, '男士 POLO 衫', '翻领 / 商务', '/img/product/tshirt.jpg', '成熟稳重', 445, 139.00, 'cloth'),
    (6, 5, '男士商务手提包', '头层牛皮 / 大容量', '/img/product/handbag.jpg', '通勤出差有面子', 234, 599.00, 'c2'),
]

COLOR2 = ['曜石黑', '珍珠白']
COLOR3 = ['曜石黑', '珍珠白', '星空银']
CL3 = ['曜石黑', '珍珠白', '薄雾蓝']
CL2 = ['黑色', '卡其']
CLOTH_SIZES = [('M', 0), ('L', 0), ('XL', 10)]
CLOTH_COLORS = ['黑色', '白色']


def skus_for(pid, spec_type, price, rnd):
    """按规格类型生成 SKU 列表 [(spec_json, price, stock, remark)]"""
    out = []
    if spec_type == 'single':
        out.append(({}, price, rnd.randint(50, 400), None))
    elif spec_type == 'c2':
        for c in COLOR2:
            out.append(({'颜色': c}, price, rnd.randint(80, 400), None))
    elif spec_type == 'c3':
        for c in COLOR3:
            out.append(({'颜色': c}, price, rnd.randint(60, 300), None))
    elif spec_type == 'cap':
        for c in CL2:
            out.append(({'颜色': c}, price, rnd.randint(150, 500), None))
    elif spec_type == 'cl2':
        for c in CL2:
            out.append(({'颜色': c, '尺码': rnd.choice(['M', 'L', '均码'])}, price, rnd.randint(150, 500), None))
    elif spec_type == 'cl3':
        for c in CL3:
            out.append(({'颜色': c, '版本': '标准版'}, price, rnd.randint(60, 300), None))
    elif spec_type == 'cloth':
        for c in CLOTH_COLORS:
            for s, extra in CLOTH_SIZES[:2]:
                out.append(({'颜色': c, '尺码': s}, price + extra, rnd.randint(100, 400), None))
    return out


def main():
    rnd = random.Random(20260913)  # 固定种子：同一脚本多次运行结果一致
    print('-- 商家：8 个新增商家（seller3~seller10，密码 123456）')
    print('INSERT INTO merchant (id, username, password, apply_status, audit_time, merchant_name, contact, phone, '
          'shop_name, shop_desc, shop_address, shop_status, status, balance) VALUES')
    rows = []
    for mid, uname, mname, contact, phone, sname, sdesc, saddr, bal in MERCHANTS:
        rows.append(f"({mid}, '{uname}', '{PWD}', 1, '2026-08-25 10:00:00', '{mname}', '{contact}', '{phone}', "
                    f"'{sname}', '{sdesc}', '{saddr}', 0, 0, {bal:.2f})")
    print(',\n'.join(rows) + ';')
    print()

    pid = 14
    sku_id = 24
    prod_rows = []
    sku_rows = []
    for mid, cid, title, sub, img, detail, sale, price, spec in P:
        prod_rows.append(f"({pid}, {mid}, {cid}, '{title}', '{sub}', '{img}', '{detail}', 0, {sale})")
        for spec_d, sp, stock, remark in skus_for(pid, spec, price, rnd):
            spec_json = json.dumps(spec_d, ensure_ascii=False, separators=(',', ':'))
            r = 'NULL' if remark is None else f"'{remark}'"
            sku_rows.append(f"({sku_id}, {pid}, '{spec_json}', {sp:.2f}, {stock}, 0, {r})")
            sku_id += 1
        pid += 1

    print(f'-- 商品：{len(P)} 条（id 14-{pid - 1}），覆盖 5 个类目')
    print('INSERT INTO product (id, merchant_id, category_id, title, subtitle, main_img, detail, status, sale_count) VALUES')
    print(',\n'.join(prod_rows) + ';')
    print()
    print(f'-- SKU：{len(sku_rows)} 条（id 24-{sku_id - 1}）')
    print('INSERT INTO sku (id, product_id, spec_json, price, stock, status, remark) VALUES')
    print(',\n'.join(sku_rows) + ';')


if __name__ == '__main__':
    main()
