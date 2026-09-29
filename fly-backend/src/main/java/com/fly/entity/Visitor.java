package com.fly.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 访客记录 */
@Entity
@Table(name = "visitor")
public class Visitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String ip;
    public String path = "";
    public String userAgent = "";
    public String city = "";
    public String region = "";
    public String country = "";
    public String district = "";
    public String org = "";
    public String asn = "";
    public boolean isMobile = false;
    public boolean isProxy = false;
    public boolean isHosting = false;
    public String browser = "";
    public String os = "";
    public String deviceType = "";
    public LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
