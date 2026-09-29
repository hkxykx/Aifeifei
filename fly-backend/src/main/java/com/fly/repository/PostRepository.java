package com.fly.repository;

import com.fly.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    Optional<Post> findBySlug(String slug);

    @Modifying
    @Query("update Post p set p.categoryId = null where p.categoryId = :categoryId")
    void clearCategoryRef(@Param("categoryId") Long categoryId);
}
