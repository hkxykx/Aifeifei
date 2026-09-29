package com.fly.repository;

import com.fly.entity.Chatter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChatterRepository extends JpaRepository<Chatter, Long>, JpaSpecificationExecutor<Chatter> {
}
