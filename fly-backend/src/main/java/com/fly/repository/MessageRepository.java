package com.fly.repository;

import com.fly.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {
    List<Message> findByParentIdAndStatusOrderByCreatedAtAsc(Long parentId, String status);
}
