package com.fly.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 收藏站点 */
@Entity
@Table(name = "bookmark_site")
public class BookmarkSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long categoryId;
    public String name;
    public String url;
    public String icon = "";
    public String description = "";
    /** JSON 数组字符串，如 ["Windows","macOS"] */
    public String platforms = "[]";
    public int sort = 0;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
