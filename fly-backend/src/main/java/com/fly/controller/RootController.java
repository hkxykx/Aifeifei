package com.fly.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 根路由（对应原 main.py 的 /api/health、/api/routes） */
@RestController
public class RootController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/routes")
    public Map<String, Object> routes() {
        return Map.of(
                "code", 0,
                "message", "success",
                "data", List.of());
    }
}
