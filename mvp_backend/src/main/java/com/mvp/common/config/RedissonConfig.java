package com.mvp.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

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
              .setConnectionPoolSize(32)
              .setConnectionMinimumIdleSize(16)
              .setIdleConnectionTimeout(10000)
              .setConnectTimeout(10000)
              .setTimeout(3000)
              .setRetryAttempts(3)
              .setRetryInterval(1500);

        // 添加编解码器配置（使用 JSON 序列化）
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());

        return Redisson.create(config);
    }
}