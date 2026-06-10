package com.mvp.security.filter;

import com.mvp.common.utils.JwtUtil;
import com.mvp.common.utils.ThreadLocalUtil;
import com.mvp.security.config.SecurityWhitePaths;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor // 推荐使用构造器注入代替 @Autowired
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        String requestUri = request.getRequestURI();
        log.debug("进入JWT过滤器，请求路径：{}", requestUri);

        // 1. 白名单路径直接放行
        if (isWhiteListPath(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 获取并校验 Token 格式
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // 交给后续的 Spring Security 过滤器处理 401
            return;
        }
        String token = authHeader.substring(7);

        try {
            // 3. 解析 Token
            Map<String, Object> claims = JwtUtil.parseToken(token);
            log.debug("JWT解析成功: {}", claims);

            // ⚠️ 关键修复：与你 GenerateTestTokens 中的 key 保持一致！
            String username = (String) claims.get("username");
            // 兼容 Integer 和 Long 类型，防止 ClassCastException
            Object userIdObj = claims.get("id");  // ✅ 修改为 "id"，与测试数据一致
            Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;

            if (username == null || userId == null) {
                log.warn("Token 缺少必要字段 (username/id)");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // ✅ 返回 401
                return;
            }

            // 4. 校验 Redis 中 Token 是否有效（可选，取决于你的登出/单点登录策略）
            String redisToken = stringRedisTemplate.opsForValue().get(token);
            if (redisToken == null) {
                log.warn("Redis 中未找到该 Token，可能已过期或被踢出: {}", username);
                filterChain.doFilter(request, response); // 不要直接 return 401，交给 Security 链处理
                return;
            }

            // 5. 构建 Authentication 并设置到 SecurityContext
            UserDetails userDetails = User.withUsername(username)
                    .password("")
                    .authorities("ROLE_USER") // 建议从 claims.get("role") 动态获取
                    .build();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // ✅ 核心：设置认证信息
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 6. 存入 ThreadLocal 供业务层使用
            ThreadLocalUtil.set(claims);

            log.info("✅ JWT认证成功，用户: {}, ID: {}", username, userId);

        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT 认证异常", e);
        }

        // ✅ 无论成功失败，都继续执行过滤器链
        // 如果上面没有 setAuthentication，后续的 AuthorizationFilter 自然会返回 401/403
        try {
            filterChain.doFilter(request, response);
        } finally {
            ThreadLocalUtil.remove();
        }
    }

    private boolean isWhiteListPath(String requestUri) {
        for (String pattern : SecurityWhitePaths.WHITE_LIST) {
            if (requestUri.equals(pattern) || requestUri.startsWith(pattern + "/")) {
                return true;
            }
        }
        return false;
    }
}
//@Slf4j
//@Component
//public class JwtAuthenticationFilter extends OncePerRequestFilter {
//
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain) throws java.io.IOException, jakarta.servlet.ServletException {
//
//        String requestUri = request.getRequestURI();
//        log.info("进入JWT过滤器，请求路径：{}", requestUri);
//
//        // 白名单路径直接放行
//        if (isWhiteListPath(requestUri)) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        // 获取 token
//        String token = request.getHeader("Authorization");
//        if (token == null || !token.startsWith("Bearer ")) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }
//        token = token.substring(7);
//
//        try {
//            // 解析 token
//            Map<String, Object> claims = JwtUtil.parseToken(token);
//            System.out.println("解析成功："+claims);
//
//            String username = (String) claims.get("username");
//            Long userId = (Long) claims.get("id");
//
//            // 校验 Redis token 是否存在
//            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
//            String redisToken = operations.get(token);
//            if (redisToken == null) {
//                log.warn("Redis 中未找到该 Token，可能已过期或被踢出: {}", username);
//                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                return;
//            }
//
//
//            // 给 Spring Security 授权
//            UserDetails userDetails = User.withUsername(username)
//                    .password("")
//                    .authorities("ROLE_USER")
//                    .build();
//
//            UsernamePasswordAuthenticationToken authentication =
//                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
//            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
//            SecurityContextHolder.getContext().setAuthentication(authentication);
//
//            // 存入当前用户信息
//            ThreadLocalUtil.set(claims);
//            filterChain.doFilter(request, response);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//        } finally {
//            ThreadLocalUtil.remove();
////            SecurityContextHolder.clearContext();
//        }
//    }
//
//    private boolean isWhiteListPath(String requestUri) {
//        for (String pattern : SecurityWhitePaths.WHITE_LIST) {
//            if (requestUri.equals(pattern) || requestUri.startsWith(pattern + "/")) {
//                return true;
//            }
//        }
//        return false;
//    }
//}