package com.fly.service;

import com.fly.common.ApiError;
import com.fly.common.PageParams;
import com.fly.dto.Requests.MessageCreateReq;
import com.fly.dto.Responses.GitHubUserOut;
import com.fly.dto.Responses.MessageOut;
import com.fly.entity.GitHubUser;
import com.fly.entity.Message;
import com.fly.repository.GitHubUserRepository;
import com.fly.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 留言板（对应原 app/services/message_service.py）。
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final GitHubUserRepository githubUserRepository;

    public MessageService(MessageRepository messageRepository,
                          GitHubUserRepository githubUserRepository) {
        this.messageRepository = messageRepository;
        this.githubUserRepository = githubUserRepository;
    }

    /** 留言列表（仅顶层，嵌套回复只含已审核）；公开接口不返回访客 IP */
    public List<MessageOut> list(String status, Integer page, Integer size) {
        return list(status, page, size, false);
    }

    /** 留言列表，includeIp=true 时返回 IP（仅限管理员接口） */
    public List<MessageOut> list(String status, Integer page, Integer size, boolean includeIp) {
        var pageable = PageRequest.of(PageParams.page(page) - 1, PageParams.size(size, 20, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return messageRepository.findAll(topLevelSpec(status), pageable)
                .getContent().stream().map(m -> toOut(m, includeIp)).toList();
    }

    /** 顶层数量 */
    public long count(String status) {
        return messageRepository.count(topLevelSpec(status));
    }

    /** 留言提交：匿名可提交，默认 pending，站长在后台审核通过后才公开展示 */
    @Transactional
    public MessageOut create(MessageCreateReq data, GitHubUser githubUser, String ip) {
        if (data == null || data.content == null || data.content.isBlank()) {
            throw new ApiError(422, "content 为必填字段");
        }
        if (data.content.trim().length() > 255) {
            throw new ApiError(422, "留言内容不能超过 255 字");
        }
        if (data.parentId != null) {
            messageRepository.findById(data.parentId)
                    .orElseThrow(() -> ApiError.notFound("被回复的留言不存在"));
        }
        Message msg = new Message();
        msg.githubUserId = githubUser == null ? null : githubUser.id;
        msg.parentId = data.parentId;
        msg.content = data.content.trim();
        msg.ip = ip == null ? "" : ip;
        msg.status = "pending";
        msg = messageRepository.save(msg);
        return toOut(msg);
    }

    @Transactional
    public MessageOut updateStatus(Long msgId, String status) {
        if (!"pending".equals(status) && !"approved".equals(status) && !"rejected".equals(status)) {
            throw new ApiError(422, "status 仅支持 pending/approved/rejected");
        }
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> ApiError.notFound("留言不存在"));
        msg.status = status;
        msg = messageRepository.save(msg);
        return toOut(msg);
    }

    @Transactional
    public Map<String, Object> delete(Long msgId) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> ApiError.notFound("留言不存在"));
        messageRepository.delete(msg);
        return Map.of("ok", true);
    }

    public MessageOut toggleLike(Long msgId, boolean unlike) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> ApiError.notFound("留言不存在"));
        msg.likes = Math.max(0, msg.likes + (unlike ? -1 : 1));
        msg = messageRepository.save(msg);
        return toOut(msg);
    }

    private Specification<Message> topLevelSpec(String status) {
        return (root, query, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.isNull(root.get("parentId")));
            if (status != null && !status.isEmpty()) {
                preds.add(cb.equal(root.get("status"), status));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private MessageOut toOut(Message msg) {
        return toOut(msg, false);
    }

    /** includeIp=false 时 IP 置空，避免访客 IP 泄露给公开接口 */
    private MessageOut toOut(Message msg, boolean includeIp) {
        GitHubUserOut gh = msg.githubUserId == null ? null
                : githubUserRepository.findById(msg.githubUserId)
                .map(u -> new GitHubUserOut(u.id, u.login, u.avatar, u.bio))
                .orElse(null);
        List<MessageOut> replies = messageRepository
                .findByParentIdAndStatusOrderByCreatedAtAsc(msg.id, "approved")
                .stream().map(m -> toOut(m, includeIp)).toList();
        return new MessageOut(msg.id, msg.githubUserId, msg.parentId, msg.content,
                includeIp ? msg.ip : null,
                msg.status, msg.likes, msg.createdAt, gh, replies);
    }
}
