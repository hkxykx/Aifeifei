package com.fly.service;

import com.fly.common.ApiError;
import com.fly.config.FlyProperties;
import com.fly.dto.Requests.LoginReq;
import com.fly.entity.User;
import com.fly.repository.UserRepository;
import com.fly.security.AuthInterceptor;
import com.fly.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端认证（对应原 app/api/auth.py）。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final FlyProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, TokenService tokenService,
                       FlyProperties properties) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public Map<String, Object> login(LoginReq req) {
        if (req == null || req.username == null || req.password == null) {
            throw new ApiError(422, "username 和 password 为必填字段");
        }
        User user = userRepository.findByUsername(req.username).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password, user.hashedPassword)) {
            throw ApiError.unauthorized("用户名或密码错误");
        }

        String token = tokenService.createAdminToken(user.username, user.isAdmin);
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC)
                .plusHours(properties.getTokenExpireHours());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", token);
        data.put("refreshToken", "");
        data.put("expires", expires);
        data.put("avatar", user.avatar == null ? "" : user.avatar);
        data.put("username", user.username);
        data.put("nickname", user.nickname == null || user.nickname.isEmpty()
                ? user.username : user.nickname);
        data.put("roles", user.isAdmin ? List.of("admin") : List.of());
        data.put("permissions", user.isAdmin ? List.of("*:*:*") : List.of());

        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("message", "success");
        body.put("data", data);
        return body;
    }

    public Map<String, Object> me(HttpServletRequest request) {
        User user = currentDbUser(request);
        Map<String, Object> data = new HashMap<>();
        data.put("avatar", user.avatar == null ? "" : user.avatar);
        data.put("username", user.username);
        data.put("nickname", user.nickname == null || user.nickname.isEmpty()
                ? user.username : user.nickname);
        data.put("email", user.email == null ? "" : user.email);
        data.put("description", user.bio == null ? "" : user.bio);
        data.put("phone", "");
        data.put("roles", user.isAdmin ? List.of("admin") : List.of());
        data.put("permissions", user.isAdmin ? List.of("*:*:*") : List.of());

        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("message", "success");
        body.put("data", data);
        return body;
    }

    public Map<String, Object> updateMe(HttpServletRequest request, Map<String, Object> data) {
        User user = currentDbUser(request);
        if (data.containsKey("nickname") && data.get("nickname") != null) {
            user.nickname = String.valueOf(data.get("nickname"));
        }
        if (data.containsKey("email") && data.get("email") != null) {
            user.email = String.valueOf(data.get("email"));
        }
        if (data.containsKey("bio") || data.containsKey("description")) {
            Object bio = data.get("bio");
            Object description = data.get("description");
            String value = bio != null ? String.valueOf(bio) : "";
            if (value.isEmpty() && description != null) {
                value = String.valueOf(description);
            }
            user.bio = value == null ? "" : value;
        }
        if (data.containsKey("avatar") && data.get("avatar") != null) {
            user.avatar = String.valueOf(data.get("avatar"));
        }
        user.updatedAt = LocalDateTime.now();
        userRepository.save(user);

        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("message", "更新成功");
        return body;
    }

    private User currentDbUser(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) request.getAttribute(AuthInterceptor.AUTH_ATTR);
        String username = auth == null ? null : String.valueOf(auth.get("sub"));
        User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            throw ApiError.notFound("用户不存在");
        }
        return user;
    }
}
