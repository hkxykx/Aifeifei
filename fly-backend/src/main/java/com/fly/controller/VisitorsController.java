package com.fly.controller;

import com.fly.common.ClientIps;
import com.fly.common.PageParams;
import com.fly.security.RequiresAuth;
import com.fly.service.VisitorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 访客记录（对应原 app/api/visitors.py） */
@RestController
@RequestMapping("/api/visitors")
public class VisitorsController {

    private final VisitorService visitorService;

    public VisitorsController(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    /** 访客列表含 IP/地域，仅管理员可见（/record、/location 保持匿名公开） */
    @GetMapping
    @RequiresAuth
    public Map<String, Object> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = PageParams.page(page);
        int s = PageParams.size(size, 20, 100);
        return Map.of("code", 0, "data", visitorService.recent(p, s));
    }

    @GetMapping("/count")
    @RequiresAuth
    public Map<String, Object> count() {
        return Map.of("code", 0, "count", visitorService.count());
    }

    @GetMapping("/location")
    public Map<String, Object> location(HttpServletRequest request) {
        return Map.of("code", 0, "data",
                visitorService.location(ClientIps.from(request)));
    }

    @PostMapping("/record")
    public Map<String, Object> record(HttpServletRequest request) {
        visitorService.recordVisit(
                ClientIps.from(request),
                ClientIps.path(request),
                ClientIps.userAgent(request));
        return Map.of("code", 0, "message", "ok");
    }

    @DeleteMapping("/{visitorId:\\d+}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long visitorId) {
        visitorService.delete(visitorId);
        return Map.of("code", 0, "message", "ok");
    }

    @DeleteMapping
    @RequiresAuth
    public Map<String, Object> deleteAll() {
        visitorService.clear();
        return Map.of("code", 0, "message", "ok");
    }
}
