package com.fly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.common.ApiError;
import com.fly.dto.Requests.SiteConfigCreateReq;
import com.fly.dto.Requests.SiteConfigUpdateReq;
import com.fly.dto.Responses.SiteConfigOut;
import com.fly.dto.Responses.SiteConfigRow;
import com.fly.entity.SiteConfig;
import com.fly.repository.SiteConfigRepository;
import com.fly.security.RequiresAuth;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 站点配置（对应原 app/api/site_config.py） */
@RestController
@RequestMapping("/api/site-config")
public class SiteConfigController {

    private final SiteConfigRepository siteConfigRepository;
    private final ObjectMapper objectMapper;

    public SiteConfigController(SiteConfigRepository siteConfigRepository, ObjectMapper objectMapper) {
        this.siteConfigRepository = siteConfigRepository;
        this.objectMapper = objectMapper;
    }

    /** 所有配置的 key-value 字典（value 尽量还原为 JSON 类型）。
     *  需管理员鉴权：站点配置可能含敏感值，匿名只读字典等于开放泄露口子。 */
    @GetMapping
    @RequiresAuth
    public Map<String, Object> getAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (SiteConfig row : siteConfigRepository.findAll()) {
            result.put(row.key, parseValue(row.value));
        }
        return result;
    }

    /** 管理端完整列表 */
    @GetMapping("/list")
    @RequiresAuth
    public List<SiteConfigRow> list() {
        return siteConfigRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(
                        a.id == null ? 0 : a.id, b.id == null ? 0 : b.id))
                .map(r -> new SiteConfigRow(r.id, r.key, r.value,
                        r.description == null ? "" : r.description, r.updatedAt))
                .toList();
    }

    @GetMapping("/{key}")
    @RequiresAuth
    public Object getOne(@PathVariable String key) {
        SiteConfig row = siteConfigRepository.findByKey(key)
                .orElseThrow(() -> ApiError.notFound("配置 " + key + " 不存在"));
        return parseValue(row.value);
    }

    @PostMapping
    @RequiresAuth
    public SiteConfigOut create(@RequestBody SiteConfigCreateReq data) {
        if (data.key == null) {
            throw new ApiError(422, "key 为必填字段");
        }
        if (siteConfigRepository.findByKey(data.key).isPresent()) {
            throw ApiError.badRequest("配置 " + data.key + " 已存在");
        }
        SiteConfig row = new SiteConfig();
        row.key = data.key;
        row.value = data.value == null ? "" : data.value;
        row.description = data.description == null ? "" : data.description;
        row = siteConfigRepository.save(row);
        return new SiteConfigOut(row.id, row.key, row.value, row.description);
    }

    @PutMapping("/{key}")
    @RequiresAuth
    public SiteConfigOut update(@PathVariable String key, @RequestBody SiteConfigUpdateReq data) {
        if (data.value == null) {
            throw new ApiError(422, "value 为必填字段");
        }
        SiteConfig row = siteConfigRepository.findByKey(key).orElse(null);
        if (row == null) {
            row = new SiteConfig();
            row.key = key;
            row.value = data.value;
            row.description = data.description == null ? "" : data.description;
        } else {
            row.value = data.value;
            if (data.description != null && !data.description.isEmpty()) {
                row.description = data.description;
            }
        }
        row.updatedAt = LocalDateTime.now();
        row = siteConfigRepository.save(row);
        return new SiteConfigOut(row.id, row.key, row.value, row.description);
    }

    /** 批量更新：body 形如 {"site_title": "新标题", ...}，写回后返回全部配置字典 */
    @PutMapping
    @RequiresAuth
    public Map<String, Object> batchUpdate(@RequestBody Map<String, Object> configs) {
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            SiteConfig row = siteConfigRepository.findByKey(entry.getKey()).orElse(null);
            String value;
            try {
                value = objectMapper.writeValueAsString(entry.getValue());
            } catch (Exception e) {
                value = String.valueOf(entry.getValue());
            }
            if (row == null) {
                row = new SiteConfig();
                row.key = entry.getKey();
                row.value = value;
            } else {
                row.value = value;
            }
            row.updatedAt = LocalDateTime.now();
            siteConfigRepository.save(row);
        }
        return getAll();
    }

    @DeleteMapping("/{key}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable String key) {
        SiteConfig row = siteConfigRepository.findByKey(key)
                .orElseThrow(() -> ApiError.notFound("配置 " + key + " 不存在"));
        siteConfigRepository.delete(row);
        return Map.of("ok", true);
    }

    private Object parseValue(String value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception e) {
            return value;
        }
    }
}
