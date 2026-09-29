package com.fly.repository;

import com.fly.entity.BookmarkCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkCategoryRepository extends JpaRepository<BookmarkCategory, Long> {
}
