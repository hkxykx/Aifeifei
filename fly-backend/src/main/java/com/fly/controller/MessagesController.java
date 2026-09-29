package com.fly.controller;

import com.fly.common.ClientIps;
import com.fly.dto.Requests.MessageCreateReq;
import com.fly.dto.Requests.StatusReq;
import com.fly.dto.Responses.MessageOut;
import com.fly.security.RequiresAuth;
import com.fly.service.GithubAuthService;
import com.fly.service.MessageService;
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

/** 留言板（对应原 app/api/messages.py） */
@RestController
@RequestMapping("/api/messages")
public class MessagesController {

    private final MessageService messageService;
    private final GithubAuthService githubAuthService;

    public MessagesController(MessageService messageService, GithubAuthService githubAuthService) {
        this.messageService = messageService;
        this.githubAuthService = githubAuthService;
    }

    @GetMapping
    public List<MessageOut> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return messageService.list("approved", page, size);
    }

    @GetMapping("/count")
    public Map<String, Object> count() {
        return Map.of("count", messageService.count("approved"));
    }

    @PostMapping
    public MessageOut create(@RequestBody(required = false) MessageCreateReq data,
                             HttpServletRequest request) {
        if (data == null) {
            throw new com.fly.common.ApiError(422, "请求体不能为空");
        }
        return messageService.create(data,
                githubAuthService.getGithubUserOptional(request),
                ClientIps.from(request));
    }

    @PostMapping("/{msgId:\\d+}/like")
    public MessageOut like(@PathVariable Long msgId) {
        return messageService.toggleLike(msgId, false);
    }

    @PostMapping("/{msgId:\\d+}/unlike")
    public MessageOut unlike(@PathVariable Long msgId) {
        return messageService.toggleLike(msgId, true);
    }

    @GetMapping("/admin/count")
    @RequiresAuth
    public Map<String, Object> adminCount(@RequestParam(required = false) String status) {
        return Map.of("count", messageService.count(status));
    }

    @GetMapping("/admin")
    @RequiresAuth
    public List<MessageOut> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return messageService.list(status, page, size, true);
    }

    @PutMapping("/{msgId:\\d+}/status")
    @RequiresAuth
    public MessageOut updateStatus(@PathVariable Long msgId, @RequestBody StatusReq data) {
        if (data == null || data.status == null) {
            throw new com.fly.common.ApiError(422, "status 为必填字段");
        }
        return messageService.updateStatus(msgId, data.status);
    }

    @DeleteMapping("/{msgId:\\d+}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long msgId) {
        return messageService.delete(msgId);
    }
}
