package com.fly.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 校验标注了 {@link RequiresAuth} 的接口的 Bearer 令牌。
 * 错误行为与 FastAPI HTTPBearer + decode_token 一致：
 * 未携带/格式错误 → 403 {"detail":"Not authenticated"}；令牌无效或过期 → 401 {"detail":"无效的令牌"}。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_ATTR = "fly.auth";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean requiredAdmin = method.hasMethodAnnotation(RequiresAuth.class)
                || method.getBeanType().isAnnotationPresent(RequiresAuth.class);
        if (!requiredAdmin) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Not authenticated");
            return false;
        }
        Claims claims;
        try {
            claims = tokenService.parse(header.substring(7));
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "无效的令牌");
            return false;
        }
        Map<String, Object> payload = tokenService.toMap(claims);

        // 管理端：仅管理员令牌（admin=true）可通过
        if (!Boolean.TRUE.equals(payload.get("admin"))) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "需要管理员权限");
            return false;
        }
        request.setAttribute(AUTH_ATTR, payload);
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String detail) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("detail", detail)));
    }
}
