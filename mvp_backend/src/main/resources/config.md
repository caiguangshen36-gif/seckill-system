# 高并发秒杀系统 - 配置文件说明

## 概述

`com.mvp.common.config` 目录下包含四个核心配置类，分别负责：
- **MybatisPlusConfig**: MyBatis-Plus 增强配置
- **RabbitMQConfig**: 消息队列配置
- **RedisConfig**: Spring Data Redis 配置
- **RedissonConfig**: Redisson 分布式客户端配置

---

## 1. MybatisPlusConfig.java

### 作用

配置 MyBatis-Plus 的核心插件，实现分页查询和乐观锁功能。

### 代码解析

```java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 添加分页插件（指定数据库类型为MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        
        // 添加乐观锁插件（通过 version 字段实现）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        
        return interceptor;
    }
}
```

### 关键组件说明

| 组件 | 作用 | 使用场景 |
|------|------|----------|
| `MybatisPlusInterceptor` | MyBatis-Plus 拦截器链 | 管理所有插件 |
| `PaginationInnerInterceptor` | 分页插件 | 自动生成分页SQL |
| `OptimisticLockerInnerInterceptor` | 乐观锁插件 | 并发更新时防止数据覆盖 |

### 使用示例

```java
// 分页查询
Page<User> page = new Page<>(1, 10);
IPage<User> userPage = userMapper.selectPage(page, null);

// 乐观锁（实体类需有 @Version 注解的字段）
user.setVersion(user.getVersion() + 1);
userMapper.updateById(user);
```

---

## 2. RabbitMQConfig.java

### 作用

配置 RabbitMQ 消息队列，定义队列、交换机、绑定关系及消息转换器。

### 代码解析

#### 2.1 队列与交换机定义

```java
@Configuration
public class RabbitMQConfig {

    // ==================== 队列名称常量 ====================
    
    /** 订单创建队列 - 存储订单创建消息 */
    public static final String ORDER_CREATE_QUEUE = "order.create.queue";
    
    /** 库存更新队列 - 存储库存更新消息 */
    public static final String STOCK_UPDATE_QUEUE = "stock.update.queue";
    
    /** 订单取消队列 - 存储订单取消消息 */
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    
    /** 死信队列 - 处理超时未支付订单 */
    public static final String ORDER_DEAD_LETTER_QUEUE = "order.dead.letter.queue";

    // ==================== 交换机名称常量 ====================
    
    /** 订单交换机 - 路由订单相关消息 */
    public static final String ORDER_EXCHANGE = "order.exchange";
    
    /** 库存交换机 - 路由库存相关消息 */
    public static final String STOCK_EXCHANGE = "stock.exchange";
    
    /** 死信交换机 - 路由死信消息 */
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    // ==================== 路由键常量 ====================
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";
    public static final String STOCK_UPDATE_ROUTING_KEY = "stock.update";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";
    public static final String ORDER_DEAD_LETTER_ROUTING_KEY = "order.dead.letter";
}
```

#### 2.2 核心队列配置

```java
/**
 * 订单创建队列（带死信队列配置）
 * 特点：消息10分钟超时后自动转入死信队列
 */
@Bean
public Queue orderCreateQueue() {
    return QueueBuilder.durable(ORDER_CREATE_QUEUE)
            // 指定死信交换机
            .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
            // 指定死信路由键
            .withArgument("x-dead-letter-routing-key", ORDER_DEAD_LETTER_ROUTING_KEY)
            // 消息超时时间（10分钟 = 600000毫秒）
            .withArgument("x-message-ttl", 600000)
            .build();
}

/**
 * 死信队列（处理超时未支付订单）
 */
@Bean
public Queue orderDeadLetterQueue() {
    return QueueBuilder.durable(ORDER_DEAD_LETTER_QUEUE).build();
}
```

#### 2.3 交换机与绑定配置

```java
// 创建直连交换机
@Bean
public DirectExchange orderExchange() {
    return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
}

// 绑定队列到交换机
@Bean
public Binding orderCreateBinding(Queue orderCreateQueue, DirectExchange orderExchange) {
    return BindingBuilder.bind(orderCreateQueue).to(orderExchange).with(ORDER_CREATE_ROUTING_KEY);
}
```

#### 2.4 消息转换器

```java
/**
 * JSON消息转换器
 * 将Java对象序列化为JSON格式传输
 */
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

### 消息流转图

```
生产者发送消息
        │
        ▼
    交换机(Exchange)
        │
   ┌────┴────┐
   ▼         ▼
队列1      队列2
   │         │
   ▼         ▼
消费者1    消费者2

死信机制：
订单创建队列(10分钟超时)
        │ 超时
        ▼
   死信交换机
        │
        ▼
   死信队列
        │
        ▼
   超时订单处理器
```

---

## 3. RedisConfig.java

### 作用

配置 Spring Data Redis 的 `RedisTemplate`，定义序列化方式。

### 代码解析

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        // 设置连接工厂
        template.setConnectionFactory(connectionFactory);

        // 配置 ObjectMapper（JSON序列化器）
        ObjectMapper mapper = new ObjectMapper();
        // 设置所有属性可见
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 启用多态类型支持
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), 
                                    ObjectMapper.DefaultTyping.NON_FINAL);
        // 注册Java 8时间模块
        mapper.registerModule(new JavaTimeModule());
        // 禁用日期时间戳格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 创建序列化器
        Jackson2JsonRedisSerializer<Object> serializer = 
            new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 设置序列化器
        template.setKeySerializer(stringSerializer);      // Key使用String序列化
        template.setValueSerializer(serializer);          // Value使用JSON序列化
        template.setHashKeySerializer(stringSerializer);  // Hash Key使用String序列化
        template.setHashValueSerializer(serializer);      // Hash Value使用JSON序列化
        
        template.afterPropertiesSet();
        return template;
    }
}
```

### 序列化配置说明

| 序列化器 | 用途 | 特点 |
|----------|------|------|
| `StringRedisSerializer` | Key/HashKey | 纯字符串，高效 |
| `Jackson2JsonRedisSerializer` | Value/HashValue | JSON格式，支持复杂对象 |

---

## 4. RedissonConfig.java

### 作用

配置 Redisson 客户端，提供分布式锁、原子操作等高级 Redis 功能。

### 代码解析

```java
@Configuration
public class RedissonConfig {

    // 从配置文件读取Redis连接信息
    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // 单节点模式配置
        config.useSingleServer()
              .setAddress("redis://" + redisHost + ":" + redisPort)
              .setPassword(redisPassword.isEmpty() ? null : redisPassword)
              .setConnectionPoolSize(32)           // 连接池大小
              .setConnectionMinimumIdleSize(16)    // 最小空闲连接数
              .setIdleConnectionTimeout(10000)     // 空闲连接超时时间(ms)
              .setConnectTimeout(10000)            // 连接超时时间(ms)
              .setTimeout(3000)                    // 命令执行超时时间(ms)
              .setRetryAttempts(3)                 // 重试次数
              .setRetryInterval(1500);             // 重试间隔(ms)

        // 设置JSON编解码器
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());

        return Redisson.create(config);
    }
}
```

### Redisson 核心功能

| 功能 | 说明 | 使用场景 |
|------|------|----------|
| **分布式锁** | `RLock` 可重入锁 | 秒杀场景防超卖 |
| **信号量** | `RSemaphore` | 限流控制 |
| **Lua脚本** | 原子操作 | 库存扣减 |
| **分布式集合** | `RMap`, `RList` 等 | 共享数据结构 |

### 连接池配置说明

| 参数 | 值 | 说明 |
|------|-----|------|
| `connectionPoolSize` | 32 | 最大连接数 |
| `connectionMinimumIdleSize` | 16 | 最小空闲连接数 |
| `idleConnectionTimeout` | 10000ms | 空闲连接回收时间 |
| `connectTimeout` | 10000ms | 连接超时时间 |
| `timeout` | 3000ms | 命令执行超时时间 |
| `retryAttempts` | 3 | 重试次数 |
| retryInterval | 1500ms | 重试间隔 |

---

## 5. RedissonCacheService.java

### 作用

`RedissonCacheService` 是基于 Redisson 客户端封装的缓存服务类，提供以下核心功能：

1. **基础缓存操作**：get/set/delete
2. **商品缓存管理**：商品信息、库存缓存
3. **订单缓存管理**：订单信息缓存
4. **Lua脚本执行**：原子性库存扣减/恢复操作
5. **分布式锁**：可重入锁、公平锁、红锁
6. **限流控制**：信号量、限流器

### 核心设计

#### 5.1 缓存键前缀设计

```java
private static final String PRODUCT_KEY_PREFIX = "product:";        // 商品信息前缀
private static final String ORDER_KEY_PREFIX = "order:";            // 订单信息前缀
private static final String PRODUCT_STOCK_KEY_PREFIX = "product:stock:"; // 库存前缀
private static final String USER_ORDER_PREFIX = "user:order:";     // 用户购买记录前缀
private static final long DEFAULT_CACHE_TIME = 300;                // 默认缓存时间(秒)
```

| 前缀 | 完整键示例 | 存储内容 |
|------|-----------|----------|
| `product:` | `product:1` | 商品完整信息(JSON) |
| `order:` | `order:1001` | 订单完整信息(JSON) |
| `product:stock:` | `product:stock:1` | 商品库存数量(Integer) |
| `user:order:` | `user:order:100` | 用户已购商品ID列表 |

#### 5.2 Lua脚本预加载机制

```java
// 脚本对象缓存（性能优化）
private RedisScript<Long> decrementStockScript;
private RedisScript<Long> incrementStockScript;
private RedisScript<Long> checkAndDecrementScript;
private RedisScript<Long> checkUserPurchaseScript;
private RedisScript<Long> addUserPurchaseScript;

@PostConstruct
public void init() {
    // 从 resources/lua 目录加载脚本
    decrementStockScript = loadScript("lua/decrement_stock.lua");
    incrementStockScript = loadScript("lua/increment_stock.lua");
    checkAndDecrementScript = loadScript("lua/check_and_decrement.lua");
    checkUserPurchaseScript = loadScript("lua/check_user_purchase.lua");
    addUserPurchaseScript = loadScript("lua/add_user_purchase.lua");
    log.info("Lua脚本加载完成");
}
```

**设计优势**：
- ✅ 脚本在启动时一次性加载，避免运行时重复读取文件
- ✅ 脚本对象缓存，提高执行效率
- ✅ 脚本与代码分离，便于维护和版本管理

#### 5.3 原子性库存操作

##### 扣减库存（Lua脚本）

```java
public boolean decrementStock(Long productId) {
    String key = PRODUCT_STOCK_KEY_PREFIX + productId;
    Long result = executeLoadedScript(decrementStockScript,
            Collections.singletonList(key));
    if (result != null && result >= 0) {
        log.info("[库存扣减] Lua脚本执行成功 | 商品ID: {}, 剩余库存: {}", productId, result);
        return true;
    }
    log.warn("[库存扣减] Lua脚本执行失败 | 商品ID: {}, 结果: {}", productId, result);
    return false;
}
```

##### 双重检查扣减（防超卖）

```java
/**
 * 检查并扣减库存（双重检查，防超卖）
 * @param expectedStock 期望库存值（乐观锁校验，可为null）
 * @return 1=成功, 0=库存不足, -1=key不存在, -2=并发冲突
 */
public int checkAndDecrementStock(Long productId, Integer expectedStock) {
    String key = PRODUCT_STOCK_KEY_PREFIX + productId;
    Long result = executeLoadedScript(checkAndDecrementScript,
            Collections.singletonList(key), expectedStock);
    return result != null ? result.intValue() : -1;
}
```

#### 5.4 用户购买记录管理

```java
/**
 * 检查用户是否已购买该商品
 */
public boolean checkUserPurchase(Long userId, Long productId) {
    String userKey = USER_ORDER_PREFIX + userId;
    Long result = executeLoadedScript(checkUserPurchaseScript,
            Collections.singletonList(userKey), productId);
    return result != null && result == 1;
}

/**
 * 添加用户购买记录（防止重复购买）
 */
public boolean addUserPurchase(Long userId, Long productId) {
    String userKey = USER_ORDER_PREFIX + userId;
    Long result = executeLoadedScript(addUserPurchaseScript,
            Collections.singletonList(userKey), productId);
    boolean success = result != null && result == 1;
    if (success) {
        redissonClient.getBucket(userKey).expire(DEFAULT_CACHE_TIME, TimeUnit.SECONDS);
    }
    return success;
}
```

#### 5.5 分布式锁服务

```java
/**
 * 获取分布式可重入锁
 */
public RLock getLock(String lockKey) {
    return redissonClient.getLock(lockKey);
}

/**
 * 获取分布式公平锁（按请求顺序获取锁）
 */
public RLock getFairLock(String lockKey) {
    return redissonClient.getFairLock(lockKey);
}

/**
 * 获取红锁（多节点分布式锁，需多个Redis节点）
 */
public RLock getRedLock(String... lockKeys) {
    RLock[] locks = new RLock[lockKeys.length];
    for (int i = 0; i < lockKeys.length; i++) {
        locks[i] = redissonClient.getLock(lockKeys[i]);
    }
    return new RedissonRedLock(locks);
}
```

#### 5.6 限流控制服务

```java
/**
 * 获取分布式信号量（控制并发数量）
 */
public RSemaphore getSemaphore(String name, int permits) {
    RSemaphore semaphore = redissonClient.getSemaphore(name);
    semaphore.trySetPermits(permits);
    return semaphore;
}

/**
 * 获取分布式限流器（控制访问速率）
 */
public RRateLimiter getRateLimiter(String name, long rate, long rateInterval) {
    RRateLimiter rateLimiter = redissonClient.getRateLimiter(name);
    rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, RateIntervalUnit.MILLISECONDS);
    return rateLimiter;
}
```

### 核心方法速查表

| 方法名 | 功能 | 返回值 |
|--------|------|--------|
| `setProduct()` | 设置商品缓存 | void |
| `getProduct()` | 获取商品缓存 | Object |
| `deleteProduct()` | 删除商品缓存 | void |
| `setProductStock()` | 设置库存缓存 | void |
| `getProductStock()` | 获取库存缓存 | Integer |
| `decrementStock()` | 扣减库存（原子） | boolean |
| `incrementStock()` | 恢复库存（原子） | boolean |
| `checkAndDecrementStock()` | 检查并扣减（防超卖） | int |
| `setOrder()` | 设置订单缓存 | void |
| `getOrder()` | 获取订单缓存 | Object |
| `deleteOrder()` | 删除订单缓存 | void |
| `checkUserPurchase()` | 检查用户是否已购买 | boolean |
| `addUserPurchase()` | 添加用户购买记录 | boolean |
| `getLock()` | 获取分布式锁 | RLock |
| `getFairLock()` | 获取公平锁 | RLock |
| `getRedLock()` | 获取红锁 | RLock |
| `getSemaphore()` | 获取信号量 | RSemaphore |
| `getRateLimiter()` | 获取限流器 | RRateLimiter |

### 秒杀场景使用流程

```
用户发起秒杀请求
        │
        ▼
┌─────────────────┐
│ 1.检查商品缓存  │ ── 缓存命中 → 直接返回
└────────┬────────┘
         │ 缓存未命中
         ▼
┌─────────────────┐
│ 2.从DB加载商品  │
└────────┬────────┘
         ▼
┌─────────────────┐
│ 3.检查秒杀时间  │ ── 未开始/已结束 → 返回错误
└────────┬────────┘
         │ 在秒杀时间内
         ▼
┌─────────────────┐
│ 4.Lua脚本原子扣减│ ── 库存不足 → 返回错误
│   库存 + 检查   │
│   用户购买记录  │
└────────┬────────┘
         │ 扣减成功
         ▼
┌─────────────────┐
│ 5.创建订单      │
└────────┬────────┘
         ▼
┌─────────────────┐
│ 6.发送MQ消息    │ ── 异步处理后续逻辑
└─────────────────┘
```

### Lua脚本文件位置

所有 Lua 脚本存放在 `resources/lua/` 目录下：

| 脚本文件 | 功能 |
|----------|------|
| `decrement_stock.lua` | 扣减库存 |
| `increment_stock.lua` | 恢复库存 |
| `check_and_decrement.lua` | 检查并扣减（防超卖） |
| `check_user_purchase.lua` | 检查用户购买记录 |
| `add_user_purchase.lua` | 添加用户购买记录 |

---

## 配置文件关系图

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot 应用                         │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   RedisConfig   │  │  RedissonConfig │  │   RabbitMQConfig│
│  (Spring Data)  │  │  (Redisson)     │  │  (消息队列)     │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────┐
│                    Redis Server                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  缓存数据   │  │  Lua脚本    │  │  分布式锁   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────┐
│                    RabbitMQ Server                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ 订单队列    │  │ 库存队列    │  │ 死信队列    │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## 配置优先级

配置文件读取顺序（优先级从高到低）：

1. 命令行参数（如 `--spring.redis.host=192.168.1.100`）
2. `application.yml` / `application.properties`
3. `application-{profile}.yml`（指定profile时）
4. 默认值（代码中 `@Value` 的默认值）

---

## 总结

| 配置类 | 技术组件 | 核心功能 |
|--------|----------|----------|
| `MybatisPlusConfig` | MyBatis-Plus | 分页、乐观锁 |
| `RabbitMQConfig` | RabbitMQ | 消息队列、解耦、异步处理 |
| `RedisConfig` | Spring Data Redis | 基础缓存操作 |
| `RedissonConfig` | Redisson | 分布式锁、Lua脚本、高级特性 |

这四个配置类共同支撑了高并发秒杀系统的核心基础设施，实现了缓存、消息队列、分布式锁等关键功能。

---

## 6. RateLimit.java（限流注解）

### 作用

`@RateLimit` 是自定义限流注解，用于标记需要限流的接口方法。配合 `RateLimitAspect` 切面实现接口限流控制。

### 代码解析

```java
package com.mvp.common.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 使用 Redis INCR 实现分布式限流
 * 
 * 使用示例：
 * @RateLimit(rate = 100, rateInterval = 1000)
 * public Result<?> seckill() { ... }
 */
@Target(ElementType.METHOD)      // 作用于方法
@Retention(RetentionPolicy.RUNTIME) // 运行时保留
@Documented                       // 生成文档
public @interface RateLimit {

    /**
     * 限流器名称（默认使用方法名）
     */
    String name() default "";

    /**
     * 速率（单位时间内允许的请求数）
     * 默认每秒100个请求
     */
    long rate() default 100;

    /**
     * 速率间隔时间（毫秒）
     * 默认 1000ms = 1秒
     */
    long rateInterval() default 1000;

    /**
     * 限流类型
     * OVERALL: 全局限流（所有用户共享限流配额）
     * PER_CLIENT: 单客户端限流（每个用户独立限流配额）
     */
    LimitType limitType() default LimitType.OVERALL;

    /**
     * 限流失败提示消息
     */
    String message() default "系统繁忙，请稍后重试";

    /**
     * 限流类型枚举
     */
    enum LimitType {
        OVERALL,    // 全局限流
        PER_CLIENT  // 单客户端限流
    }
}
```

### 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | 方法名 | 限流器唯一标识 |
| `rate` | long | 100 | 单位时间允许请求数 |
| `rateInterval` | long | 1000 | 时间窗口（毫秒） |
| `limitType` | LimitType | OVERALL | 全局/单客户端限流 |
| `message` | String | "系统繁忙..." | 限流提示消息 |

### 使用示例

```java
// 秒杀接口：每秒最多100个请求
@RateLimit(rate = 100, rateInterval = 1000, message = "秒杀人数过多，请稍后重试")
@PostMapping
public Result<OrderVo> createOrder(@RequestBody OrderDto dto) {
    // 业务逻辑
}

// 商品详情：每秒最多500个请求
@RateLimit(rate = 500, rateInterval = 1000, name = "product_detail")
@GetMapping("/detail")
public Result<ProductVo> getProductById(@RequestParam Long id) {
    // 业务逻辑
}

// 单客户端限流：每个用户每秒最多10个请求
@RateLimit(rate = 10, rateInterval = 1000, limitType = LimitType.PER_CLIENT)
@PostMapping("/query")
public Result<?> query() {
    // 业务逻辑
}
```

---

## 7. RateLimitAspect.java（限流切面）

### 作用

`RateLimitAspect` 是限流切面类，拦截带有 `@RateLimit` 注解的方法，执行限流检查逻辑。

### 代码解析

```java
package com.mvp.common.aspect;

import com.mvp.common.annotation.RateLimit;
import com.mvp.common.exption.BusinessException;
import com.mvp.common.utils.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 基于 Redis INCR 实现分布式限流
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 环绕通知：拦截 @RateLimit 注解的方法
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        // 构建限流 key
        String limitKey = buildLimitKey(rateLimit, className, methodName);

        // 执行限流检查
        boolean allowed = checkRateLimit(limitKey, rateLimit);

        if (!allowed) {
            log.warn("[限流拦截] 接口: {}.{}, Key: {}, 速率: {}/{}ms",
                    className, methodName, limitKey, rateLimit.rate(), rateLimit.rateInterval());
            throw new BusinessException(rateLimit.message());
        }

        log.debug("[限流通过] 接口: {}.{}", className, methodName);
        return point.proceed();
    }

    /**
     * 构建限流 key
     */
    private String buildLimitKey(RateLimit rateLimit, String className, String methodName) {
        String baseKey = "rate_limit:";

        // 使用自定义名称或类名+方法名
        if (!rateLimit.name().isEmpty()) {
            baseKey += rateLimit.name();
        } else {
            baseKey += className + ":" + methodName;
        }

        // 单客户端限流时，添加用户ID
        if (rateLimit.limitType() == RateLimit.LimitType.PER_CLIENT) {
            Long userId = ThreadLocalUtil.getUserId();
            if (userId != null) {
                baseKey += ":" + userId;
            }
        }

        return baseKey;
    }

    /**
     * 检查限流（使用 Redis INCR 实现）
     */
    private boolean checkRateLimit(String key, RateLimit rateLimit) {
        long rate = rateLimit.rate();
        long interval = rateLimit.rateInterval();

        try {
            // 获取当前计数
            String countStr = redisTemplate.opsForValue().get(key);
            long currentCount = countStr != null ? Long.parseLong(countStr) : 0;

            // 未超过限流阈值
            if (currentCount < rate) {
                // 计数 +1
                Long newCount = redisTemplate.opsForValue().increment(key);

                // 第一次访问时设置过期时间
                if (newCount != null && newCount == 1) {
                    redisTemplate.expire(key, interval, TimeUnit.MILLISECONDS);
                }

                return true;
            }

            // 超过限流阈值
            log.warn("[限流拒绝] Key: {}, 当前: {}, 阈值: {}", key, currentCount, rate);
            return false;

        } catch (Exception e) {
            log.error("[限流异常] Key: {}, error: {}", key, e.getMessage());
            // 异常时放行，避免影响业务
            return true;
        }
    }
}
```

### 限流原理

```
请求到达
    │
    ▼
┌─────────────────┐
│ AOP切面拦截     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 构建限流Key     │
│ rate_limit:xxx  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Redis GET计数   │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
 未超限      已超限
    │         │
    ▼         ▼
┌─────────┐ ┌─────────┐
│ INCR +1 │ │ 返回错误 │
│ 设置TTL │ │ 提示消息 │
└────┬────┘ └─────────┘
     │
     ▼
┌─────────────────┐
│ 执行业务方法    │
└─────────────────┘
```

### Redis Key 设计

| 限流类型 | Key 格式 | 示例 |
|----------|----------|------|
| 全局限流 | `rate_limit:{name}` | `rate_limit:product_detail` |
| 全局限流（默认） | `rate_limit:{class}:{method}` | `rate_limit:OrderController:createOrder` |
| 单客户端限流 | `rate_limit:{name}:{userId}` | `rate_limit:product_query:1001` |

### 异常处理策略

```java
// 限流异常时放行，避免影响业务
catch (Exception e) {
    log.error("[限流异常] Key: {}, error: {}", key, e.getMessage());
    return true;  // 放行
}
```

**设计原因**：
- Redis 连接异常时不应阻塞业务
- 限流是保护机制，而非核心功能
- 可通过日志监控异常情况

---

## 8. 项目接口限流配置汇总

### 秒杀接口限流

| 接口 | 路径 | 限流配置 | 说明 |
|------|------|----------|------|
| 秒杀下单 | `POST /order` | 100次/秒 | 核心秒杀接口，防止流量洪峰 |

### 商品接口限流

| 接口 | 路径 | 限流配置 | 说明 |
|------|------|----------|------|
| 添加商品 | `POST /product/add` | 10次/秒 | 管理接口，防恶意刷 |
| 更新商品 | `POST /product/update` | 10次/秒 | 管理接口 |
| 删除商品 | `POST /product/delete` | 10次/秒 | 管理接口 |
| 商品详情 | `GET /product/detail` | 500次/秒 | 高频查询接口 |
| 商品列表 | `POST /product/list` | 200次/秒 | 分页查询 |
| 秒杀商品 | `POST /product/active` | 300次/秒 | 秒杀商品列表 |
| 库存查询 | `GET /product/stock` | 500次/秒 | 高频查询 |
| 条件查询 | `POST /product/query` | 200次/秒 | 复合查询 |

---

## 9. 限流 vs Redisson RRateLimiter

### 对比分析

| 方案 | 实现方式 | 优点 | 缺点 |
|------|----------|------|------|
| **Redis INCR**（当前） | `redisTemplate.increment()` | 简单轻量、无额外依赖 | 功能相对简单 |
| **Redisson RRateLimiter** | `redissonClient.getRateLimiter()` | 功能强大、支持复杂策略 | 初始化开销较大 |

### Redisson RRateLimiter 方式（可选）

如果需要更强大的限流功能，可使用 `RedissonCacheService` 中已有的方法：

```java
// RedissonCacheService.java 中已存在
public RRateLimiter getRateLimiter(String name, long rate, long rateInterval) {
    RRateLimiter rateLimiter = redissonClient.getRateLimiter(name);
    rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, RateIntervalUnit.MILLISECONDS);
    return rateLimiter;
}

// 使用方式
RRateLimiter limiter = cacheService.getRateLimiter("seckill", 100, 1000);
if (!limiter.tryAcquire()) {
    throw new BusinessException("系统繁忙");
}
```

---

## 总结（更新）

| 配置类/组件 | 技术组件 | 核心功能 |
|-------------|----------|----------|
| `MybatisPlusConfig` | MyBatis-Plus | 分页、乐观锁 |
| `RabbitMQConfig` | RabbitMQ | 消息队列、解耦、异步处理 |
| `RedisConfig` | Spring Data Redis | 基础缓存操作 |
| `RedissonConfig` | Redisson | 分布式锁、Lua脚本、高级特性 |
| `RedissonCacheService` | Redisson封装 | 缓存服务、Lua执行、分布式锁 |
| **`RateLimit`** | 自定义注解 | 接口限流标记 |
| **`RateLimitAspect`** | AOP切面 | 限流逻辑执行 |

这些配置类和组件共同支撑了高并发秒杀系统的核心基础设施，实现了缓存、消息队列、分布式锁、**接口限流**等关键功能。
