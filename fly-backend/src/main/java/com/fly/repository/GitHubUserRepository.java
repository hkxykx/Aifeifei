package com.fly.repository;

import com.fly.entity.GitHubUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubUserRepository extends JpaRepository<GitHubUser, Long> {
    Optional<GitHubUser> findByGithubId(Long githubId);
}
