package com.mvp.test;

import com.mvp.common.utils.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 生成测试用户Token（用于JMeter压测）
 * 运行一次生成所有需要的测试数据
 */
//测试的时候再可开启这个文件

//@Slf4j
//@Component
//public class TestDataInitializer {
//
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//
//    @PostConstruct // 应用启动后自动执行
//    public void init() {
//        List<String> tokens = new ArrayList<>();
//
//        for (int i = 1; i <= 100; i++) {
//            Map<String, Object> claims = new HashMap<>();
//            claims.put("id", 1000L + i);           //  与 JwtAuthenticationFilter 解析的 key 一致
//            claims.put("username", "testuser" + i);
//            claims.put("role", "USER");
//
//            String token = JwtUtil.genToken(claims);// 你的 JWT 生成方法
//            //  关键：写入 Redis！
//            stringRedisTemplate.opsForValue().set(token, "valid", 24, TimeUnit.HOURS);
//
//            tokens.add("testuser" + i + "," + token);
//        }
//
//        // 打印供 JMeter 使用（可选）
//        tokens.forEach(System.out::println);
//        log.info(" 已生成并写入 Redis: {} 个测试 Token", tokens.size());
//    }
//}