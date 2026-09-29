package com.fly.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.common.ApiError;
import com.fly.common.PageParams;
import com.fly.dto.Requests.ChatterCommentCreateReq;
import com.fly.dto.Requests.ChatterCreateReq;
import com.fly.dto.Requests.ChatterUpdateReq;
import com.fly.dto.Responses.ChatterCommentOut;
import com.fly.dto.Responses.ChatterOut;
import com.fly.dto.Responses.GitHubUserOut;
import com.fly.entity.Chatter;
import com.fly.entity.ChatterComment;
import com.fly.entity.GitHubUser;
import com.fly.repository.ChatterCommentRepository;
import com.fly.repository.ChatterRepository;
import com.fly.repository.GitHubUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 说说/微语（对应原 app/services/chatter_service.py）。
 */
@Service
public class ChatterService {

    private final ChatterRepository chatterRepository;
    private final ChatterCommentRepository commentRepository;
    private final GitHubUserRepository githubUserRepository;
    private final ObjectMapper objectMapper;

    public ChatterService(ChatterRepository chatterRepository,
                          ChatterCommentRepository commentRepository,
                          GitHubUserRepository githubUserRepository,
                          ObjectMapper objectMapper) {
        this.chatterRepository = chatterRepository;
        this.commentRepository = commentRepository;
        this.githubUserRepository = githubUserRepository;
        this.objectMapper = objectMapper;
    }

    public List<ChatterOut> list(String status, Integer page, Integer size) {
        var pageable = PageRequest.of(PageParams.page(page) - 1, PageParams.size(size, 20, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return chatterRepository.findAll(statusSpec(status), pageable)
                .getContent().stream().map(this::toOut).toList();
    }

    public long count(String status) {
        return chatterRepository.count(statusSpec(status));
    }

    public ChatterOut getById(Long chatterId, boolean isAdmin) {
        Chatter c = chatterRepository.findById(chatterId)
                .orElseThrow(() -> ApiError.notFound("说说不存在"));
        // 非管理员访问未发布说说 → 404（不暴露存在性）
        if (!isAdmin && !"published".equals(c.status)) {
            throw ApiError.notFound("说说不存在");
        }
        return toOut(c);
    }

    @Transactional
    public ChatterOut create(ChatterCreateReq data) {
        if (data.content == null || data.content.isBlank()) {
            throw new ApiError(422, "content 为必填字段");
        }
        if (data.content.trim().length() > 255) {
            throw new ApiError(422, "说说内容不能超过 255 字");
        }
        if (data.mood != null && data.mood.length() > 255) {
            throw new ApiError(422, "心情标签不能超过 255 字");
        }
        if (data.status != null && data.status.length() > 20) {
            throw new ApiError(422, "status 过长");
        }
        Chatter c = new Chatter();
        c.content = data.content.trim();
        c.images = toJson(data.images);
        c.mood = data.mood == null ? "" : data.mood;
        c.status = data.status == null ? "draft" : data.status;
        if ("published".equals(c.status)) {
            c.createdAt = LocalDateTime.now();
        }
        c = chatterRepository.save(c);
        return toOut(c);
    }

    @Transactional
    public ChatterOut update(Long chatterId, ChatterUpdateReq data) {
        Chatter c = chatterRepository.findById(chatterId)
                .orElseThrow(() -> ApiError.notFound("说说不存在"));
        if (data.content != null) {
            if (data.content.isBlank()) {
                throw new ApiError(422, "content 不能为空");
            }
            if (data.content.trim().length() > 255) {
                throw new ApiError(422, "说说内容不能超过 255 字");
            }
            c.content = data.content.trim();
        }
        if (data.images != null) {
            c.images = toJson(data.images);
        }
        if (data.mood != null) {
            if (data.mood.length() > 255) {
                throw new ApiError(422, "心情标签不能超过 255 字");
            }
            c.mood = data.mood;
        }
        if (data.status != null) {
            if (data.status.length() > 20) {
                throw new ApiError(422, "status 过长");
            }
            c.status = data.status;
        }
        c.updatedAt = LocalDateTime.now();
        c = chatterRepository.save(c);
        return toOut(c);
    }

    @Transactional
    public Map<String, Object> delete(Long chatterId) {
        Chatter c = chatterRepository.findById(chatterId)
                .orElseThrow(() -> ApiError.notFound("说说不存在"));
        commentRepository.deleteByChatterId(chatterId);
        chatterRepository.delete(c);
        return Map.of("ok", true);
    }

    public Map<String, Object> toggleLike(Long chatterId, boolean unlike) {
        Chatter c = chatterRepository.findById(chatterId)
                .orElseThrow(() -> ApiError.notFound("说说不存在"));
        c.likes = Math.max(0, c.likes + (unlike ? -1 : 1));
        c = chatterRepository.save(c);
        return Map.of("likes", c.likes);
    }

    // ---- 说说评论 ----

    /** 公开：已审核评论层级组装（回复按创建时间倒序）；非管理员不可读未发布说说的评论 */
    public List<ChatterCommentOut> getComments(Long chatterId, boolean isAdmin) {
        Chatter chatter = chatterRepository.findById(chatterId)
                .orElseThrow(() -> ApiError.notFound("说说不存在"));
        if (!isAdmin && !"published".equals(chatter.status)) {
            throw ApiError.notFound("说说不存在");
        }
        List<ChatterComment> rows = commentRepository
                .findByChatterIdAndStatusOrderByCreatedAtDesc(chatterId, "approved");
        Map<Long, ChatterCommentOut> idMap = new HashMap<>();
        for (ChatterComment c : rows) {
            idMap.put(c.id, toCommentOut(c, false));
        }
        List<ChatterCommentOut> roots = new ArrayList<>();
        for (ChatterComment c : rows) {
            ChatterCommentOut out = idMap.get(c.id);
            if (c.parentId != null && idMap.containsKey(c.parentId)) {
                idMap.get(c.parentId).replies().add(out);
            } else {
                roots.add(out);
            }
        }
        return roots;
    }

    /** 管理：仅顶层分页，含 IP，回复递归（含未审核，正序） */
    public List<ChatterCommentOut> adminCommentList(String status, Integer page, Integer size) {
        var pageable = PageRequest.of(PageParams.page(page) - 1, PageParams.size(size, 20, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ChatterComment> rows = commentRepository.findAll((root, query, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.isNull(root.get("parentId")));
            if (status != null && !status.isEmpty()) {
                preds.add(cb.equal(root.get("status"), status));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable).getContent();
        return rows.stream().map(c -> toCommentOutRecursive(c, true)).toList();
    }

    /** 匿名可提交（仅限已发布说说），默认 pending，站长在后台审核通过后才公开展示（计数仅统计已通过） */
    @Transactional
    public ChatterCommentOut createComment(ChatterCommentCreateReq data,
                                           GitHubUser githubUser, String ip, boolean isAdmin) {
        if (data.chatterId == null || data.content == null || data.content.isBlank()) {
            throw new ApiError(422, "chatter_id 和 content 为必填字段");
        }
        if (data.content.trim().length() > 255) {
            throw new ApiError(422, "评论内容不能超过 255 字");
        }
        if (data.parentId != null) {
            commentRepository.findById(data.parentId)
                    .orElseThrow(() -> ApiError.notFound("被回复的评论不存在"));
        }
        Chatter chatter = chatterRepository.findById(data.chatterId)
                .orElseThrow(() -> ApiError.notFound("说说不存在"));
        if (!isAdmin && !"published".equals(chatter.status)) {
            throw ApiError.notFound("说说不存在");
        }

        ChatterComment comment = new ChatterComment();
        comment.chatterId = data.chatterId;
        comment.parentId = data.parentId;
        comment.githubUserId = githubUser == null ? null : githubUser.id;
        comment.content = data.content.trim();
        comment.ip = ip == null ? "" : ip;
        comment.status = "pending";
        comment = commentRepository.save(comment);
        return toCommentOut(comment, false);
    }

    @Transactional
    public ChatterCommentOut updateCommentStatus(Long commentId, String status) {
        if (!"pending".equals(status) && !"approved".equals(status) && !"rejected".equals(status)) {
            throw new ApiError(422, "status 仅支持 pending/approved/rejected");
        }
        ChatterComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("评论不存在"));
        String old = comment.status;
        comment.status = status;
        comment = commentRepository.save(comment);

        // 维护说说的已通过评论计数
        boolean nowApproved = "approved".equals(status);
        boolean wasApproved = "approved".equals(old);
        if (nowApproved != wasApproved) {
            chatterRepository.findById(comment.chatterId).ifPresent(chatter -> {
                chatter.commentsCount = Math.max(0, chatter.commentsCount + (nowApproved ? 1 : -1));
                chatterRepository.save(chatter);
            });
        }
        return toCommentOut(comment, false);
    }

    @Transactional
    public Map<String, Object> deleteComment(Long commentId) {
        ChatterComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("评论不存在"));
        commentRepository.delete(comment);
        return Map.of("ok", true);
    }

    public ChatterCommentOut toggleCommentLike(Long commentId, boolean unlike) {
        ChatterComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("评论不存在"));
        comment.likes = Math.max(0, comment.likes + (unlike ? -1 : 1));
        comment = commentRepository.save(comment);
        return toCommentOut(comment, false);
    }

    // ---- 组装 ----

    private Specification<Chatter> statusSpec(String status) {
        return (root, query, cb) -> {
            if (status == null || status.isEmpty()) {
                return cb.conjunction();
            }
            return cb.equal(root.get("status"), status);
        };
    }

    private ChatterOut toOut(Chatter c) {
        return new ChatterOut(c.id, c.content, parseImages(c.images),
                c.mood == null ? "" : c.mood, c.likes, c.commentsCount, c.status,
                c.createdAt, c.updatedAt);
    }

    private List<String> parseImages(String json) {
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

    private ChatterCommentOut toCommentOut(ChatterComment c, boolean includeIp) {
        GitHubUserOut gh = c.githubUserId == null ? null
                : githubUserRepository.findById(c.githubUserId)
                .map(u -> new GitHubUserOut(u.id, u.login, u.avatar, u.bio))
                .orElse(null);
        return new ChatterCommentOut(c.id, c.chatterId, c.parentId, c.content, c.likes,
                c.status, c.createdAt, gh, new ArrayList<>(), includeIp ? c.ip : null);
    }

    private ChatterCommentOut toCommentOutRecursive(ChatterComment c, boolean includeIp) {
        ChatterCommentOut out = toCommentOut(c, includeIp);
        commentRepository.findByParentIdOrderByCreatedAtAsc(c.id).forEach(child ->
                out.replies().add(toCommentOutRecursive(child, includeIp)));
        return out;
    }
}
