package com.mvp.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 加密工具类
 * 用于用户密码加密、密码校验
 */
public class BCryptUtils {

    // 初始化 BCrypt 加密器
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 密码加密
     * @param rawPassword 明文密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * 密码校验
     * @param rawPassword 明文密码（用户输入的）
     * @param encodedPassword 数据库中存储的加密密码
     * @return 匹配返回 true，不匹配返回 false
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }
}
