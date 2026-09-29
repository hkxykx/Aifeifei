package com.fly.repository;

import com.fly.entity.ChatterComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ChatterCommentRepository extends JpaRepository<ChatterComment, Long>,
        JpaSpecificationExecutor<ChatterComment> {
    List<ChatterComment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    List<ChatterComment> findByChatterIdAndStatusOrderByCreatedAtDesc(Long chatterId, String status);

    void deleteByChatterId(Long chatterId);
}
