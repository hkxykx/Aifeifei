package com.fly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 响应体模型（record），JSON 字段名经全局 SNAKE_CASE 策略输出，
 * 与原 FastAPI Pydantic 序列化结果一致。
 */
public final class Responses {

    private Responses() {
    }

    public record PostOut(
            Long id,
            String title,
            String slug,
            String description,
            String cover,
            String category,
            List<String> tags,
            String status,
            boolean isPinned,
            int views,
            int likes,
            int wordCount,
            int readingTime,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PostDetail(
            Long id,
            String title,
            String slug,
            String description,
            String content,
            String cover,
            String category,
            List<String> tags,
            String status,
            boolean isPinned,
            int views,
            int likes,
            int wordCount,
            int readingTime,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record GitHubUserOut(Long id, String login, String avatar, String bio) {
    }

    public record CommentOut(
            Long id,
            Long postId,
            Long parentId,
            String content,
            int likes,
            String status,
            LocalDateTime createdAt,
            GitHubUserOut githubUser,
            List<CommentOut> replies,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) String ip) {

        public static CommentOut empty() {
            return new CommentOut(null, null, null, null, 0, null, null, null, new ArrayList<>(), null);
        }
    }

    public record MessageOut(
            Long id,
            Long githubUserId,
            Long parentId,
            String content,
            String ip,
            String status,
            int likes,
            LocalDateTime createdAt,
            GitHubUserOut githubUser,
            List<MessageOut> replies) {
    }

    public record ChatterOut(
            Long id,
            String content,
            List<String> images,
            String mood,
            int likes,
            int commentsCount,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ChatterCommentOut(
            Long id,
            Long chatterId,
            Long parentId,
            String content,
            int likes,
            String status,
            LocalDateTime createdAt,
            GitHubUserOut githubUser,
            List<ChatterCommentOut> replies,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) String ip) {
    }

    public record BookmarkSiteOut(
            Long id,
            Long categoryId,
            String name,
            String url,
            String icon,
            String description,
            List<String> platforms,
            int sort,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record BookmarkFull(
            Long id,
            String name,
            String icon,
            String description,
            int sort,
            LocalDateTime createdAt,
            List<BookmarkSiteOut> sites) {
    }

    public record SiteConfigOut(Long id, String key, String value, String description) {
    }

    public record ProjectOut(
            Long id,
            String name,
            String slug,
            String description,
            String longDescription,
            String coverImage,
            List<String> techStack,
            String linkGithub,
            String linkGitee,
            String linkLive,
            String linkDocs,
            String status,
            String statusLabel,
            boolean isFeatured,
            int sort,
            LocalDateTime createdAt) {
    }

    public record SiteConfigRow(
            Long id,
            String key,
            String value,
            String description,
            LocalDateTime updatedAt) {
    }

    public record VisitorOut(
            Long id,
            String ip,
            String path,
            String city,
            String region,
            String country,
            String district,
            String org,
            String orgCn,
            String asn,
            boolean isMobile,
            boolean isProxy,
            boolean isHosting,
            String browser,
            String os,
            String deviceType,
            LocalDateTime createdAt) {
    }
}
