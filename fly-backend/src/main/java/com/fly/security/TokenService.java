package com.fly.security;

import com.fly.config.FlyProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与校验（HS256，72 小时有效期，与原 python-jose 实现保持一致）。
 */
@Service
public class TokenService {

    private final FlyProperties properties;
    private SecretKey key;

    public TokenService(FlyProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "缺少必需的环境变量 FLY_SECRET_KEY（JWT 签名密钥）");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FLY_SECRET_KEY 长度至少 32 字符");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 管理端登录令牌：{sub: username, admin: bool, exp: +72h} */
    public String createAdminToken(String username, boolean isAdmin) {
        LocalDateTime expire = LocalDateTime.now(ZoneOffset.UTC)
                .plusHours(properties.getTokenExpireHours());
        return Jwts.builder()
                .subject(username)
                .claim("admin", isAdmin)
                .expiration(Date.from(expire.toInstant(ZoneOffset.UTC)))
                .issuedAt(new Date())
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌，失败抛出 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    /** 令牌中的全部声明（供 AuthInterceptor 存入请求属性） */
    public Map<String, Object> toMap(Claims claims) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> e : claims.entrySet()) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }

    /** 可选解析：任何异常都返回 null（对应原 get_github_user_optional 行为） */
    public Claims parseQuietly(String token) {
        try {
            return parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
