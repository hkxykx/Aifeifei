package com.fly.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.common.ApiError;
import com.fly.dto.Requests.BookmarkCategoryCreateReq;
import com.fly.dto.Requests.BookmarkCategoryUpdateReq;
import com.fly.dto.Requests.BookmarkSiteCreateReq;
import com.fly.dto.Requests.BookmarkSiteUpdateReq;
import com.fly.dto.Responses.BookmarkFull;
import com.fly.dto.Responses.BookmarkSiteOut;
import com.fly.entity.BookmarkCategory;
import com.fly.entity.BookmarkSite;
import com.fly.repository.BookmarkCategoryRepository;
import com.fly.repository.BookmarkSiteRepository;
import com.fly.security.RequiresAuth;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 收藏夹（对应原 app/api/bookmarks.py） */
@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkCategoryRepository categoryRepository;
    private final BookmarkSiteRepository siteRepository;
    private final ObjectMapper objectMapper;

    public BookmarkController(BookmarkCategoryRepository categoryRepository,
                              BookmarkSiteRepository siteRepository,
                              ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
        this.objectMapper = objectMapper;
    }

    /** 前台完整收藏夹（分类 + 站点） */
    @GetMapping
    public List<BookmarkFull> full() {
        List<BookmarkFull> result = new ArrayList<>();
        for (BookmarkCategory cat : categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sort"))) {
            List<BookmarkSiteOut> sites = siteRepository.findByCategoryIdOrderBySortAsc(cat.id)
                    .stream().map(this::toSiteOut).toList();
            result.add(new BookmarkFull(cat.id, cat.name, cat.icon, cat.description,
                    cat.sort, cat.createdAt, sites));
        }
        return result;
    }

    // ---- 分类 ----

    @GetMapping("/categories")
    public List<BookmarkCategory> categories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sort"));
    }

    @PostMapping("/categories")
    @RequiresAuth
    public BookmarkCategory createCategory(@RequestBody BookmarkCategoryCreateReq data) {
        if (data.name == null) {
            throw new ApiError(422, "name 为必填字段");
        }
        BookmarkCategory cat = new BookmarkCategory();
        cat.name = data.name;
        cat.icon = data.icon == null ? "" : data.icon;
        cat.description = data.description == null ? "" : data.description;
        cat.sort = data.sort;
        return categoryRepository.save(cat);
    }

    @PutMapping("/categories/{catId}")
    @RequiresAuth
    public BookmarkCategory updateCategory(@PathVariable Long catId,
                                           @RequestBody BookmarkCategoryUpdateReq data) {
        BookmarkCategory cat = categoryRepository.findById(catId)
                .orElseThrow(() -> ApiError.notFound("分类不存在"));
        if (data.name != null) {
            cat.name = data.name;
        }
        if (data.icon != null) {
            cat.icon = data.icon;
        }
        if (data.description != null) {
            cat.description = data.description;
        }
        if (data.sort != null) {
            cat.sort = data.sort;
        }
        cat.updatedAt = LocalDateTime.now();
        return categoryRepository.save(cat);
    }

    @DeleteMapping("/categories/{catId}")
    @RequiresAuth
    @Transactional
    public Map<String, Object> deleteCategory(@PathVariable Long catId) {
        BookmarkCategory cat = categoryRepository.findById(catId)
                .orElseThrow(() -> ApiError.notFound("分类不存在"));
        siteRepository.deleteByCategoryId(catId);
        categoryRepository.delete(cat);
        return Map.of("ok", true);
    }

    // ---- 站点 ----

    @GetMapping("/sites")
    public List<BookmarkSiteOut> sites(@RequestParam(required = false) Long categoryId) {
        List<BookmarkSite> rows = categoryId != null
                ? siteRepository.findByCategoryIdOrderBySortAsc(categoryId)
                : siteRepository.findAll(Sort.by(Sort.Direction.ASC, "sort"));
        return rows.stream().map(this::toSiteOut).toList();
    }

    @PostMapping("/sites")
    @RequiresAuth
    public BookmarkSiteOut createSite(@RequestBody BookmarkSiteCreateReq data) {
        if (data.categoryId == null || data.name == null || data.url == null) {
            throw new ApiError(422, "category_id、name 和 url 为必填字段");
        }
        BookmarkSite site = new BookmarkSite();
        site.categoryId = data.categoryId;
        site.name = data.name;
        site.url = data.url;
        site.icon = data.icon == null ? "" : data.icon;
        site.description = data.description == null ? "" : data.description;
        site.platforms = toJson(data.platforms);
        site.sort = data.sort;
        site = siteRepository.save(site);
        return toSiteOut(site);
    }

    @PutMapping("/sites/{siteId}")
    @RequiresAuth
    public BookmarkSiteOut updateSite(@PathVariable Long siteId,
                                      @RequestBody BookmarkSiteUpdateReq data) {
        BookmarkSite site = siteRepository.findById(siteId)
                .orElseThrow(() -> ApiError.notFound("站点不存在"));
        if (data.categoryId != null) {
            site.categoryId = data.categoryId;
        }
        if (data.name != null) {
            site.name = data.name;
        }
        if (data.url != null) {
            site.url = data.url;
        }
        if (data.icon != null) {
            site.icon = data.icon;
        }
        if (data.description != null) {
            site.description = data.description;
        }
        if (data.platforms != null) {
            site.platforms = toJson(data.platforms);
        }
        if (data.sort != null) {
            site.sort = data.sort;
        }
        site.updatedAt = LocalDateTime.now();
        site = siteRepository.save(site);
        return toSiteOut(site);
    }

    @DeleteMapping("/sites/{siteId}")
    @RequiresAuth
    public Map<String, Object> deleteSite(@PathVariable Long siteId) {
        BookmarkSite site = siteRepository.findById(siteId)
                .orElseThrow(() -> ApiError.notFound("站点不存在"));
        siteRepository.delete(site);
        return Map.of("ok", true);
    }

    private BookmarkSiteOut toSiteOut(BookmarkSite site) {
        return new BookmarkSiteOut(site.id, site.categoryId, site.name, site.url, site.icon,
                site.description, parsePlatforms(site.platforms), site.sort,
                site.createdAt, site.updatedAt);
    }

    private List<String> parsePlatforms(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
