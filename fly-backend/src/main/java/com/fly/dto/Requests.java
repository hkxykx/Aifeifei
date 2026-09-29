package com.fly.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求体模型。公共字段 + 初始默认值，语义与原 Pydantic Schema 一致：
 * 缺省字段取默认值，可空字段为 null 表示"未提供"（对应原 exclude_unset 行为）。
 */
public final class Requests {

    private Requests() {
    }

    public static class LoginReq {
        public String username;
        public String password;
    }

    /** 新增管理员（账户管理：仅用户名+密码） */
    public static class AdminUserCreateReq {
        public String username;
        public String password;
    }

    /** 修改管理员（字段为空表示不修改；密码留空=不改密码） */
    public static class AdminUserUpdateReq {
        public String username;
        public String password;
    }

    public static class PostCreateReq {
        public String title;
        public String slug;
        public String description = "";
        public String content = "";
        public String cover = "";
        public Long categoryId;
        public List<String> tags = new ArrayList<>();
        public String status = "draft";
        public boolean isPinned = false;
        public int readingTime = 0;
        public int wordCount = 0;
    }

    public static class PostUpdateReq {
        public String title;
        public String slug;
        public String description;
        public String content;
        public String cover;
        /** 用 setter 区分"未提供"与"显式置空"（category_id: null 表示清除分类） */
        private Long categoryId;
        private boolean categoryIdSet;
        public List<String> tags;
        public String status;
        public Boolean isPinned;
        public Integer readingTime;
        public Integer wordCount;

        public Long getCategoryId() {
            return categoryId;
        }

        public boolean isCategoryIdSet() {
            return categoryIdSet;
        }

        @JsonSetter("category_id")
        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
            this.categoryIdSet = true;
        }
    }

    public static class CategoryCreateReq {
        public String name;
        public String slug;
        public String description = "";
        public int sort = 0;
    }

    public static class CategoryUpdateReq {
        public String name;
        public String slug;
        public String description;
        public Integer sort;
    }

    public static class TagCreateReq {
        public String name;
        public String slug;
    }

    public static class TagUpdateReq {
        public String name;
        public String slug;
    }

    public static class CommentCreateReq {
        public Long postId;
        public Long parentId;
        public String content;
    }

    public static class StatusReq {
        public String status;
    }

    public static class MessageCreateReq {
        public String content;
        public Long parentId;
    }

    public static class ChatterCreateReq {
        public String content;
        public List<String> images = new ArrayList<>();
        public String mood = "";
        public String status = "draft";
    }

    public static class ChatterUpdateReq {
        public String content;
        public List<String> images;
        public String mood;
        public String status;
    }

    public static class ChatterCommentCreateReq {
        public Long chatterId;
        public Long parentId;
        public String content;
    }

    public static class AlbumCreateReq {
        public String title;
        public String description = "";
        public String cover = "";
        public int sort = 0;
    }

    public static class AlbumUpdateReq {
        public String title;
        public String description;
        public String cover;
        public Integer sort;
    }

    public static class PhotoCreateReq {
        public Long albumId;
        public String url;
        public String caption = "";
        public String orientation = "landscape";
        public int sort = 0;
    }

    public static class ProjectCreateReq {
        public String name;
        public String slug;
        public String description = "";
        public String longDescription = "";
        public String coverImage = "";
        public List<String> techStack = new ArrayList<>();
        public String linkGithub = "";
        public String linkGitee = "";
        public String linkLive = "";
        public String linkDocs = "";
        public String status = "developing";
        public String statusLabel = "";
        public boolean isFeatured = false;
        public int sort = 0;
    }

    public static class ProjectUpdateReq {
        public String name;
        public String slug;
        public String description;
        public String longDescription;
        public String coverImage;
        public List<String> techStack;
        public String linkGithub;
        public String linkGitee;
        public String linkLive;
        public String linkDocs;
        public String status;
        public String statusLabel;
        public Boolean isFeatured;
        public Integer sort;
    }

    public static class FriendLinkCreateReq {
        public String name;
        public String url;
        public String avatar = "";
        public String description = "";
        public int sort = 0;
    }

    public static class FriendLinkUpdateReq {
        public String name;
        public String url;
        public String avatar;
        public String description;
        public Integer sort;
        public Boolean isApproved;
    }

    public static class SiteConfigCreateReq {
        public String key;
        public String value = "";
        public String description = "";
    }

    public static class SiteConfigUpdateReq {
        public String value;
        public String description = "";
    }

    public static class BookmarkCategoryCreateReq {
        public String name;
        public String icon = "";
        public String description = "";
        public int sort = 0;
    }

    public static class BookmarkCategoryUpdateReq {
        public String name;
        public String icon;
        public String description;
        public Integer sort;
    }

    public static class BookmarkSiteCreateReq {
        public Long categoryId;
        public String name;
        public String url;
        public String icon = "";
        public String description = "";
        public List<String> platforms = new ArrayList<>();
        public int sort = 0;
    }

    public static class BookmarkSiteUpdateReq {
        public Long categoryId;
        public String name;
        public String url;
        public String icon;
        public String description;
        public List<String> platforms;
        public Integer sort;
    }
}
