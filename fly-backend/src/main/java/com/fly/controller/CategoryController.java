package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.dto.Requests.CategoryCreateReq;
import com.fly.dto.Requests.CategoryUpdateReq;
import com.fly.entity.Category;
import com.fly.repository.CategoryRepository;
import com.fly.repository.PostRepository;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 文章分类（对应原 app/api/categories.py） */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;

    public CategoryController(CategoryRepository categoryRepository, PostRepository postRepository) {
        this.categoryRepository = categoryRepository;
        this.postRepository = postRepository;
    }

    @GetMapping
    public List<Category> list() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sort"));
    }

    @PostMapping
    @RequiresAuth
    public Category create(@RequestBody CategoryCreateReq data) {
        if (data.name == null || data.slug == null) {
            throw new ApiError(422, "name 和 slug 为必填字段");
        }
        Category cat = new Category();
        cat.name = data.name;
        cat.slug = data.slug;
        cat.description = data.description == null ? "" : data.description;
        cat.sort = data.sort;
        return categoryRepository.save(cat);
    }

    @PutMapping("/{catId}")
    @RequiresAuth
    public Category update(@PathVariable Long catId, @RequestBody CategoryUpdateReq data) {
        Category cat = categoryRepository.findById(catId)
                .orElseThrow(() -> ApiError.notFound("分类不存在"));
        if (data.name != null) {
            cat.name = data.name;
        }
        if (data.slug != null) {
            cat.slug = data.slug;
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

    @DeleteMapping("/{catId}")
    @RequiresAuth
    @Transactional
    public Map<String, Object> delete(@PathVariable Long catId) {
        Category cat = categoryRepository.findById(catId)
                .orElseThrow(() -> ApiError.notFound("分类不存在"));
        postRepository.clearCategoryRef(catId);
        categoryRepository.delete(cat);
        return Map.of("ok", true);
    }
}
