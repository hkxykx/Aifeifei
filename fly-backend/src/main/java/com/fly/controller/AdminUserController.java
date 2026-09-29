package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.dto.Requests.AdminUserCreateReq;
import com.fly.dto.Requests.AdminUserUpdateReq;
import com.fly.entity.User;
import com.fly.repository.UserRepository;
import com.fly.security.AuthInterceptor;
import com.fly.security.RequiresAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理员账户管理（账户设置-账户管理）：
 * - 仅保留账户（用户名）与密码的新增/修改/删除
 * - 最多 MAX_ADMINS 个管理员，至少保留 1 个
 * - 所有管理员（is_admin=true）登录后拥有后台全量权限（*:*:*）
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiresAuth
public class AdminUserController {

    public static final int MAX_ADMINS = 3;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<Map<String, Object>> users = new ArrayList<>();
        userRepository.findAll().stream()
                .filter(u -> u.isAdmin)
                .sorted((a, b) -> Long.compare(a.id, b.id))
                .forEach(u -> users.add(Map.of("id", u.id, "username", u.username)));
        return body(users);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) AdminUserCreateReq req) {
        String username = req == null || req.username == null ? "" : req.username.trim();
        String password = req == null || req.password == null ? "" : req.password;
        if (username.isEmpty() || password.isEmpty()) {
            throw new ApiError(422, "用户名和密码为必填字段");
        }
        if (username.length() < 2 || username.length() > 50) {
            throw new ApiError(422, "用户名长度需在 2-50 之间");
        }
        if (password.length() < 6) {
            throw new ApiError(422, "密码长度至少 6 位");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiError(422, "用户名已存在");
        }
        if (userRepository.countByIsAdminTrue() >= MAX_ADMINS) {
            throw new ApiError(422, "最多支持 " + MAX_ADMINS + " 个管理员账户");
        }
        User user = new User();
        user.username = username;
        user.hashedPassword = passwordEncoder.encode(password);
        user.nickname = username;
        user.isAdmin = true;
        userRepository.save(user);
        return body(Map.of("id", user.id, "username", user.username));
    }

    @PutMapping("/{userId}")
    public Map<String, Object> update(@PathVariable Long userId,
                                      @RequestBody(required = false) AdminUserUpdateReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.notFound("账户不存在"));
        if (req == null) {
            throw new ApiError(422, "请求体为空");
        }
        if (req.username != null && !req.username.trim().isEmpty()) {
            String username = req.username.trim();
            if (username.length() < 2 || username.length() > 50) {
                throw new ApiError(422, "用户名长度需在 2-50 之间");
            }
            userRepository.findByUsername(username).ifPresent(existing -> {
                if (!existing.id.equals(user.id)) {
                    throw new ApiError(422, "用户名已存在");
                }
            });
            user.username = username;
        }
        if (req.password != null && !req.password.isEmpty()) {
            if (req.password.length() < 6) {
                throw new ApiError(422, "密码长度至少 6 位");
            }
            user.hashedPassword = passwordEncoder.encode(req.password);
        }
        userRepository.save(user);
        return body(Map.of("id", user.id, "username", user.username));
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> delete(@PathVariable Long userId, HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.notFound("账户不存在"));
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) request.getAttribute(AuthInterceptor.AUTH_ATTR);
        String current = auth == null ? null : String.valueOf(auth.get("sub"));
        if (current != null && current.equals(user.username)) {
            throw new ApiError(422, "不能删除当前登录账户");
        }
        if (user.isAdmin && userRepository.countByIsAdminTrue() <= 1) {
            throw new ApiError(422, "至少保留 1 个管理员账户");
        }
        userRepository.delete(user);
        return Map.of("code", 0, "message", "删除成功");
    }

    private Map<String, Object> body(Object data) {
        return Map.of("code", 0, "message", "success", "data", data);
    }
}
