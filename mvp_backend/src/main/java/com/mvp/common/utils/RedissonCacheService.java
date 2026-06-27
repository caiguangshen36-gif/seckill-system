package com.mvp.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.RedissonRedLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 缓存服务
 * 提供分布式缓存、Lua脚本执行、分布式锁等功能
 * Lua脚本统一存放在 resources/lua 目录下，便于维护
 */
@Slf4j
@Component
public class RedissonCacheService {

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String ORDER_KEY_PREFIX = "order:";
    private static final String PRODUCT_STOCK_KEY_PREFIX = "product:stock:";
    private static final String USER_ORDER_PREFIX = "user:order:";
    // [Q7优化] TTL随机偏移范围：每个key在300~600秒之间随机过期，避免大量key同时失效导致缓存雪崩
    // 原先是一个硬编码的 DEFAULT_CACHE_TIME = 300，所有key同时写入→同时过期→DB瞬间压力峰值
    private static final long DEFAULT_CACHE_TIME_MIN = 300;  // 下限5分钟
    private static final long DEFAULT_CACHE_TIME_MAX = 600;  // 上限10分钟
    private static final Random TTL_RANDOM = new Random();

    /**
     * [Q7优化] 获取带随机偏移的TTL（秒）
     * 每个key的实际TTL在 [MIN, MAX] 区间随机分布
     * 原因：避免缓存雪崩——大量key在同一时刻过期会导致DB瞬间承受全部请求压力
     */
    private long getCacheTimeWithJitter() {
        return DEFAULT_CACHE_TIME_MIN + TTL_RANDOM.nextInt(
                (int) (DEFAULT_CACHE_TIME_MAX - DEFAULT_CACHE_TIME_MIN));
    }

    @Autowired
    private RedissonClient redissonClient;

    // RedisScript 缓存（性能优化：避免每次执行脚本时重新加载）
    private RedisScript<Long> decrementStockScript;
    private RedisScript<Long> incrementStockScript;
    private RedisScript<Long> checkAndDecrementScript;
    private RedisScript<Long> checkUserPurchaseScript;
    private RedisScript<Long> addUserPurchaseScript;

    /**
     * 初始化：加载所有 Lua 脚本文件
     */
    @PostConstruct
    public void init() {
        decrementStockScript = loadScript("lua/decrement_stock.lua");
        incrementStockScript = loadScript("lua/increment_stock.lua");
        checkAndDecrementScript = loadScript("lua/check_and_decrement.lua");
        checkUserPurchaseScript = loadScript("lua/check_user_purchase.lua");
        addUserPurchaseScript = loadScript("lua/add_user_purchase.lua");
        log.info("Lua脚本加载完成");
    }

    /**
     * 加载 Lua 脚本文件
     */
    private RedisScript<Long> loadScript(String scriptPath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(scriptPath)));
        return script;
    }

    /**
     * 执行 Lua 脚本（使用预加载的脚本对象，性能更优）
     */
    public Long executeLoadedScript(RedisScript<Long> script,
                                     java.util.List<Object> keys, Object... values) {
        try {
            return redissonClient.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    script.getScriptAsString(),
                    RScript.ReturnType.INTEGER,
                    keys,
                    values
            );
        } catch (Exception e) {
            log.error("执行Lua脚本失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取字符串缓存
     */
    public Object get(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        try {
            return bucket.get();
        } catch (Exception e) {
            log.error("获取缓存失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 设置字符串缓存（带过期时间）
     */
    public void set(String key, Object value, long seconds) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        try {
            bucket.set(value, seconds, TimeUnit.SECONDS);
            log.debug("设置缓存成功: key={}", key);
        } catch (Exception e) {
            log.error("设置缓存失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        redissonClient.getBucket(key).delete();
        log.debug("删除缓存: key={}", key);
    }

    /**
     * 设置商品缓存（[Q7优化] TTL带随机偏移，防雪崩）
     */
    public void setProduct(Long productId, Object product) {
        String key = PRODUCT_KEY_PREFIX + productId;
        set(key, product, getCacheTimeWithJitter());
    }

    /**
     * 获取商品缓存
     */
    public Object getProduct(Long productId) {
        String key = PRODUCT_KEY_PREFIX + productId;
        return get(key);
    }

    /**
     * 删除商品缓存
     */
    public void deleteProduct(Long productId) {
        String key = PRODUCT_KEY_PREFIX + productId;
        delete(key);
    }

    /**
     * 设置商品库存缓存（永久缓存，由商品下架/删除时主动删除）
     */
    public void setProductStock(Long productId, Integer stock) {
        String key = PRODUCT_STOCK_KEY_PREFIX + productId;
        try {
            RBucket<Integer> bucket = redissonClient.getBucket(key);
            bucket.set(stock);
            log.debug("设置商品库存缓存: productId={}, stock={}", productId, stock);
        } catch (Exception e) {
            log.error("设置商品库存缓存失败: productId={}, error={}", productId, e.getMessage());
        }
    }

    /**
     * 删除商品库存缓存
     */
    public void deleteProductStock(Long productId) {
        String key = PRODUCT_STOCK_KEY_PREFIX + productId;
        delete(key);
        log.info("删除商品库存缓存: productId={}", productId);
    }

    /**
     * 获取商品库存缓存
     */
    public Integer getProductStock(Long productId) {
        String key = PRODUCT_STOCK_KEY_PREFIX + productId;
        try {
            RBucket<Integer> bucket = redissonClient.getBucket(key);
            return bucket.get();
        } catch (Exception e) {
            log.error("获取商品库存缓存失败: productId={}, error={}", productId, e.getMessage());
            return null;
        }
    }

    /**
     * 扣减库存（Lua脚本，原子操作）
     * @return true=成功, false=失败
     */
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

    /**
     * 恢复库存（Lua脚本，原子操作）
     * @return true=成功, false=失败
     */
    public boolean incrementStock(Long productId) {
        String key = PRODUCT_STOCK_KEY_PREFIX + productId;
        Long result = executeLoadedScript(incrementStockScript,
                Collections.singletonList(key));
        if (result != null && result >= 0) {
            log.info("[库存恢复] Lua脚本执行成功 | 商品ID: {}, 当前库存: {}", productId, result);
            return true;
        }
        log.warn("[库存恢复] Lua脚本执行失败 | 商品ID: {}, 结果: {}", productId, result);
        return false;
    }

    /**
     * 检查并扣减库存（双重检查，防超卖）
     * @return 1=成功, 0=库存不足, -1=key不存在, -2=并发冲突
     */
    public int checkAndDecrementStock(Long productId) {
        return checkAndDecrementStock(productId, null);
    }

    /**
     * 检查并扣减库存（带期望库存校验）
     * @param expectedStock 期望的库存值（用于乐观锁校验，可为null）
     * @return 1=成功, 0=库存不足, -1=key不存在, -2=并发冲突
     */
    public int checkAndDecrementStock(Long productId, Integer expectedStock) {
        String key = PRODUCT_STOCK_KEY_PREFIX + productId;
        Long result = executeLoadedScript(checkAndDecrementScript,
                Collections.singletonList(key), expectedStock);
        int resultInt = result != null ? result.intValue() : -1;
        log.debug("Lua脚本检查并扣减库存: productId={}, result={}", productId, resultInt);
        return resultInt;
    }

    /**
     * 设置订单缓存（[Q7优化] TTL带随机偏移，防雪崩）
     */
    public void setOrder(Long orderId, Object order) {
        String key = ORDER_KEY_PREFIX + orderId;
        set(key, order, getCacheTimeWithJitter());
    }

    /**
     * 获取订单缓存
     */
    public Object getOrder(Long orderId) {
        String key = ORDER_KEY_PREFIX + orderId;
        return get(key);
    }

    /**
     * 删除订单缓存
     */
    public void deleteOrder(Long orderId) {
        String key = ORDER_KEY_PREFIX + orderId;
        delete(key);
    }

    /**
     * 检查用户是否已购买该商品
     * @return true=已购买, false=未购买
     */
    public boolean checkUserPurchase(Long userId, Long productId) {
        String userKey = USER_ORDER_PREFIX + userId;
        Long result = executeLoadedScript(checkUserPurchaseScript,
                Collections.singletonList(userKey), productId);
        return result != null && result == 1;
    }

    /**
     * 添加用户购买记录
     * @return true=添加成功, false=已存在
     */
    public boolean addUserPurchase(Long userId, Long productId) {
        String userKey = USER_ORDER_PREFIX + userId;
        Long result = executeLoadedScript(addUserPurchaseScript,
                Collections.singletonList(userKey), productId);
        boolean success = result != null && result == 1;
        if (success) {
            // [Q7优化] TTL带随机偏移（秒杀活动结束后失效），防雪崩
            redissonClient.getBucket(userKey).expire(getCacheTimeWithJitter(), TimeUnit.SECONDS);
        }
        return success;
    }

    /**
     * 获取分布式可重入锁
     * @param lockKey 锁的key
     * @return RLock 对象
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 获取分布式公平锁
     * @param lockKey 锁的key
     * @return RLock 对象
     */
    public RLock getFairLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    /**
     * 获取红锁（多节点分布式锁）
     * @param lockKeys 多个锁的key
     * @return RedissonRedLock 对象
     */
    public RLock getRedLock(String... lockKeys) {
        RLock[] locks = new RLock[lockKeys.length];
        for (int i = 0; i < lockKeys.length; i++) {
            locks[i] = redissonClient.getLock(lockKeys[i]);
        }
        return new RedissonRedLock(locks);
    }

    /**
     * 获取分布式信号量
     * @param name 信号量名称
     * @param permits 许可数量
     * @return RSemaphore 对象
     */
    public RSemaphore getSemaphore(String name, int permits) {
        RSemaphore semaphore = redissonClient.getSemaphore(name);
        semaphore.trySetPermits(permits);
        return semaphore;
    }

    /**
     * 获取分布式限流器
     * @param name 限流器名称
     * @param rate 速率
     * @param rateInterval 速率间隔（毫秒）
     * @return RRateLimiter 对象
     */
    public RRateLimiter getRateLimiter(String name, long rate, long rateInterval) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(name);
        rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, RateIntervalUnit.MILLISECONDS);
        return rateLimiter;
    }

    /**
     * 使商品缓存失效
     */
    public void invalidateProductCache(Long productId) {
        deleteProduct(productId);
    }
}