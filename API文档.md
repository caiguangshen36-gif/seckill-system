# 高并发秒杀系统 — 后端接口文档

> 版本：v1.0 | 更新日期：2026-06-26 | 基于实际代码追踪

---

## 一、全局约定

### 1.1 Base URL

| 环境 | 地址 |
|------|------|
| 本地开发（后端直连） | `http://localhost:8080` |
| 本地开发（前端 Vite 代理） | `/api/*` → 代理到 `http://localhost:8080`（自动去除 `/api` 前缀） |
| 生产环境 | 【待确认】 |

> 后端无 `server.servlet.context-path`，所有接口路径以 `/` 为根。

### 1.2 统一响应体结构

所有接口返回统一格式 `Result<T>`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `Integer` | 状态码，`200`=成功，`400`=参数错误，`500`=服务端错误 |
| `msg` | `String` | 提示信息 |
| `data` | `T` (泛型) | 业务数据，无数据时为 `null` |

**已知问题：** 前端响应拦截器（`request.js`）读取 `result.data.message`，但后端字段名为 `msg`，导致错误路径下前端始终回退到 `"服务异常"` 而非后端实际错误消息。此问题已记录，将在后续迭代修复。

### 1.3 认证方式

| 项目 | 约定 |
|------|------|
| 认证类型 | JWT（HMAC256），无状态 Session |
| Token 传递方式 | 请求头 `Authorization: Bearer <token>` |
| JWT 有效期 | 12 小时 |
| Redis Token TTL | 1 小时（**实际生效 TTL**：1 小时后 Token 在 Redis 中被清除，请求被拒） |
| 获取当前用户 | 后端通过 `ThreadLocalUtil.getUserId()` 从 JWT claims 的 `id` 字段提取 |

**白名单路径（无需 Token）：**

| 路径 | 说明 |
|------|------|
| `/user/login` | 用户登录 |
| `/user/register` | 用户注册 |
| `/product/detail` | 商品详情 |
| `/product/list` | 商品列表 |
| `/product/active` | 活跃商品 |
| `/product/stock` | 商品库存 |
| `/product/query` | 商品查询 |

### 1.4 错误码

当前项目未定义错误码枚举，仅在 `Result` 和 `GlobalExceptionHandler` 中硬编码三个状态码：

| Code | 含义 | 触发场景 |
|:----:|------|----------|
| `200` | 成功 | `Result.success()` |
| `400` | 请求参数错误 | `IllegalArgumentException` 被全局异常处理器捕获 |
| `500` | 服务端错误 | `Result.error()`、`BusinessException`、`Exception`、`NullPointerException` |

### 1.5 分页约定

| 项目 | 约定 |
|------|------|
| 请求方式 | `POST` + `@RequestBody` JSON |
| 请求字段 | `pageNum`（默认 `1`）、`pageSize`（默认 `10`） |
| 响应结构 | MyBatis-Plus `IPage<T>`：`{ records, total, size, current, pages }` 包裹在 `Result.data` 中 |

**分页请求示例：**

```json
{
  "pageNum": 1,
  "pageSize": 10
}
```

**分页响应示例：**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [ ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

### 1.6 字段命名约定

全链路统一 **camelCase**（小驼峰）：Java 实体 → Jackson 序列化 → 前端 JSON，无下划线转换。

### 1.7 鉴权标注

接口清单中使用以下标记：

| 标记 | 含义 |
|:---:|------|
| 🔓 | 白名单，无需 Token |
| 🔒 | 需要 Token（`Authorization: Bearer <token>`） |

### 1.8 限流

部分接口带有 `@RateLimit` 注解，使用 Redis INCR 固定窗口计数器实现。超出限制时返回错误。

---

---

## 二、用户模块 `/user`

### 2.1 POST /user/login — 用户登录

> 🔓 白名单 · 版本 v1.0

| 项目 | 说明 |
|------|------|
| **路径** | `/user/login` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 无 |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| `username` | `String` | 是 | `@NotBlank` | 用户名 |
| `password` | `String` | 是 | `@NotBlank` | 密码（明文，传输层依赖 HTTPS） |

**请求示例**

```json
{
  "username": "zhangsan",
  "password": "mypassword123"
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "eyJhbGciOiJIUzI1NiIs..."
}
```

> `data` 为 JWT Token 字符串，客户端需存储并在后续请求中通过 `Authorization: Bearer <token>` 传递。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 必填字段为空 | `400` | `"用户名不能为空"` / `"密码不能为空"`（由 `@Validated` + `@NotBlank` 触发） |
| 用户不存在 | `500` | `"用户不存在"` |
| 密码错误 | `500` | `"密码错误"` |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"mypassword123"}'
```

---

### 2.2 POST /user/register — 用户注册

> 🔓 白名单 · 版本 v1.0

| 项目 | 说明 |
|------|------|
| **路径** | `/user/register` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 无 |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| `username` | `String` | 是 | `@NotBlank` | 用户名 |
| `password` | `String` | 是 | `@NotBlank` | 密码（明文，后端 BCrypt 加密存储） |

**请求示例**

```json
{
  "username": "newuser",
  "password": "securePass123"
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "注册成功"
}
```

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 必填字段为空 | `400` | `"用户名不能为空"` / `"密码不能为空"` |
| 用户名已存在 | `500` | `"用户已存在"` |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"securePass123"}'
```

---

### 2.3 GET /user/info — 获取当前用户信息

> 🔒 需要 Token · 版本 v1.0

| 项目 | 说明 |
|------|------|
| **路径** | `/user/info` |
| **Method** | `GET` |
| **限流** | 无 |
| **认证** | `Authorization: Bearer <token>`（从 JWT claims 中提取 `id`，查询对应用户） |

**请求参数**

无。用户 ID 从 JWT Token 中服务端提取，无需客户端传入。

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "password": "$2a$10$..."
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 用户 ID |
| `username` | `String` | 用户名 |
| `password` | `String` | BCrypt 加密后的密码密文 |

> ⚠ **注意：** `data` 中包含 `password` 字段（BCrypt 密文），当前后端直接返回 `User` 实体，未做脱敏处理。后续迭代建议在 VO 层移除该字段。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 未登录 / Token 过期 | `401` | （由 Spring Security 返回，非 `Result` 格式） |
| Token 有效但用户不存在 | `500` | `"服务器异常，请稍后重试"`（实际抛 `NullPointerException`，被全局异常处理器捕获） |

**cURL 示例**

```bash
curl -X GET http://localhost:8080/user/info \
  -H "Authorization: Bearer <your_token>"
```

---

---

## 三、商品模块 `/product`

### 3.1 POST /product/add — 添加秒杀商品

> 🔒 需要 Token · 版本 v1.0 · 限流 10/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/add` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 10 次/秒（超出返回 `500 "操作过于频繁"`） |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| `id` | `Long` | 否 | — | 添加时不需要传（自增主键） |
| `goodsName` | `String` | 是 | `@NotBlank` | 商品名称 |
| `seckillPrice` | `BigDecimal` | 是 | `@NotNull @Positive` | 秒杀价格（元） |
| `stockCount` | `Integer` | 是 | `@NotNull @Positive` | 库存数量 |
| `startTime` | `Long` | 是 | `@NotNull` | 秒杀开始时间（Unix 秒） |
| `endTime` | `Long` | 是 | `@NotNull` | 秒杀结束时间（Unix 秒） |

**请求示例**

```json
{
  "goodsName": "iPhone 15 Pro Max",
  "seckillPrice": 1.00,
  "stockCount": 100,
  "startTime": 1719504000,
  "endTime": 1719590400
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "添加成功"
}
```

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 必填字段为空 | `400` | `"商品名称不能为空"` 等（`@Validated` 触发） |
| 价格/库存为非正数 | `400` | `"秒杀价格必须为正数"` / `"库存数量必须为正数"` |
| 开始时间 ≥ 结束时间 | `500` | `"开始时间必须早于结束时间"` |
| 触发限流 | `500` | `"操作过于频繁"` |

**后端处理说明**
- 写入 `seckill_goods` 表后，同步预热 Redis 库存缓存（`product:stock:{id}`）
- 清除商品详情缓存，确保下次查询时重新加载

**cURL 示例**

```bash
curl -X POST http://localhost:8080/product/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{
    "goodsName": "iPhone 15 Pro Max",
    "seckillPrice": 1.00,
    "stockCount": 100,
    "startTime": 1719504000,
    "endTime": 1719590400
  }'
```

---

### 3.2 POST /product/update — 更新秒杀商品

> 🔒 需要 Token · 版本 v1.0 · 限流 10/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/update` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 10 次/秒（超出返回 `500 "操作过于频繁"`） |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| `id` | `Long` | **是** | — | 要更新的商品 ID |
| `goodsName` | `String` | 是 | `@NotBlank` | 商品名称 |
| `seckillPrice` | `BigDecimal` | 是 | `@NotNull @Positive` | 秒杀价格（元） |
| `stockCount` | `Integer` | 是 | `@NotNull @Positive` | 库存数量 |
| `startTime` | `Long` | 是 | `@NotNull` | 秒杀开始时间（Unix 秒） |
| `endTime` | `Long` | 是 | `@NotNull` | 秒杀结束时间（Unix 秒） |

> ⚠ **注意：** 更新操作为**全量替换**，所有必填字段（含 `id`）缺一不可。

**请求示例**

```json
{
  "id": 1,
  "goodsName": "iPhone 15 Pro Max（降价）",
  "seckillPrice": 0.01,
  "stockCount": 50,
  "startTime": 1719504000,
  "endTime": 1719590400
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "更新成功"
}
```

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 商品不存在 | `500` | `"商品不存在"` |
| 开始时间 ≥ 结束时间 | `500` | `"开始时间必须早于结束时间"` |
| 字段校验失败 | `400` | 同 3.1 校验错误 |
| 触发限流 | `500` | `"操作过于频繁"` |

**后端处理说明**
- 更新 DB 后同步更新 Redis 库存缓存（`product:stock:{id}`）
- 清除商品详情缓存

**cURL 示例**

```bash
curl -X POST http://localhost:8080/product/update \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{
    "id": 1,
    "goodsName": "iPhone 15 Pro Max（降价）",
    "seckillPrice": 0.01,
    "stockCount": 50,
    "startTime": 1719504000,
    "endTime": 1719590400
  }'
```

---

### 3.3 POST /product/delete — 删除秒杀商品

> 🔒 需要 Token · 版本 v1.0 · 限流 10/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/delete` |
| **Method** | `POST` |
| **Content-Type** | N/A（使用 Query 参数） |
| **限流** | 10 次/秒（超出返回 `500 "操作过于频繁"`） |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | `Long` | 是 | 要删除的商品 ID |

**请求示例**

```
POST /product/delete?id=1
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "删除成功"
}
```

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 商品不存在 | `500` | `"商品不存在"` |
| 触发限流 | `500` | `"操作过于频繁"` |

**后端处理说明**
- 删除 DB 记录后，同步清除 Redis 中的商品缓存和库存缓存

**cURL 示例**

```bash
curl -X POST "http://localhost:8080/product/delete?id=1" \
  -H "Authorization: Bearer <your_token>"
```

---

### 3.4 GET /product/detail — 查询商品详情

> 🔓 白名单 · 版本 v1.0 · 限流 500/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/detail` |
| **Method** | `GET` |
| **限流** | 500 次/秒（超出返回 `500 "系统繁忙，请稍后重试"`） |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | `Long` | 是 | 商品 ID |

**请求示例**

```
GET /product/detail?id=1
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "goodsName": "iPhone 15 Pro Max",
    "seckillPrice": 1.00,
    "stockCount": 100,
    "version": 0,
    "status": 1,
    "statusDesc": "进行中",
    "startTime": 1719504000,
    "endTime": 1719590400
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 商品 ID |
| `goodsName` | `String` | 商品名称 |
| `seckillPrice` | `BigDecimal` | 秒杀价格 |
| `stockCount` | `Integer` | 库存数量 |
| `version` | `Integer` | 乐观锁版本号 |
| `status` | `Integer` | 活动状态：0=未开始，1=进行中，2=已结束，3=已下架 |
| `statusDesc` | `String` | 状态描述文本 |
| `startTime` | `Long` | 秒杀开始时间（Unix 秒） |
| `endTime` | `Long` | 秒杀结束时间（Unix 秒） |

> **说明：** `status` 由后端根据当前时间动态计算（非 DB 中存储的值）。缓存优先——命中 Redis 直接返回，未命中则查 DB 后回填缓存。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 商品不存在 | `500` | `"商品不存在"` |
| 触发限流 | `500` | `"系统繁忙，请稍后重试"` |

**cURL 示例**

```bash
curl -X GET "http://localhost:8080/product/detail?id=1"
```

---

### 3.5 POST /product/list — 全部商品分页列表

> 🔓 白名单 · 版本 v1.0 · 限流 200/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/list` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 200 次/秒（超出返回 `500 "系统繁忙，请稍后重试"`） |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:---:|:------|------|
| `pageNum` | `Integer` | 否 | `1` | 页码 |
| `pageSize` | `Integer` | 否 | `10` | 每页条数 |

**请求示例**

```json
{
  "pageNum": 1,
  "pageSize": 10
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "goodsName": "iPhone 15 Pro Max",
        "seckillPrice": 1.00,
        "stockCount": 100,
        "version": 0,
        "status": 1,
        "statusDesc": "进行中",
        "startTime": 1719504000,
        "endTime": 1719590400
      }
    ],
    "total": 20,
    "size": 10,
    "current": 1,
    "pages": 2
  }
}
```

> 排序规则：按 `id` 降序。`records` 中每条数据字段同 3.4 `ProductVo`。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 触发限流 | `500` | `"系统繁忙，请稍后重试"` |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/product/list \
  -H "Content-Type: application/json" \
  -d '{"pageNum": 1, "pageSize": 10}'
```

---

### 3.6 POST /product/active — 活跃秒杀商品分页列表

> 🔓 白名单 · 版本 v1.0 · 限流 300/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/active` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 300 次/秒（超出返回 `500 "系统繁忙，请稍后重试"`） |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:---:|:------|------|
| `pageNum` | `Integer` | 否 | `1` | 页码 |
| `pageSize` | `Integer` | 否 | `10` | 每页条数 |

**请求示例**

```json
{
  "pageNum": 1,
  "pageSize": 10
}
```

**成功响应 (200)**

同 3.5 `/product/list`，但仅返回满足以下全部条件的商品：
- `startTime` ≤ 当前时间 ≤ `endTime`（在活动时间窗口内）
- `stockCount` ≥ 1（有库存）

排序规则：按 `id` 降序。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 触发限流 | `500` | `"系统繁忙，请稍后重试"` |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/product/active \
  -H "Content-Type: application/json" \
  -d '{"pageNum": 1, "pageSize": 10}'
```

---

### 3.7 POST /product/query — 条件查询商品（分页）

> 🔓 白名单 · 版本 v1.0 · 限流 200/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/query` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 200 次/秒（超出返回 `500 "系统繁忙，请稍后重试"`） |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:---:|:------|------|
| `goodsName` | `String` | 否 | — | 商品名称模糊匹配 |
| `minPrice` | `BigDecimal` | 否 | — | 最低秒杀价格（含） |
| `maxPrice` | `BigDecimal` | 否 | — | 最高秒杀价格（含） |
| `pageNum` | `Integer` | 否 | `1` | 页码 |
| `pageSize` | `Integer` | 否 | `10` | 每页条数 |

> 所有条件均为可选——不传则不筛选，仅做分页返回。

**请求示例**

```json
{
  "goodsName": "iPhone",
  "minPrice": 0.01,
  "maxPrice": 100.00,
  "pageNum": 1,
  "pageSize": 10
}
```

**成功响应 (200)**

同 3.5 `/product/list`，返回满足筛选条件的商品。排序规则：按 `id` 降序。

> 📌 这是前端 **SeckillGoods.vue** 和 **Goods.vue** 实际使用的查询接口，替代了 `/product/list` 和 `/product/active` 的功能。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 触发限流 | `500` | `"系统繁忙，请稍后重试"` |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/product/query \
  -H "Content-Type: application/json" \
  -d '{
    "goodsName": "iPhone",
    "minPrice": 0.01,
    "maxPrice": 100.00,
    "pageNum": 1,
    "pageSize": 10
  }'
```

---

### 3.8 GET /product/stock — 查询商品库存

> 🔓 白名单 · 版本 v1.0 · 限流 500/s（全局限流）

| 项目 | 说明 |
|------|------|
| **路径** | `/product/stock` |
| **Method** | `GET` |
| **限流** | 500 次/秒（超出返回 `500 "系统繁忙，请稍后重试"`） |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | `Long` | 是 | 商品 ID |

**请求示例**

```
GET /product/stock?id=1
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": 100
}
```

> `data` 为 `Integer` 类型的当前库存数量。缓存优先——命中 Redis `product:stock:{id}` 直接返回，未命中则查 DB 后回填缓存。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 商品不存在 | `500` | `"商品不存在"` |
| 触发限流 | `500` | `"系统繁忙，请稍后重试"` |

**cURL 示例**

```bash
curl -X GET "http://localhost:8080/product/stock?id=1"
```

---

---

## 四、订单模块 `/order`

### 4.1 POST /order — 秒杀下单（核心接口）

> 🔒 需要 Token · 版本 v1.0 · 限流 100/s（全局限流）

这是系统的核心业务接口，承载秒杀请求的高并发流量。

| 项目 | 说明 |
|------|------|
| **路径** | `/order` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 100 次/秒（超出返回 `500 "秒杀人数过多，请稍后重试"`） |
| **认证** | `Authorization: Bearer <token>`（从 JWT claims 中提取 `id` 作为 `userId`） |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| `goodsId` | `Long` | 是 | `@NotNull` | 秒杀商品 ID |
| `orderPrice` | `BigDecimal` | 是 | `@NotNull @Positive` | 订单金额（元） |

**请求示例**

```json
{
  "goodsId": 1,
  "orderPrice": 1.00
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 42,
    "userId": 3,
    "goodsId": 1,
    "orderNo": "ORD1845123456789012",
    "goodsName": "iPhone 15 Pro Max",
    "orderPrice": 1.00,
    "status": 1,
    "createTime": 1719504000,
    "payExpireTime": 1719504900
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 订单主键 ID |
| `userId` | `Long` | 下单用户 ID（从 JWT 提取） |
| `goodsId` | `Long` | 秒杀商品 ID |
| `orderNo` | `String` | 业务订单号（雪花算法生成，格式 `ORD` + 19 位数字） |
| `goodsName` | `String` | 商品名称（连表查询，非订单表字段） |
| `orderPrice` | `BigDecimal` | 订单金额（元） |
| `status` | `Integer` | 订单状态：0=待支付，1=已支付，2=已取消，3=已退款 |
| `createTime` | `Long` | 下单时间（Unix 秒） |
| `payExpireTime` | `Long` | 支付截止时间（`createTime + 900` 秒） |

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 必填字段为空/非正数 | `400` | `"商品ID不能为空"` / `"订单价格必须为正数"` |
| 商品不存在 | `500` | `"商品不存在"` |
| 秒杀尚未开始 | `500` | `"秒杀尚未开始"` |
| 秒杀已结束 | `500` | `"秒杀已结束"` |
| 已购买过该商品 | `500` | `"您已购买过该商品"` |
| 库存不足 | `500` | `"库存不足，下单失败"` |
| 触发限流 | `500` | `"秒杀人数过多，请稍后重试"` |

**后端处理流程**

1. 校验商品是否存在、是否在活动时间窗口内
2. 校验用户是否已购买过该商品（`uk_user_goods` 唯一约束 + 业务层查询）
3. Redis Lua 脚本原子扣减库存 → DB 乐观锁兜底扣减
4. 写入 `seckill_order` 表（`status = 1`，即直接已支付）
5. 清除商品详情缓存，缓存订单到 Redis
6. 发送 RabbitMQ 订单创建消息（异步：库存同步、超时取消监控）

> ⚠ **已知问题 ①：** `orderPrice` 由客户端传入，后端未与商品 `seckillPrice` 做一致性校验，理论上存在价格篡改风险。
>
> ⚠ **已知问题 ②：** 订单创建后 `status` 直接设为 `1`（已支付），而取消接口要求 `status = 0`（待支付），因此通过此接口创建的订单**无法通过 `/order/cancel` 取消**。

**cURL 示例**

```bash
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"goodsId": 1, "orderPrice": 1.00}'
```

---

### 4.2 POST /order/list — 我的订单列表

> 🔒 需要 Token · 版本 v1.0 · 无限流

| 项目 | 说明 |
|------|------|
| **路径** | `/order/list` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **限流** | 无 |

**请求参数 (Body — JSON)**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:---:|:------|------|
| `pageNum` | `Integer` | 否 | `1` | 页码 |
| `pageSize` | `Integer` | 否 | `10` | 每页条数 |

**请求示例**

```json
{
  "pageNum": 1,
  "pageSize": 10
}
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [
      {
        "id": 42,
        "userId": 3,
        "goodsId": 1,
        "orderNo": "ORD1845123456789012",
        "goodsName": "iPhone 15 Pro Max",
        "orderPrice": 1.00,
        "status": 1,
        "createTime": 1719504000,
        "payExpireTime": 1719504900
      }
    ],
    "total": 5,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

> ⚠ 仅返回当前登录用户的订单（`userId` 从 JWT 提取）。排序：按 `createTime` 降序。
>
> 当关联商品已被删除时，`goodsName` 返回 `"商品已删除"`（不抛异常）。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 未登录 / Token 过期 | `401` | （Spring Security 拦截，非 `Result` 格式） |

**cURL 示例**

```bash
curl -X POST http://localhost:8080/order/list \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"pageNum": 1, "pageSize": 10}'
```

---

### 4.3 GET /order/{id} — 根据 ID 查询订单

> 🔒 需要 Token · 版本 v1.0 · 无限流

| 项目 | 说明 |
|------|------|
| **路径** | `/order/{id}` |
| **Method** | `GET` |
| **限流** | 无 |

**请求参数 (Path)**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | `Long` | 是 | 订单主键 ID |

**请求示例**

```
GET /order/42
```

**成功响应 (200)**

同 4.1 响应格式。缓存优先——命中 Redis 直接返回，未命中则查 DB（校验所有权后回填缓存）。

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 订单不存在 | `500` | `"订单不存在"` |
| 订单不属于当前用户 | `500` | `"无权查看该订单"` |

> 所有权校验：对比订单 `userId` 与 JWT 中的 `id`，不匹配则拒绝。此校验在缓存命中和 DB 查询两条路径均执行。

**cURL 示例**

```bash
curl -X GET http://localhost:8080/order/42 \
  -H "Authorization: Bearer <your_token>"
```

---

### 4.4 GET /order/no/{orderNo} — 根据订单号查询订单

> 🔒 需要 Token · 版本 v1.0 · 无限流

| 项目 | 说明 |
|------|------|
| **路径** | `/order/no/{orderNo}` |
| **Method** | `GET` |
| **限流** | 无 |

**请求参数 (Path)**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `orderNo` | `String` | 是 | 业务订单号（如 `ORD1845123456789012`） |

**请求示例**

```
GET /order/no/ORD1845123456789012
```

**成功响应 (200)**

同 4.1 响应格式。注意：此接口**不走缓存**，直接查 DB 并连表查商品名称。

**错误响应**

同 4.3（`"订单不存在"` / `"无权查看该订单"`）。

**cURL 示例**

```bash
curl -X GET http://localhost:8080/order/no/ORD1845123456789012 \
  -H "Authorization: Bearer <your_token>"
```

---

### 4.5 POST /order/cancel — 取消订单

> 🔒 需要 Token · 版本 v1.0 · 无限流

| 项目 | 说明 |
|------|------|
| **路径** | `/order/cancel` |
| **Method** | `POST` |
| **Content-Type** | N/A（使用 Query 参数） |
| **限流** | 无 |

**请求参数 (Query)**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `id` | `Long` | 是 | 订单主键 ID |

**请求示例**

```
POST /order/cancel?id=42
```

**成功响应 (200)**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "取消成功"
}
```

**错误响应**

| 场景 | Code | msg |
|------|:----:|------|
| 订单不存在 | `500` | `"订单不存在"` |
| 订单不属于当前用户 | `500` | `"无权操作该订单"` |
| 订单状态非待支付 | `500` | `"只能取消待支付订单"` |

**后端处理流程**

1. 校验订单存在、属于当前用户
2. 校验 `status == 0`（待支付）——**否则拒绝**
3. 更新 `status = 2`（已取消）
4. Redis 库存恢复（`incrementStock`）、清除订单缓存
5. 发送 RabbitMQ 订单取消消息

> ⚠ **致命问题：** 4.1 下单接口创建订单时 `status` 设为 `1`（已支付），而本接口要求 `status == 0`（待支付）。因此**通过 Web 流程创建的订单永远无法通过此接口取消**。此接口目前仅在以下场景可用：
> - 通过 RabbitMQ 死信队列超时回调创建的订单（若该流程设 status=0）
> - 直接操作数据库插入 status=0 的订单

**cURL 示例**

```bash
curl -X POST "http://localhost:8080/order/cancel?id=42" \
  -H "Authorization: Bearer <your_token>"
```

---

---

## 五、非功能性说明

### 5.1 并发安全保障 — 秒杀下单核心链路

秒杀场景的核心挑战是：**高并发下防止超卖**。当前系统采用 Redis Lua + DB 双重保障：

```
POST /order 请求
  │
  ├─ [1] 限流检查（Redis INCR 固定窗口，100/s 全局限流）
  │     └─ 超限 → 抛 BusinessException → HTTP 500
  │
  ├─ [2] 业务校验（产品存在、时间窗口、重复购买）
  │     └─ 任一不通过 → 抛 BusinessException → HTTP 500
  │
  ├─ [3] Redis 缓存检查（product:stock:{id}）
  │     └─ 缓存库存 ≤ 0 → 返回 "库存不足"
  │
  ├─ [4] 【关键】Redis Lua 脚本原子扣减（decrement_stock.lua）
  │     脚本逻辑：
  │     · EXISTS key → GET stock → 检查 stock > 0 → DECR key
  │     · 返回值 -1（key 不存在）/ -2（库存不足）/ 新库存值
  │     └─ 失败 → 返回 "库存不足"
  │
  ├─ [5] 【兜底】DB 乐观锁扣减
  │     SQL: UPDATE seckill_goods SET stock_count = stock_count - 1
  │          WHERE id = ? AND stock_count > 0
  │     └─ 失败（affected rows = 0）→ Redis 回滚（INCR）+ 返回 "库存不足"
  │
  ├─ [6] 写入订单（status = 1，MyBatis-Plus 自增主键）
  │
  ├─ [7] 清除商品缓存 + 缓存新订单（Redis，TTL 300s）
  │
  └─ [8] 发送 RabbitMQ 消息（异步，非阻塞）
```

| 保障层 | 机制 | 作用 |
|--------|------|------|
| 入口限流 | Redis INCR 固定窗口 | 削减峰值，保护下游 |
| Redis 库存预检 | 读 `product:stock:{id}` | 快速失败，避免无效 DB 操作 |
| Redis 原子扣减 | Lua 脚本 `DECR` + `>0` 校验 | **主力防超卖**，单线程执行保证原子性 |
| DB 乐观锁兜底 | `WHERE stock_count > 0` | Redis 异常或缓存不一致时的最后防线 |
| Redis 回滚 | `INCR` 恢复 | DB 扣减失败时补偿 Redis，保持缓存一致性 |

> **关键细节：** `decrementStock.lua`（简单版）被实际调用——直接 DECR，无期望值校验。`check_and_decrement.lua`（带 expectedStock 参数版，支持 Redis 级乐观锁校验）已在 RedissonCacheService 中加载但**未被下单流程使用**，属于预留的高级能力。

### 5.2 消息队列异步流程

RabbitMQ 作为下单后的异步处理通道，架构如下：

```
下单成功（status=1）
  │
  ├──→ order.exchange ──→ order.create.queue（10min TTL）
  │                         │
  │                         ├─ OrderConsumer.handleOrderCreate
  │                         │  检查 status==0 → 跳过（当前 status=1 直接 ACK）
  │                         │
  │                         └─ 10分钟后未消费 → dead.letter.exchange
  │                                                │
  │                                                └─→ order.dead.letter.queue
  │                                                   │
  │                                                   └─ OrderConsumer.handleOrderTimeout
  │                                                      检查 status==0 → 取消订单 + 恢复库存
  │                                                      检查 status!=0 → 跳过
  │
  ├──→ order.exchange ──→ order.cancel.queue
  │                         │
  │                         └─ OrderConsumer.handleOrderCancel
  │                            检查 status==0 → 设为 status=2
  │                            检查 status!=0 → 跳过
  │
  └──→ stock.exchange ──→ stock.update.queue
                            │
                            └─ StockConsumer.handleStockUpdate
                               DECREMENT → DB 扣减库存
                               INCREMENT → DB 恢复库存
```

| 队列 | TTL | 死信队列 | 消费者逻辑 |
|------|:---:|------|------|
| `order.create.queue` | 10 min | `order.dead.letter.queue` | 仅处理 `status=0` 订单 |
| `order.dead.letter.queue` | — | — | 超时未支付 → 取消订单 + 恢复库存 |
| `order.cancel.queue` | — | — | 取消订单（`status=0` → `2`） |
| `stock.update.queue` | — | — | 异步同步 DB 库存（扣减/恢复） |

**确认模式：** 手动 ACK（`acknowledge-mode: manual`）。消费者处理成功后调用 `channel.basicAck`；异常时首次拒绝并重新入队，已重试过的消息直接丢弃（`requeue=false`）。

> ⚠ **设计意图 vs 实际行为差异：** 该架构的设计意图是"下单 → 待支付（status=0）→ 10分钟超时自动取消"。但当前下单接口直接设 `status=1`（已支付），跳过了 pending 状态，导致：
> - `OrderConsumer.handleOrderCreate` — 永远跳过（status != 0）
> - `OrderConsumer.handleOrderCancel` — 永远跳过（status != 0）
> - `OrderConsumer.handleOrderTimeout` — 永远跳过（status != 0）
>
> 三个消费者均处于事实上的空转状态。若后续将下单改为 `status=0`，整套异步流程即可激活。

### 5.3 第三方依赖说明

| 组件 | 用途 | 关键配置 |
|------|------|------|
| **Redis (Lettuce)** | Token 存储（TTL 1h）、限流计数器、Redisson 底层连接 | 连接池 max 200 active |
| **Redisson** | 分布式缓存（商品/订单/库存）、Lua 脚本执行、分布式锁（RLock/FairLock/RedLock）、限流器（RRateLimiter，已封装但未启用） | 与 Lettuce 共享同一 Redis 地址 |
| **RabbitMQ** | 订单创建/取消异步处理、库存异步同步、死信队列超时取消 | 手动 ACK，消费者并发 10-20 |

**Redisson 缓存 Key 设计：**

| Key 模式 | 用途 | TTL |
|------|------|:---:|
| `product:{id}` | 商品详情缓存 | 300s |
| `product:stock:{id}` | 商品库存缓存（秒杀核心） | 永久（商品下架/删除时主动清除） |
| `order:{id}` | 订单缓存 | 300s |
| `user:order:{userId}` | 用户购买记录（Lua Set 结构） | 300s |
| `rate_limit:*` | 限流计数器 | 限流窗口时长 |

**降级行为：** `MessageProducer` 标注 `@Autowired(required = false)` + `@ConditionalOnClass(RabbitTemplate.class)`——若 RabbitMQ 不可用，下单流程不会阻塞，仅跳过消息发送，日志中不会有报错。但后续异步处理（库存同步、超时取消）也无法执行。

### 5.4 边界场景与容错

| 场景 | 当前行为 | 影响评估 |
|------|----------|----------|
| **商品被删除后的订单查询** | `goodsName` 返回 `"商品已删除"` 字符串 | ✅ 已合理处理 |
| **库存缓存与 DB 不一致** | Redis 缓存优先；热点数据以 Redis 为准，DB 为兜底。`product:stock:{id}` 永久缓存，仅在增/删/改商品时更新或清除。定时任务（60s）会将过期商品批量下架并清除缓存 | ⚠ 极端情况下 Redis 和 DB 可能短暂不一致（如 Redis 扣减成功但 DB 回滚失败——但实际上 Redis 回滚是同步执行的，出问题概率很低） |
| **Token 过期时间不一致** | JWT 有效期 12h，但 Redis Token TTL 仅 1h。实际生效的是 Redis TTL——1 小时后请求被拒（401） | ⚠ 前端需在 1 小时内刷新 Token，但当前无 refresh 接口 |
| **重复下单** | DB 层 `uk_user_goods (user_id, goods_id)` 唯一约束 + 业务层预先查询 | ✅ 双层防护 |
| **开始/结束时间校验** | 添加/更新商品时校验 `startTime < endTime`；下单时校验当前时间在活动窗口内 | ✅ 已覆盖 |
| **过期商品清理** | 定时任务每 60s 将 `endTime < now` 的商品状态批量设为 3（已下架）并清除库存缓存 | ⚠ 定时任务有 60s 延迟，过期商品的库存缓存可能在下架前还被查询到 |
| **并发取消与退款** | `cancelOrder` 无乐观锁保护，仅判断 `status == 0`。若两个取消请求同时到达，理论上有并发风险（但当前 Web 流程不会触发取消，实际风险极低） | ⚠ 低风险 |
| **限流组件异常** | `RateLimitAspect.checkRateLimit` 在 Redis 操作抛异常时**放行请求**（返回 true），避免限流组件故障导致全站不可用 | ✅ 已做降级 |

---

> 文档完结。共 16 个接口 + 4 项非功能性说明。
