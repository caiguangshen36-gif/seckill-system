create database if not exists  seckill;
use seckill;

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '加密后的密码',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `seckill_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_name` varchar(100) NOT NULL,
  `seckill_price` decimal(10,2) NOT NULL,
  `stock_count` int NOT NULL DEFAULT 0 COMMENT '【仅作为兜底】真实库存在Redis中',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止ABA问题和异常回补',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-未开始 1-进行中 2-已结束 3-已下架',
  `start_time` bigint NOT NULL COMMENT '改为Unix时间戳(秒)，提升比较性能',
  `end_time` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 秒杀商品测试数据 (20条)
-- status: 0-未开始 1-进行中 2-已结束 3-已下架
-- 时间戳: 2026-05-30 00:00:00 = 1771334400
-- 时间戳: 2026-06-18 23:59:59 = 1773062399
-- ----------------------------
INSERT INTO `seckill_goods` (`goods_name`, `seckill_price`, `stock_count`, `version`, `status`, `start_time`, `end_time`) VALUES
('iPhone 15 Pro Max 256GB', 7999.00, 100, 0, 1, 1771334400, 1773062399),
('小米14 Ultra 512GB', 5999.00, 150, 0, 1, 1771334400, 1773062399),
('华为Mate60 Pro 512GB', 6499.00, 120, 0, 1, 1771334400, 1773062399),
('MacBook Pro 14寸 M4', 12999.00, 50, 0, 1, 1771334400, 1773062399),
('联想拯救者Y9000P 2026', 8999.00, 80, 0, 1, 1771334400, 1773062399),
('iPad Pro 12.9寸 M4', 9999.00, 60, 0, 1, 1771334400, 1773062399),
('华为MatePad Pro 13', 4299.00, 200, 0, 1, 1771334400, 1773062399),
('AirPods Pro 3', 1699.00, 300, 0, 1, 1771334400, 1773062399),
('索尼WH-1000XM6', 2499.00, 180, 0, 1, 1771334400, 1773062399),
('Apple Watch Ultra 3', 5499.00, 90, 0, 1, 1771334400, 1773062399),
('华为Watch GT5', 1899.00, 250, 0, 1, 1771334400, 1773062399),
('PS6 游戏主机', 3999.00, 75, 0, 1, 1771334400, 1773062399),
('Nintendo Switch 2', 2499.00, 120, 0, 1, 1771334400, 1773062399),
('索尼A7M6 微单相机', 15999.00, 40, 0, 1, 1771334400, 1773062399),
('佳能R8 全画幅微单', 11999.00, 55, 0, 1, 1771334400, 1773062399),
('RTX 5090 显卡', 15999.00, 30, 0, 1, 1771334400, 1773062399),
('RTX 5080 显卡', 8999.00, 60, 0, 1, 1771334400, 1773062399),
('三星Galaxy S26 Ultra', 8999.00, 100, 0, 1, 1771334400, 1773062399),
('OPPO Find X8 Pro', 4999.00, 150, 0, 1, 1771334400, 1773062399),
('vivo X200 Pro', 5499.00, 130, 0, 1, 1771334400, 1773062399);

CREATE TABLE `seckill_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号(雪花算法生成)，对外暴露此字段而非自增ID',
  `order_price` decimal(10,2) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已取消 3-已退款',
  `create_time` bigint NOT NULL COMMENT 'Unix时间戳',
  `pay_expire_time` bigint NOT NULL COMMENT '支付截止时间，用于超时自动取消',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_create_time` (`create_time`) COMMENT '按时间清理/统计用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;