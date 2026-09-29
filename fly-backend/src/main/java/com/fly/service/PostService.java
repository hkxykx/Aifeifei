package com.fly.service;

import com.fly.common.ApiError;
import com.fly.dto.Requests.PostCreateReq;
import com.fly.dto.Requests.PostUpdateReq;
import com.fly.dto.Responses.PostDetail;
import com.fly.dto.Responses.PostOut;
import com.fly.entity.Category;
import com.fly.entity.Post;
import com.fly.entity.PostTag;
import com.fly.entity.Tag;
import com.fly.repository.CategoryRepository;
import com.fly.repository.CommentRepository;
import com.fly.repository.PostRepository;
import com.fly.repository.PostTagRepository;
import com.fly.repository.TagRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文章（对应原 app/services/post_service.py）。
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final CommentRepository commentRepository;

    public PostService(PostRepository postRepository, CategoryRepository categoryRepository,
                       TagRepository tagRepository, PostTagRepository postTagRepository,
                       CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.postTagRepository = postTagRepository;
        this.commentRepository = commentRepository;
    }

    public List<PostOut> getPosts(String status, String category, String tag, int page, int size) {
        Long categoryId = null;
        if (category != null && !category.isEmpty()) {
            categoryId = categoryRepository.findBySlug(category).map(c -> c.id).orElse(null);
        }
        Long tagId = null;
        if (tag != null && !tag.isEmpty()) {
            tagId = tagRepository.findBySlug(tag).map(t -> t.id).orElse(null);
        }
        Long finalCategoryId = categoryId;
        Long finalTagId = tagId;
        Specification<Post> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null && !status.isEmpty()) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (finalCategoryId != null) {
                preds.add(cb.equal(root.get("categoryId"), finalCategoryId));
            }
            if (finalTagId != null) {
                preds.add(root.get("id").in(
                        postTagRepository.findByTagId(finalTagId).stream().map(pt -> pt.postId).toList()));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("createdAt")));
        return postRepository.findAll(spec, pageable).getContent().stream()
                .map(this::toOut)
                .toList();
    }

    public long countPosts(String status) {
        if (status == null || status.isEmpty()) {
            return postRepository.count();
        }
        return postRepository.count((root, query, cb) -> cb.equal(root.get("status"), status));
    }

    public PostDetail getBySlug(String slug, boolean isAdmin) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> ApiError.notFound("文章不存在"));
        // 非管理员访问未发布文章 → 404（不暴露存在性）
        if (!isAdmin && !"published".equals(post.status)) {
            throw ApiError.notFound("文章不存在");
        }
        post.views += 1;
        postRepository.save(post);
        return toDetail(post);
    }

    public PostDetail getById(Long postId, boolean isAdmin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiError.notFound("文章不存在"));
        if (!isAdmin && !"published".equals(post.status)) {
            throw ApiError.notFound("文章不存在");
        }
        return toDetail(post);
    }

    @Transactional
    public PostOut create(PostCreateReq data) {
        if (data.title == null || data.slug == null) {
            throw new ApiError(422, "title 和 slug 为必填字段");
        }
        Post post = new Post();
        post.title = data.title;
        post.slug = data.slug;
        post.description = data.description == null ? "" : data.description;
        post.content = data.content == null ? "" : data.content;
        post.cover = data.cover == null ? "" : data.cover;
        post.categoryId = data.categoryId;
        post.status = data.status == null ? "draft" : data.status;
        post.isPinned = data.isPinned;
        post.wordCount = data.wordCount;
        post.readingTime = data.readingTime;

        if (post.content != null && !post.content.isEmpty()) {
            if (post.wordCount == 0) {
                post.wordCount = post.content.length();
            }
            if (post.readingTime == 0) {
                post.readingTime = Math.max(1, post.wordCount / 300);
            }
        }
        if ("published".equals(post.status) && post.publishedAt == null) {
            post.publishedAt = LocalDateTime.now();
        }
        post = postRepository.save(post);

        if (data.tags != null && !data.tags.isEmpty()) {
            syncTags(post.id, data.tags);
        }
        if (post.categoryId != null) {
            updateCategoryCount(post.categoryId);
        }
        return toOut(post);
    }

    @Transactional
    public PostOut update(Long postId, PostUpdateReq data) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiError.notFound("文章不存在"));

        Long oldCategoryId = post.categoryId;

        if (data.title != null) {
            post.title = data.title;
        }
        if (data.slug != null) {
            post.slug = data.slug;
        }
        if (data.description != null) {
            post.description = data.description;
        }
        if (data.content != null) {
            post.content = data.content;
        }
        if (data.cover != null) {
            post.cover = data.cover;
        }
        if (data.isCategoryIdSet()) {
            post.categoryId = data.getCategoryId();
        }
        if (data.status != null) {
            post.status = data.status;
        }
        if (data.isPinned != null) {
            post.isPinned = data.isPinned;
        }

        if (data.wordCount != null) {
            post.wordCount = data.wordCount;
        }
        if (data.readingTime != null) {
            post.readingTime = data.readingTime;
        }
        if (post.content != null && !post.content.isEmpty()) {
            if (data.wordCount == null) {
                post.wordCount = post.content.length();
            }
            if (data.readingTime == null) {
                post.readingTime = Math.max(1, post.wordCount / 300);
            }
        }
        if ("published".equals(post.status) && post.publishedAt == null) {
            post.publishedAt = LocalDateTime.now();
        }
        post.updatedAt = LocalDateTime.now();
        post = postRepository.save(post);

        if (data.tags != null) {
            syncTags(post.id, data.tags);
        }
        updateCategoryCount(oldCategoryId);
        updateCategoryCount(post.categoryId);
        return toOut(post);
    }

    @Transactional
    public Map<String, Object> delete(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiError.notFound("文章不存在"));
        Long categoryId = post.categoryId;

        commentRepository.deleteByPostId(postId);
        postTagRepository.deleteAll(postTagRepository.findByPostId(postId));
        postRepository.delete(post);
        postRepository.flush();

        updateCategoryCount(categoryId);
        updateAllTagCounts();
        return Map.of("ok", true);
    }

    public Map<String, Object> toggleLike(Long postId, boolean unlike) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiError.notFound("文章不存在"));
        post.likes = Math.max(0, post.likes + (unlike ? -1 : 1));
        postRepository.save(post);
        return Map.of("likes", post.likes);
    }

    // ---- 内部逻辑 ----

    private void syncTags(Long postId, List<String> tagNames) {
        postTagRepository.deleteAll(postTagRepository.findByPostId(postId));
        for (String raw : tagNames) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            Tag tag = tagRepository.findByName(name).orElse(null);
            if (tag == null) {
                tag = new Tag();
                tag.name = name;
                tag.slug = name.toLowerCase().replace(" ", "-");
                tag = tagRepository.save(tag);
            }
            postTagRepository.save(new PostTag(postId, tag.id));
        }
        updateAllTagCounts();
    }

    private void updateAllTagCounts() {
        for (Tag tag : tagRepository.findAll()) {
            tag.postCount = (int) postTagRepository.countByTagId(tag.id);
            tagRepository.save(tag);
        }
    }

    private void updateCategoryCount(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        categoryRepository.findById(categoryId).ifPresent(cat -> {
            cat.postCount = (int) postRepository.count(
                    (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
            categoryRepository.save(cat);
        });
    }

    private PostOut toOut(Post post) {
        return new PostOut(post.id, post.title, post.slug, post.description, post.cover,
                categoryName(post.categoryId), tagNames(post.id), post.status, post.isPinned,
                post.views, post.likes, post.wordCount, post.readingTime,
                post.publishedAt, post.createdAt, post.updatedAt);
    }

    private PostDetail toDetail(Post post) {
        return new PostDetail(post.id, post.title, post.slug, post.description, post.content,
                post.cover, categoryName(post.categoryId), tagNames(post.id), post.status,
                post.isPinned, post.views, post.likes, post.wordCount, post.readingTime,
                post.publishedAt, post.createdAt, post.updatedAt);
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return "";
        }
        return categoryRepository.findById(categoryId).map(c -> c.name).orElse("");
    }

    private List<String> tagNames(Long postId) {
        List<String> names = new ArrayList<>();
        for (PostTag pt : postTagRepository.findByPostId(postId)) {
            tagRepository.findById(pt.tagId).ifPresent(t -> names.add(t.name));
        }
        return names;
    }
}
