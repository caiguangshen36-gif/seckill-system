# 高并发秒杀系统

基于 Spring Boot + Vue 3 的高并发秒杀系统，采用 Redis 缓存、Lua 脚本原子操作、RabbitMQ 消息队列、Redisson 分布式锁等技术解决秒杀场景下的库存超卖、高并发流量冲击等问题。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2、Spring Security、Spring AOP |
| ORM | MyBatis-Plus 3.5.7（分页、乐观锁） |
| 数据库 | MySQL 8.0 |
| 缓存 & 分布式 | Redis（Redisson 3.23.3）、Lua 原子脚本 |
| 消息队列 | RabbitMQ（死信队列处理超时订单） |
| 认证 | JWT（jjwt 0.12.5）+ Redis Token 校验 |
| 前端框架 | Vue 3 + Pinia + Vue Router 5 |
| UI 组件库 | Element Plus |
| 构建工具 | Vite 8、TypeScript 6 |

## 项目结构

```
高并发秒杀系统/
├── mvp_backend/                  # Spring Boot 后端
│   └── src/main/java/com/mvp/
│       ├── common/               # 公共组件（配置、注解、切面、工具类、MQ、异常处理）
│       ├── module/
│       │   ├── user/             # 用户模块（登录/注册）
│       │   ├── product/          # 商品模块（CRUD、库存管理、定时任务）
│       │   └── order/            # 订单模块（秒杀下单、取消、超时处理）
│       ├── security/             # Spring Security + JWT 过滤器
│       └── test/                 # 测试数据初始化
├── mvp_frontend/                 # Vue 3 前端
│   └── src/
│       ├── api/                  # Axios 接口封装
│       ├── router/               # Vue Router 路由
│       ├── stores/               # Pinia 状态管理
│       ├── utils/                # 请求拦截器（JWT、JSONbig）
│       └── views/                # 页面组件
├── Phase1_压测报告.pdf            # 压测报告
├── Phase2_压测报告.pdf            # 压测报告
├── Phase3_压测报告.pdf            # 压测报告
├── 压测总结报告.pdf               # 压测报告
└── 性能优化记录.pdf               # 压测报告
```

## 核心功能

- **用户模块**：注册、登录（BCrypt + JWT 无状态认证）
- **商品管理**：秒杀商品 CRUD、活动时间管理、过期商品自动下架（定时任务）
- **秒杀下单**：Redis 预扣库存 → Lua 脚本原子扣减 → 数据库兜底扣减 → 防重复购买
- **订单管理**：订单查询、主动取消、超时自动取消（RabbitMQ 死信队列）
- **接口限流**：自定义 `@RateLimit` 注解 + Redis INCR 计数器实现分布式限流

## 秒杀核心技术方案

```
请求 → @RateLimit 接口限流
     → 校验秒杀时间 & 用户购买记录
     → Redis Lua 脚本原子扣减库存（防超卖）
     → 数据库写入订单（兜底）
     → RabbitMQ 发送消息（异步处理 + 超时取消）
```

- **防超卖**：Redis 缓存库存 + Lua 原子预扣 + DB 行级 `UPDATE WHERE stock_count > 0` 兜底
- **防重复购买**：DB 唯一索引 `uk_user_goods` + Redis 用户购买记录
- **流量削峰**：接口限流 + 消息队列异步解耦
- **超时处理**：订单队列 TTL 10 分钟 → 死信队列 → 自动取消 + 恢复库存

## 快速开始

### 环境要求

- JDK 17、Maven 3.8+
- MySQL 8.0、Redis、RabbitMQ 3.x
- Node.js >= 20.19

### 1. 初始化数据库

执行 `mvp_backend/src/main/resources/sql/database.sql` 创建库表及 20 条测试商品数据。

### 2. 修改配置

编辑 `mvp_backend/src/main/resources/application.yaml`，修改数据库、Redis、RabbitMQ 的连接信息。

### 3. 启动后端

```bash
cd mvp_backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd mvp_frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，通过 Vite 代理将 `/api` 请求转发到后端 8080 端口。

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

### 瓶颈发现与修复

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
| 4 | Phase 1 | 热路径用简单 DECR（GET→检查→DECR 非原子） | 🟡 | 改用 `checkAndDecrementStock` | DECR+负数自动回滚 |
| 5 | Phase 1 | 订单初始状态错标为"已支付" (status=1) | 🟡 | 修正为 status=0（待支付） | 订单生命周期完整 |
| 6 | Phase 3 | MQ 死信队列 0 条消息，超时订单永不取消 | 🔴 | `@Scheduled(fixedRate=30000)` 替代 | 超时订单 ≤30s 自动取消 |

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

## 压测报告详情

以下为各阶段完整压力测试报告 PDF 文档：

| 文档 | 说明 |
|------|------|
| [Phase1 压测报告](./Phase1_压测报告.pdf) | 第一阶段基准压测：裸机单服务 + 基础接口性能基线 |
| [Phase2 压测报告](./Phase2_压测报告.pdf) | 第二阶段压测：引入缓存、Lua 原子操作后的性能对比 |
| [Phase3 压测报告](./Phase3_压测报告.pdf) | 第三阶段压测：完整方案（限流 + MQ + 分布式锁）验证 |
| [压测总结报告](./压测总结报告.pdf) | 三阶段横向对比总结，系统吞吐量 QPS 提升分析 |
| [性能优化记录](./性能优化记录.pdf) | 关键优化点详细记录：SQL、Redis、JVM 参数调优过程 |

> 压测工具：JMeter，包含详细 QPS、RT、错误率、系统资源监控数据。

## 接口文档

项目集成 SpringDoc OpenAPI，启动后端后访问：`http://localhost:8080/swagger-ui.html`

API 统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```




