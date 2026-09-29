package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.dto.Requests.FriendLinkCreateReq;
import com.fly.dto.Requests.FriendLinkUpdateReq;
import com.fly.entity.FriendLink;
import com.fly.repository.FriendLinkRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 友情链接（对应原 app/api/friend_links.py） */
@RestController
@RequestMapping("/api/friend-links")
public class FriendLinkController {

    private final FriendLinkRepository friendLinkRepository;

    public FriendLinkController(FriendLinkRepository friendLinkRepository) {
        this.friendLinkRepository = friendLinkRepository;
    }

    @GetMapping
    public List<FriendLink> list() {
        return friendLinkRepository.findAll(Sort.by(Sort.Direction.ASC, "sort")).stream()
                .filter(fl -> fl.isApproved)
                .toList();
    }

    @GetMapping("/admin")
    @RequiresAuth
    public List<FriendLink> adminList() {
        return friendLinkRepository.findAll(Sort.by(Sort.Direction.ASC, "sort"));
    }

    @PostMapping
    @RequiresAuth
    public FriendLink create(@RequestBody FriendLinkCreateReq data) {
        if (data.name == null || data.url == null) {
            throw new ApiError(422, "name 和 url 为必填字段");
        }
        FriendLink fl = new FriendLink();
        fl.name = data.name;
        fl.url = data.url;
        fl.avatar = data.avatar == null ? "" : data.avatar;
        fl.description = data.description == null ? "" : data.description;
        fl.sort = data.sort;
        return friendLinkRepository.save(fl);
    }

    @PutMapping("/{linkId}")
    @RequiresAuth
    public FriendLink update(@PathVariable Long linkId, @RequestBody FriendLinkUpdateReq data) {
        FriendLink fl = friendLinkRepository.findById(linkId)
                .orElseThrow(() -> ApiError.notFound("友链不存在"));
        if (data.name != null) {
            fl.name = data.name;
        }
        if (data.url != null) {
            fl.url = data.url;
        }
        if (data.avatar != null) {
            fl.avatar = data.avatar;
        }
        if (data.description != null) {
            fl.description = data.description;
        }
        if (data.sort != null) {
            fl.sort = data.sort;
        }
        if (data.isApproved != null) {
            fl.isApproved = data.isApproved;
        }
        fl.updatedAt = LocalDateTime.now();
        return friendLinkRepository.save(fl);
    }

    @DeleteMapping("/{linkId}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long linkId) {
        FriendLink fl = friendLinkRepository.findById(linkId)
                .orElseThrow(() -> ApiError.notFound("友链不存在"));
        friendLinkRepository.delete(fl);
        return Map.of("ok", true);
    }
}
