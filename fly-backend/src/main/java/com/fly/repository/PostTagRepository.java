package com.fly.repository;

import com.fly.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostTagRepository extends JpaRepository<PostTag, PostTag.PostTagId> {
    List<PostTag> findByPostId(Long postId);

    List<PostTag> findByTagId(Long tagId);

    long countByTagId(Long tagId);
}
