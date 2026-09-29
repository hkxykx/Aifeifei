package com.fly.controller;

import com.fly.security.RequiresAuth;
import com.fly.service.AuthService;
import com.fly.dto.Requests.LoginReq;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端认证（对应原 app/api/auth.py） */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody(required = false) LoginReq req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    @RequiresAuth
    public Map<String, Object> me(HttpServletRequest request) {
        return authService.me(request);
    }

    @PutMapping("/me")
    @RequiresAuth
    public Map<String, Object> updateMe(HttpServletRequest request,
                                        @RequestBody(required = false) Map<String, Object> data) {
        return authService.updateMe(request, data == null ? Map.of() : data);
    }
}
