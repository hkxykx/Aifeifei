package com.fly.repository;

import com.fly.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

    Optional<Visitor> findFirstByIpOrderByCreatedAtDesc(String ip);

    @Modifying
    @Query(value = "DELETE FROM visitor WHERE id IN "
            + "(SELECT id FROM visitor ORDER BY created_at ASC, id ASC LIMIT :excess)", nativeQuery = true)
    void deleteOldest(int excess);
}
