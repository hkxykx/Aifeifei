package com.fly.controller;

import com.fly.common.ClientIps;
import com.fly.dto.Requests.ChatterCommentCreateReq;
import com.fly.dto.Requests.ChatterCreateReq;
import com.fly.dto.Requests.ChatterUpdateReq;
import com.fly.dto.Requests.StatusReq;
import com.fly.dto.Responses.ChatterCommentOut;
import com.fly.dto.Responses.ChatterOut;
import com.fly.security.OptionalAuth;
import com.fly.security.RequiresAuth;
import com.fly.service.ChatterService;
import com.fly.service.GithubAuthService;
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

/** 说说/微语（对应原 app/api/chatters.py） */
@RestController
@RequestMapping("/api/chatters")
public class ChattersController {

    private final ChatterService chatterService;
    private final GithubAuthService githubAuthService;
    private final OptionalAuth optionalAuth;

    public ChattersController(ChatterService chatterService, GithubAuthService githubAuthService,
                              OptionalAuth optionalAuth) {
        this.chatterService = chatterService;
        this.githubAuthService = githubAuthService;
        this.optionalAuth = optionalAuth;
    }

    @GetMapping
    public List<ChatterOut> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return chatterService.list("published", page, size);
    }

    @GetMapping("/count")
    public Map<String, Object> count(@RequestParam(required = false) String status,
                                     HttpServletRequest request) {
        // 匿名仅统计已发布；管理员可按 status 统计草稿/待审核
        String effective = optionalAuth.isAdmin(request)
                ? (status == null || status.isEmpty() ? "published" : status)
                : "published";
        return Map.of("count", chatterService.count(effective));
    }

    @GetMapping("/{chatterId:\\d+}/comments")
    public List<ChatterCommentOut> comments(@PathVariable Long chatterId,
                                            HttpServletRequest request) {
        return chatterService.getComments(chatterId, optionalAuth.isAdmin(request));
    }

    @PostMapping("/comments")
    public ChatterCommentOut createComment(
            @RequestBody(required = false) ChatterCommentCreateReq data,
            HttpServletRequest request) {
        if (data == null) {
            throw new com.fly.common.ApiError(422, "请求体不能为空");
        }
        return chatterService.createComment(data,
                githubAuthService.getGithubUserOptional(request),
                ClientIps.from(request),
                optionalAuth.isAdmin(request));
    }

    @GetMapping("/admin")
    @RequiresAuth
    public List<ChatterOut> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return chatterService.list(status, page, size);
    }

    @PostMapping
    @RequiresAuth
    public ChatterOut create(@RequestBody(required = false) ChatterCreateReq data) {
        if (data == null) {
            throw new com.fly.common.ApiError(422, "请求体不能为空");
        }
        return chatterService.create(data);
    }

    @GetMapping("/comments/admin")
    @RequiresAuth
    public List<ChatterCommentOut> adminComments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return chatterService.adminCommentList(status, page, size);
    }

    @PutMapping("/comments/{commentId:\\d+}/status")
    @RequiresAuth
    public ChatterCommentOut updateCommentStatus(@PathVariable Long commentId,
                                                 @RequestBody StatusReq data) {
        if (data == null || data.status == null) {
            throw new com.fly.common.ApiError(422, "status 为必填字段");
        }
        return chatterService.updateCommentStatus(commentId, data.status);
    }

    @DeleteMapping("/comments/{commentId:\\d+}")
    @RequiresAuth
    public Map<String, Object> deleteComment(@PathVariable Long commentId) {
        return chatterService.deleteComment(commentId);
    }

    @PostMapping("/comments/{commentId:\\d+}/like")
    public ChatterCommentOut likeComment(@PathVariable Long commentId) {
        return chatterService.toggleCommentLike(commentId, false);
    }

    @PostMapping("/comments/{commentId:\\d+}/unlike")
    public ChatterCommentOut unlikeComment(@PathVariable Long commentId) {
        return chatterService.toggleCommentLike(commentId, true);
    }

    @GetMapping("/{chatterId:\\d+}")
    public ChatterOut get(@PathVariable Long chatterId, HttpServletRequest request) {
        return chatterService.getById(chatterId, optionalAuth.isAdmin(request));
    }

    @PostMapping("/{chatterId:\\d+}/like")
    public Map<String, Object> like(@PathVariable Long chatterId) {
        return chatterService.toggleLike(chatterId, false);
    }

    @PostMapping("/{chatterId:\\d+}/unlike")
    public Map<String, Object> unlike(@PathVariable Long chatterId) {
        return chatterService.toggleLike(chatterId, true);
    }

    @PutMapping("/{chatterId:\\d+}")
    @RequiresAuth
    public ChatterOut update(@PathVariable Long chatterId,
                             @RequestBody(required = false) ChatterUpdateReq data) {
        if (data == null) {
            throw new com.fly.common.ApiError(422, "请求体不能为空");
        }
        return chatterService.update(chatterId, data);
    }

    @DeleteMapping("/{chatterId:\\d+}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long chatterId) {
        return chatterService.delete(chatterId);
    }
}
