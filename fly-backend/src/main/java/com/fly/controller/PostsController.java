package com.fly.controller;

import com.fly.common.PageParams;
import com.fly.dto.Requests.PostCreateReq;
import com.fly.dto.Requests.PostUpdateReq;
import com.fly.dto.Responses.PostDetail;
import com.fly.dto.Responses.PostOut;
import com.fly.security.OptionalAuth;
import com.fly.security.RequiresAuth;
import com.fly.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 文章（对应原 app/api/posts.py） */
@RestController
@RequestMapping("/api/posts")
public class PostsController {

    private final PostService postService;
    private final OptionalAuth optionalAuth;

    public PostsController(PostService postService, OptionalAuth optionalAuth) {
        this.postService = postService;
        this.optionalAuth = optionalAuth;
    }

    @GetMapping
    public List<PostOut> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        int p = PageParams.page(page);
        int s = PageParams.size(size, 10, 200);
        // 匿名仅可见已发布；管理员（携带有效令牌）可按 status 查草稿
        String effectiveStatus = optionalAuth.isAdmin(request) ? status : "published";
        return postService.getPosts(effectiveStatus, category, tag, p, s);
    }

    @GetMapping("/count")
    public Map<String, Object> count(@RequestParam(required = false) String status,
                                     HttpServletRequest request) {
        String effectiveStatus = optionalAuth.isAdmin(request) ? status : "published";
        return Map.of("count", postService.countPosts(effectiveStatus));
    }

    @GetMapping("/detail/{postId:\\d+}")
    public PostDetail detail(@PathVariable Long postId, HttpServletRequest request) {
        return postService.getById(postId, optionalAuth.isAdmin(request));
    }

    @GetMapping("/{slug}")
    public PostDetail bySlug(@PathVariable String slug, HttpServletRequest request) {
        return postService.getBySlug(slug, optionalAuth.isAdmin(request));
    }

    @PostMapping
    @RequiresAuth
    public PostOut create(@RequestBody PostCreateReq data) {
        return postService.create(data);
    }

    @PutMapping("/{postId:\\d+}")
    @RequiresAuth
    public PostOut update(@PathVariable Long postId, @RequestBody PostUpdateReq data) {
        return postService.update(postId, data);
    }

    @PostMapping("/{postId:\\d+}/like")
    public Map<String, Object> like(@PathVariable Long postId) {
        return postService.toggleLike(postId, false);
    }

    @PostMapping("/{postId:\\d+}/unlike")
    public Map<String, Object> unlike(@PathVariable Long postId) {
        return postService.toggleLike(postId, true);
    }

    @DeleteMapping("/{postId:\\d+}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long postId) {
        return postService.delete(postId);
    }
}
