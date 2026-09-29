package com.fly.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 可选管理员认证（读接口用）：
 * 请求携带有效管理员令牌 → 按管理员放行（可读草稿等非公开数据）；
 * 未携带或令牌无效 → 按匿名处理（仅公开数据），不报错。
 */
@Component
public class OptionalAuth {

    private final TokenService tokenService;

    public OptionalAuth(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /** 请求是否携带有效的管理员令牌。 */
    public boolean isAdmin(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        var claims = tokenService.parseQuietly(header.substring(7));
        if (claims == null) {
            return false;
        }
        Map<String, Object> payload = tokenService.toMap(claims);
        return Boolean.TRUE.equals(payload.get("admin"));
    }
}
