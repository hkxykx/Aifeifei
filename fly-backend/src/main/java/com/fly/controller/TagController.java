package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.dto.Requests.TagCreateReq;
import com.fly.dto.Requests.TagUpdateReq;
import com.fly.entity.Tag;
import com.fly.repository.TagRepository;
import com.fly.security.RequiresAuth;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 文章标签（对应原 app/api/tags.py） */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @GetMapping
    public List<Tag> list() {
        return tagRepository.findAll(Sort.by(Sort.Direction.DESC, "postCount"));
    }

    @PostMapping
    @RequiresAuth
    public Tag create(@RequestBody TagCreateReq data) {
        if (data.name == null || data.slug == null) {
            throw new ApiError(422, "name 和 slug 为必填字段");
        }
        Tag tag = new Tag();
        tag.name = data.name;
        tag.slug = data.slug;
        return tagRepository.save(tag);
    }

    @PutMapping("/{tagId}")
    @RequiresAuth
    public Tag update(@PathVariable Long tagId, @RequestBody TagUpdateReq data) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> ApiError.notFound("标签不存在"));
        if (data.name != null) {
            tag.name = data.name;
        }
        if (data.slug != null) {
            tag.slug = data.slug;
        }
        return tagRepository.save(tag);
    }

    @DeleteMapping("/{tagId}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> ApiError.notFound("标签不存在"));
        tagRepository.delete(tag);
        return Map.of("ok", true);
    }
}
