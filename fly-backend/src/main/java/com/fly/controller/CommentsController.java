package com.fly.controller;

import com.fly.common.ClientIps;
import com.fly.common.PageParams;
import com.fly.dto.Requests.CommentCreateReq;
import com.fly.dto.Requests.StatusReq;
import com.fly.dto.Responses.CommentOut;
import com.fly.security.RequiresAuth;
import com.fly.service.CommentService;
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

/** 文章评论（对应原 app/api/comments.py） */
@RestController
@RequestMapping("/api/comments")
public class CommentsController {

    private final CommentService commentService;
    private final GithubAuthService githubAuthService;

    public CommentsController(CommentService commentService, GithubAuthService githubAuthService) {
        this.commentService = commentService;
        this.githubAuthService = githubAuthService;
    }

    @GetMapping("/post/{postId:\\d+}")
    public List<CommentOut> byPost(@PathVariable Long postId) {
        return commentService.getByPost(postId);
    }

    @PostMapping
    public CommentOut create(@RequestBody(required = false) CommentCreateReq data,
                             HttpServletRequest request) {
        if (data == null) {
            throw new com.fly.common.ApiError(422, "请求体不能为空");
        }
        return commentService.create(data,
                githubAuthService.getGithubUserOptional(request),
                ClientIps.from(request));
    }

    @GetMapping("/admin")
    @RequiresAuth
    public List<CommentOut> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return commentService.adminList(status, page, size);
    }

    @PutMapping("/{commentId:\\d+}/status")
    @RequiresAuth
    public CommentOut updateStatus(@PathVariable Long commentId, @RequestBody StatusReq data) {
        if (data == null || data.status == null) {
            throw new com.fly.common.ApiError(422, "status 为必填字段");
        }
        return commentService.updateStatus(commentId, data.status);
    }

    @PostMapping("/{commentId:\\d+}/like")
    public CommentOut like(@PathVariable Long commentId) {
        return commentService.toggleLike(commentId, false);
    }

    @PostMapping("/{commentId:\\d+}/unlike")
    public CommentOut unlike(@PathVariable Long commentId) {
        return commentService.toggleLike(commentId, true);
    }

    @DeleteMapping("/{commentId:\\d+}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long commentId) {
        return commentService.delete(commentId);
    }
}
