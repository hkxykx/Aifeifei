package com.fly.repository;

import com.fly.entity.SiteConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteConfigRepository extends JpaRepository<SiteConfig, Long> {
    Optional<SiteConfig> findByKey(String key);
}
