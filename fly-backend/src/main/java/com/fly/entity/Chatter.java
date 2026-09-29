package com.fly.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 说说/微语 */
@Entity
@Table(name = "chatter")
public class Chatter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(columnDefinition = "TEXT")
    public String content;
    /** 图片 URL 列表 JSON，易超 255，用 TEXT */
    @Column(columnDefinition = "TEXT")
    public String images = "[]";
    @Column(columnDefinition = "TEXT")
    public String mood = "";
    public int likes = 0;
    public int commentsCount = 0;
    public String status = "draft";
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
