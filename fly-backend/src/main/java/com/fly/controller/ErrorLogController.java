package com.fly.controller;

import com.fly.common.ErrorLogService;
import com.fly.security.RequiresAuth;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控方案 A：后端异常日志查询（站长后台「异常日志」分区用）。
 */
@RestController
@RequestMapping("/api/admin/errors")
@RequiresAuth
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    public ErrorLogController(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false, defaultValue = "100") Integer limit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isSuccess", true);
        m.put("data", errorLogService.list(limit == null ? 100 : limit));
        return m;
    }

    @DeleteMapping
    public Map<String, Object> clear() {
        errorLogService.clear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isSuccess", true);
        m.put("data", true);
        return m;
    }

    @DeleteMapping("/{id:\\d+}")
    public Map<String, Object> delete(@PathVariable long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isSuccess", errorLogService.delete(id));
        m.put("data", true);
        return m;
    }
}
