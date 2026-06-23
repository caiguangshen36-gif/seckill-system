# 高并发秒杀系统

基于 **Spring Boot 3 + Vue 3** 的高并发秒杀系统，采用 Redis 原子化库存扣减、RabbitMQ 异步削峰、分布式限流等方案应对高并发场景。

> **在线体验**: http://8.163.136.37 | 用户名: `xiongda` | 密码: `123456`

## 技术栈

### 后端
| 技术 | 说明 |
|------|------|
| Spring Boot 3.2.0 | 核心框架 (Java 17) |
| MyBatis-Plus 3.5.7 | ORM + 乐观锁 + 雪花算法 ID |
| Spring Security + JWT | 无状态认证与鉴权 (jjwt 0.12.5 + java-jwt 4.4.0) |
| Redis + Redisson 3.23.3 | 缓存 / Lua 原子脚本 / 分布式锁 / 限流 |
| RabbitMQ (Spring AMQP) | 异步削峰 + 消息队列 |
| MySQL 8.0 | 持久化存储 |
| SpringDoc OpenAPI 2.2 | API 文档 |
| Hutool 5.8 | 工具类库 |

### 前端
| 技术 | 说明 |
|------|------|
| Vue 3 (Composition API) | 前端框架 |
| TypeScript ~6.0 | 类型安全 |
| Element Plus 2.14 | UI 组件库 |
| Pinia 3.0 + persistedstate | 状态管理 (持久化) |
| Axios 1.16 | HTTP 请求封装 |
| json-bigint | Snowflake ID 精度处理 |

## 架构亮点

### 解决超卖问题
- **Redis Lua 脚本**：`check_and_decrement.lua` 原子 DECR + 负数自动回滚，再做 MySQL 乐观扣减（`WHERE stock_count > 0`），双重保障
- **数据库唯一约束**：`(user_id, goods_id)` 联合唯一键防止重复下单
- **Redis 库存预热**：`@PostConstruct` 启动时自动将 DB 库存同步到 Redis，消除冷启动穿透

### 异步削峰
- 订单创建后通过 **RabbitMQ** 异步发送消息，平滑流量尖峰
- **`@Scheduled` 定时扫描**（每 30 秒）自动取消超时未支付订单并双恢复库存（Redis + DB）
- 支付有效期 15 分钟（`PAY_EXPIRE_SECONDS = 900`）

### 接口限流
- 自定义 `@RateLimit` 注解 + AOP 切面，基于 Redis INCR 实现分布式滑动窗口
- 下单接口限制 1000 QPS，管理接口限制 10 QPS。详见[核心接口](#核心接口)

### 库存定时同步
- `@Scheduled(fixedRate = 60000)` 每分钟将过期商品自动下架并清除缓存
- `@PostConstruct` 启动时预热活动列表缓存（4 种分页规格），消除首个请求 cache miss

## 项目结构

```
高并发秒杀系统/
├── mvp_backend/                    # Spring Boot 后端
│   ├── src/main/java/com/mvp/
│   │   ├── common/
│   │   │   ├── annotation/         # @RateLimit 限流注解
│   │   │   ├── aspect/             # AOP 切面实现
│   │   │   ├── config/             # Redis/RabbitMQ/Redisson 配置
│   │   │   ├── dto/                # 通用 DTO（分页请求等）
│   │   │   ├── exption/            # 全局异常处理 & 业务异常
│   │   │   ├── mq/                 # 消息生产者 & 消费者
│   │   │   └── utils/              # 工具类（RedissonCacheService、ThreadLocalUtil、JWT）
│   │   ├── module/
│   │   │   ├── user/               # 用户模块 (注册/登录)
│   │   │   ├── product/            # 商品模块 (CRUD/秒杀列表/库存)
│   │   │   └── order/              # 订单模块 (秒杀下单/取消/查询/超时扫描)
│   │   └── security/               # JWT 认证 & Security 配置
│   └── src/main/resources/
│       ├── lua/                    # 5 个 Redis Lua 原子脚本
│       ├── sql/database.sql        # 建表 & 种子数据
│       ├── application.yaml        # 本地环境配置
│       └── application-docker.yaml # Docker 环境配置
├── mvp_frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── Login.vue           # 登录 / 注册
│   │   │   ├── Layout.vue          # 导航框架
│   │   │   ├── SeckillGoods.vue    # 秒杀商品列表 (核心页面)
│   │   │   ├── Orders.vue          # 订单中心 (实时倒计时)
│   │   │   └── Goods.vue           # 商品管理 CRUD
│   │   ├── stores/                 # Pinia 状态管理
│   │   ├── api/                    # 接口封装
│   │   ├── components/             # 公共组件
│   │   ├── utils/                  # Axios 封装 (request.js)
│   │   └── router/                 # 路由配置
│   ├── nginx.conf                  # Nginx 配置 (Docker 部署)
│   └── Dockerfile                  # 前端镜像构建
├── docker-compose.yml              # 一键编排 (MySQL + Redis + RabbitMQ + 后端 + 前端)
├── Docker部署指南.md                # 详细部署文档
├── 压测总结报告.md                  # 压测完整报告
├── 性能优化记录.md                  # 优化过程详细记录
└── README.md
```

## 秒杀核心流程

```
用户请求 → @RateLimit 限流 (1000 QPS) → JWT 认证
  → 验证秒杀时间窗口 → 防重复购买 (uk_user_goods)
  → Redis Lua 原子扣减 (checkAndDecrementStock, 负数自动回滚)
  → MySQL 库存乐观扣减 (WHERE stock_count > 0)
  → 雪花算法生成订单号 → RabbitMQ 异步消息
  → 订单进入待支付状态 (status=0, 15分钟支付窗口)
  → @Scheduled 每30秒扫描超时订单 → 自动取消并双恢复库存 (Redis + DB)
```

## 快速启动

### 环境要求
- JDK 17
- Maven 3.8+
- Node.js `^20.19.0 || >=22.12.0`
- MySQL 8.0
- Redis 7.0+
- RabbitMQ 3.12+

### 方式一：Docker Compose 一键部署（推荐）

```bash
cd mvp
docker compose up -d --build
```

首次构建约 5-10 分钟。完成后访问 `http://localhost`（前端）和 `http://localhost:8080/doc.html`（API 文档）。

前置条件：服务器内存 ≥ 4GB，安装 Docker + Docker Compose。详见 [Docker部署指南.md](./Docker部署指南.md)。

### 方式二：本地开发

#### 1. 初始化数据库

执行 `mvp_backend/src/main/resources/sql/database.sql` 创建库表并导入种子数据。

#### 2. 启动后端

```bash
cd mvp_backend
# 修改 src/main/resources/application.yaml 中的数据库、Redis、RabbitMQ 连接信息
# RabbitMQ 默认凭据: user=cai_shen, password=123456
./mvnw spring-boot:run
```

服务启动在 `http://localhost:8080`，API 文档：`http://localhost:8080/doc.html`

#### 3. 启动前端

```bash
cd mvp_frontend
npm install
npm run dev
```

前端启动在 `http://localhost:5173`，开发模式下自动代理 API 请求到后端。

## 核心接口

| 接口 | 方法 | 说明 | 限流 |
|------|------|------|------|
| `/user/login` | POST | 用户登录 | 无需认证 |
| `/user/register` | POST | 用户注册 | 无需认证 |
| `/product/detail` | GET | 商品详情（带缓存） | 2000 QPS |
| `/product/active` | POST | 活跃秒杀商品列表（Redis 缓存 TTL 10s） | 1000 QPS |
| `/product/list` | POST | 全部商品列表 | 200 QPS |
| `/product/stock` | GET | 查询商品库存 | 500 QPS |
| `/order` | POST | 秒杀下单 | 1000 QPS |
| `/order/list` | POST | 我的订单 | 100 QPS |
| `/order/cancel` | POST | 取消订单 | 50 QPS |

> 注：以上限流值经 Phase 1-3 压测验证调整。`/product/add`、`/product/update`、`/product/delete` 管理接口限流 10 QPS。

## 压测与性能验证

> **环境**：Docker Compose 5 容器（2C4G 云服务器） | **周期**：2026-06-21 ~ 06-22 | **总请求**：~157 万 | **总订单**：~3,700

### 三阶段数据对比

| 指标 | Phase 1 初测 | Phase 1 重测 | Phase 2 | Phase 3 |
|------|:---:|:---:|:---:|:---:|
| 测试模式 | 单接口 | 单接口 | 混合 1min/级 | 混合 2min/级+驻留 |
| 总请求 | ~30K | ~30K | ~280K | **~1,055K** |
| 峰值总 QPS | — | — | 772 (@c=200) | 777 (@c=100) |
| 稳态总 QPS | — | — | ~740 (c≥500) | ~670 (c≥500) |
| `/order` QPS (c=50) | **0** (限流拦截) | 109 | 128 | 150 |
| `/order` P99 (c=500) | — | — | 1599ms | 2270ms |
| `/product/active` P99 (c=500) | 534ms | **1308ms** | 1652ms | 2300ms |
| `/product/active` P99 (c=200) | 582ms | 462ms | 935ms | 988ms |
| 超卖 | 0 | 0 | 0 | 0 |
| 重复订单 | 0 | 0 | 0 | 0 |
| Redis/DB 偏差 | 0 | 0 | 0 | 0 |

### 瓶颈发现与修复（6 个缺陷全部修复）

```
Phase 1 初测 → 瓶颈: 限流层 (100/s)
    ↓ 优化1: 限流 100→1000/s
Phase 1 重测 → 瓶颈: DB 查询层 (active P99 1308ms)
    ↓ 优化2: 加 Redis 缓存 TTL 10s + 启动预热
    ↓ 优化3: Redis 库存 @PostConstruct 自动预热
    ↓ 优化4: Lua 脚本改用 checkAndDecrementStock (原子 DECR + 负数回滚)
    ↓ 优化5: 订单状态修正 (1→0 待支付)
Phase 2 → 瓶颈: DB 连接池竞争 (混合场景总 QPS ~770)
    ↓ 验证混合场景容量，0 超卖
Phase 3 → 瓶颈: DB 连接池竞争 (总 QPS ~670 @ c≥500)
    → 新发现: MQ 死信队列超时取消从未触发 (消费者立即 ACK)
    ↓ 优化6: @Scheduled 每30秒定时扫描超时订单
```

| # | 发现阶段 | 问题 | 严重度 | 修复 | 效果 |
|---|----------|------|:---:|------|------|
| 1 | Phase 1 | `/order` 限流 100/s 导致 c≥50 成功率 0% | 🔴 | 上调至 1000/s | 5,115→0 次限流拦截 |
| 2 | Phase 1 | `/product/active` 无缓存 P99 飙至 1308ms | 🔴 | Redis 缓存 TTL 10s + 启动预热 | 缓存命中响应 123ms→23ms (**5.3x**) |
| 3 | Phase 1 | Redis 库存键缺失，Lua 扣减形同虚设 | 🟡 | `@PostConstruct` 启动自动预热 | 20 商品自动同步 |
| 4 | Phase 1 | 热路径用简单 DECR（GET→检查→DECR 非原子） | 🟡 | 改用 `checkAndDecrementStock` | DECR+负数自动回滚，消除超卖风险 |
| 5 | Phase 1 | 订单初始状态错标为"已支付" (status=1) | 🟡 | 修正为 status=0（待支付） | 订单生命周期完整 |
| 6 | Phase 3 | MQ 死信队列 **0 条消息**，超时订单永不取消 | 🔴 | `@Scheduled(fixedRate=30000)` 替代 | 超时订单 ≤30s 自动取消+双恢复库存 |

### 长时稳定性验证（Phase 3）

| 指标 | 结果 |
|------|------|
| 10min @ 800 并发持续稳定性 | QPS 波动 2.5%，P99 波动 6.3%，无劣化 |
| 降载恢复 | P99 从 2.3s → 450ms，**<1 分钟**完全恢复 |
| 极限并发 1000 | 不 OOM、不死锁、不崩溃 |
| 数据一致性 | 123 次一致性检查，~157 万请求，**0 超卖，0 重复** |

### 系统容量评估

```
┌─────────────────────────────────────────────────────┐
│  峰值总 QPS:  ~777/s   (c=100, 混合)                  │
│  稳态总 QPS:  ~670/s   (c≥500, 混合)                  │
│  安全并发:    ≤200     (P99 < 1s)                     │
│  饱和并发:    ≥500     (P99 ~1.6-2.3s, 稳定运行)       │
│  极限并发:    1000     (不 OOM, 不死锁)                │
│  持续稳定:    10min    @ 800 并发, 0 劣化              │
│  降载恢复:    <1min    (P99 2.3s → 450ms)             │
│  数据一致:    100%     (~1.57M 请求, 0 超卖)           │
│  超时取消:    ≤30s     (@Scheduled 定时扫描)           │
│  库存预热:    自动     (20 商品, @PostConstruct)       │
└─────────────────────────────────────────────────────┘
```

> 详细优化过程见：[性能优化记录.md](./性能优化记录.md) | [Phase1](./Phase1_压测报告.md) | [Phase2](./Phase2_压测报告.md) | [Phase3](./Phase3_压测报告.md)

## 待优化项

| 项目 | 优先级 | 说明 |
|------|--------|------|
| DB 连接池优化 | P1 | HikariCP `maximumPoolSize` 当前默认 10，c≥500 时 P99 由连接池竞争决定，建议增大到 20-30 |
| `pay_expire_time` 加复合索引 | P1 | `WHERE status=0 AND pay_expire_time < ?` 提升定时扫描效率 |
| Prometheus + Grafana 监控 | P2 | 实时监控连接池、GC、QPS、P99，替代脚本监控 |
| 读写分离 | P2 | `/product/detail`、`/product/active` 等读接口走从库 |
| 限流器升级为滑动窗口 | P3 | 当前 INCR+固定 TTL 存在窗口边界误杀风险，可升级为 Redisson RRateLimiter |
| MQ 流程重构 | P3 | 考虑将 `handleOrderCreate` 改为延迟消费，或移除冗余的 MQ 创建消息路径 |
| 前端增加图片/商品详情页 | P3 | 提升用户体验 |
