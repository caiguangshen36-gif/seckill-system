package com.mvp.common.aspect;

import com.mvp.common.annotation.RateLimit;
import com.mvp.common.exption.BusinessException;
import com.mvp.common.utils.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 基于 Redisson 分布式限流器实现接口限流
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 限流器缓存（避免每次请求都创建限流器）
     */
    private final ConcurrentHashMap<String, RRateLimiter> limiterCache = new ConcurrentHashMap<>();

    /**
     * 简易限流实现（使用 Redis INCR）
     * 比 Redisson RRateLimiter 更轻量，适合简单场景
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
     * 简易版：在时间窗口内计数，超过阈值则拒绝
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

                log.debug("[限流计数] Key: {}, 当前: {}, 阈值: {}", key, newCount, rate);
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