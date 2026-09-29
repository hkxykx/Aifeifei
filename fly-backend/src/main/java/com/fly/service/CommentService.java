package com.fly.service;

import com.fly.common.ApiError;
import com.fly.common.PageParams;
import com.fly.dto.Requests.CommentCreateReq;
import com.fly.dto.Responses.CommentOut;
import com.fly.dto.Responses.GitHubUserOut;
import com.fly.entity.Comment;
import com.fly.entity.GitHubUser;
import com.fly.repository.CommentRepository;
import com.fly.repository.GitHubUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章评论（对应原 app/services/comment_service.py）。
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final GitHubUserRepository githubUserRepository;

    public CommentService(CommentRepository commentRepository,
                          GitHubUserRepository githubUserRepository) {
        this.commentRepository = commentRepository;
        this.githubUserRepository = githubUserRepository;
    }

    /** 公开：文章的已审核评论，按层级组装（回复按创建时间倒序，与原实现一致） */
    public List<CommentOut> getByPost(Long postId) {
        List<Comment> rows = commentRepository
                .findByPostIdAndStatusOrderByCreatedAtDesc(postId, "approved");
        Map<Long, CommentOut> idMap = new HashMap<>();
        for (Comment c : rows) {
            idMap.put(c.id, toOut(c, false));
        }
        List<CommentOut> roots = new ArrayList<>();
        for (Comment c : rows) {
            CommentOut out = idMap.get(c.id);
            if (c.parentId != null && idMap.containsKey(c.parentId)) {
                idMap.get(c.parentId).replies().add(out);
            } else {
                roots.add(out);
            }
        }
        return roots;
    }

    /** 管理：仅顶层评论分页，含 IP，回复递归拉取（含未审核，按创建时间正序） */
    public List<CommentOut> adminList(String status, Integer page, Integer size) {
        var pageable = PageRequest.of(PageParams.page(page) - 1, PageParams.size(size, 20, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Comment> rows = commentRepository.findAll((root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isNull(root.get("parentId")));
            if (status != null && !status.isEmpty()) {
                preds.add(cb.equal(root.get("status"), status));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        }, pageable).getContent();
        return rows.stream().map(c -> toOutRecursive(c, true)).toList();
    }

    /** 匿名可提交，默认 pending，站长在后台审核通过后才公开展示 */
    @Transactional
    public CommentOut create(CommentCreateReq data, GitHubUser githubUser, String ip) {
        if (data.postId == null || data.content == null || data.content.isBlank()) {
            throw new ApiError(422, "post_id 和 content 为必填字段");
        }
        if (data.content.trim().length() > 255) {
            throw new ApiError(422, "评论内容不能超过 255 字");
        }
        if (data.parentId != null) {
            commentRepository.findById(data.parentId)
                    .orElseThrow(() -> ApiError.notFound("被回复的评论不存在"));
        }
        Comment comment = new Comment();
        comment.postId = data.postId;
        comment.parentId = data.parentId;
        comment.githubUserId = githubUser == null ? null : githubUser.id;
        comment.content = data.content.trim();
        comment.ip = ip == null ? "" : ip;
        comment.status = "pending";
        comment = commentRepository.save(comment);
        return toOut(comment, false);
    }

    @Transactional
    public CommentOut updateStatus(Long commentId, String status) {
        if (!"pending".equals(status) && !"approved".equals(status) && !"rejected".equals(status)) {
            throw new ApiError(422, "status 仅支持 pending/approved/rejected");
        }
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("评论不存在"));
        comment.status = status;
        comment = commentRepository.save(comment);
        return toOut(comment, false);
    }

    public CommentOut toggleLike(Long commentId, boolean unlike) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("评论不存在"));
        comment.likes = Math.max(0, comment.likes + (unlike ? -1 : 1));
        comment = commentRepository.save(comment);
        return toOut(comment, false);
    }

    @Transactional
    public Map<String, Object> delete(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ApiError.notFound("评论不存在"));
        commentRepository.delete(comment);
        return Map.of("ok", true);
    }

    // ---- 组装 ----

    private CommentOut toOut(Comment c, boolean includeIp) {
        GitHubUserOut gh = c.githubUserId == null ? null
                : githubUserRepository.findById(c.githubUserId)
                .map(u -> new GitHubUserOut(u.id, u.login, u.avatar, u.bio))
                .orElse(null);
        return new CommentOut(c.id, c.postId, c.parentId, c.content, c.likes, c.status,
                c.createdAt, gh, new ArrayList<>(), includeIp ? c.ip : null);
    }

    private CommentOut toOutRecursive(Comment c, boolean includeIp) {
        CommentOut out = toOut(c, includeIp);
        commentRepository.findByParentIdOrderByCreatedAtAsc(c.id).forEach(child ->
                out.replies().add(toOutRecursive(child, includeIp)));
        return out;
    }
}
