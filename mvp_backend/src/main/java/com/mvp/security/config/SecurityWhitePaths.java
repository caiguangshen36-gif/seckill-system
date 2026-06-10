package com.mvp.security.config;

public final class SecurityWhitePaths {

    // 不需要登录、不需要Token的放行路径
    public static final String[] WHITE_LIST = {
            "/user/login",
            "/user/register",
            "/product/detail",
            "/product/list",
            "/product/active",
            "/product/stock",
            "/product/query"
    };
}