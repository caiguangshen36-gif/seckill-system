package com.mvp.common.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 使用 Redisson 分布式限流器实现
 * 
 * 使用示例：
 * @RateLimit(rate = 100, rateInterval = 1000)
 * public Result<?> seckill() { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流器名称（默认使用方法名）
     */
    String name() default "";

    /**
     * 速率（单位时间内允许的请求数）
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

    enum LimitType {
        OVERALL,    // 全局限流
        PER_CLIENT  // 单客户端限流
    }
}
