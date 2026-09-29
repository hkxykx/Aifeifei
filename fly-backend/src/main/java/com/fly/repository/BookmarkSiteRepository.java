package com.fly.repository;

import com.fly.entity.BookmarkSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkSiteRepository extends JpaRepository<BookmarkSite, Long> {
    List<BookmarkSite> findByCategoryIdOrderBySortAsc(Long categoryId);

    void deleteByCategoryId(Long categoryId);
}
