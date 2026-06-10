# 高并发秒杀系统

基于 **Spring Boot 3 + Vue 3** 的高并发秒杀系统，采用 Redis 原子化库存扣减、RabbitMQ 异步削峰、分布式限流等方案应对高并发场景。

## 技术栈

### 后端
| 技术 | 说明 |
|------|------|
| Spring Boot 3.2.0 | 核心框架 (Java 17) |
| MyBatis-Plus 3.5 | ORM + 乐观锁 + 雪花算法 ID |
| Spring Security + JWT | 无状态认证与鉴权 |
| Redis + Redisson | 缓存 / Lua 原子脚本 / 分布式锁 / 限流 |
| RabbitMQ | 异步削峰 + 死信队列超时关单 |
| MySQL 8.0 | 持久化存储 |
| SpringDoc OpenAPI | API 文档 |

### 前端
| 技术 | 说明 |
|------|------|
| Vue 3 (Composition API) | 前端框架 |
| TypeScript | 类型安全 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 (持久化) |
| Axios | HTTP 请求封装 |

## 架构亮点

### 解决超卖问题
- **Redis Lua 脚本**：先原子扣减 Redis 缓存库存，再做 MySQL 乐观扣减（`WHERE stock_count > 0`），双重保障
- **数据库唯一约束**：`(user_id, goods_id)` 联合唯一键防止重复下单

### 异步削峰
- 订单创建后通过 **RabbitMQ** 异步写入数据库，平滑流量尖峰
- 死信队列（10 分钟 TTL）自动处理超时未支付订单，恢复库存

### 接口限流
- 自定义 `@RateLimit` 注解 + AOP 切面，基于 Redis INCR 实现分布式滑动窗口
- 下单接口限制 100 QPS，管理接口限制 10 QPS

### 库存定时同步
- 定时任务每分钟将过期商品自动下架并清除缓存

## 项目结构

```
高并发秒杀系统/
├── mvp_backend/                    # Spring Boot 后端
│   ├── src/main/java/com/mvp/
│   │   ├── common/
│   │   │   ├── annotation/         # @RateLimit 限流注解
│   │   │   ├── aspect/             # AOP 切面实现
│   │   │   ├── config/             # Redis/RabbitMQ/Redisson 配置
│   │   │   ├── mq/                 # 消息生产者 & 消费者
│   │   │   └── utils/              # 工具类
│   │   ├── module/
│   │   │   ├── user/               # 用户模块 (注册/登录)
│   │   │   ├── product/            # 商品模块 (CRUD/秒杀列表/库存)
│   │   │   └── order/              # 订单模块 (秒杀下单/取消/查询)
│   │   └── security/               # JWT 认证 & Security 配置
│   └── src/main/resources/
│       ├── lua/                    # Redis Lua 原子脚本
│       └── sql/database.sql        # 建表 & 种子数据
├── mvp_frontend/                   # Vue 3 前端
│   └── src/
│       ├── views/
│       │   ├── Login.vue           # 登录 / 注册
│       │   ├── Layout.vue          # 导航框架
│       │   ├── SeckillGoods.vue    # 秒杀商品列表 (核心页面)
│       │   ├── Orders.vue          # 订单中心 (实时倒计时)
│       │   └── Goods.vue           # 商品管理 CRUD
│       ├── stores/                 # Pinia 状态管理
│       ├── api/                    # 接口封装
│       └── router/                 # 路由配置
└── README.md
```

## 秒杀核心流程

```
用户请求 → @RateLimit 限流 → 验证时间窗口 → 防重复购买
    → Lua 脚本扣减 Redis 库存 → MySQL 库存乐观扣减
    → 雪花算法生成订单号 → RabbitMQ 异步落库
    → 订单进入死信队列 (10min TTL) → 超时自动取消并回滚库存
```

## 快速启动

### 环境要求
- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8.0
- Redis 7.0+
- RabbitMQ 3.12+

### 1. 初始化数据库

执行 `mvp_backend/src/main/resources/sql/database.sql` 创建库表并导入种子数据。

### 2. 启动后端

```bash
cd mvp_backend
# 修改 src/main/resources/application.yaml 中的数据库、Redis、RabbitMQ 连接信息
mvnw spring-boot:run
```

服务启动在 `http://localhost:8080`，API 文档：`http://localhost:8080/doc.html`

### 3. 启动前端

```bash
cd mvp_frontend
npm install
npm run dev
```

前端启动在 `http://localhost:5173`，开发模式下自动代理 API 请求到后端。

### 4. 压测（可选）

项目已预置 `TestDataInitializer.java`，可批量生成 Jmeter 测试 Token 用于并发压测。

## 核心接口

| 接口 | 方法 | 说明 | 限流 |
|------|------|------|------|
| `/user/login` | POST | 用户登录 | - |
| `/user/register` | POST | 用户注册 | - |
| `/product/list` | GET | 秒杀商品列表 | 200 QPS |
| `/product/active` | GET | 活跃秒杀商品 | 100 QPS |
| `/order` | POST | 秒杀下单 | 100 QPS |
| `/order/list` | POST | 我的订单 | 100 QPS |
| `/order/cancel` | POST | 取消订单 | 50 QPS |

## 待优化项

- [ ] 增加分布式锁保护数据库扣减
- [ ] 引入 Nginx 负载均衡 + 多实例部署
- [ ] 读写分离（主库写订单、从库查商品）
- [ ] Docker Compose 一键部署
- [ ] 前端增加图片 / 商品详情页
