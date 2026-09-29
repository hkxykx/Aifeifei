package com.fly.service;

import com.fly.entity.GitHubUser;
import com.fly.repository.GitHubUserRepository;
import com.fly.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * GitHub 登录态解析（OAuth 登录入口已移除，此处仅保留对历史令牌的可选解析，
 * 用于在请求仍携带旧令牌时识别评论/留言作者；无令牌一律返回 null）。
 */
@Service
public class GithubAuthService {

    private final GitHubUserRepository githubUserRepository;
    private final TokenService tokenService;

    public GithubAuthService(GitHubUserRepository githubUserRepository,
                             TokenService tokenService) {
        this.githubUserRepository = githubUserRepository;
        this.tokenService = tokenService;
    }

    /** 可选解析：未登录/令牌无效/非 GitHub 令牌均返回 null */
    public GitHubUser getGithubUserOptional(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        var claims = tokenService.parseQuietly(auth.substring(7));
        if (claims == null) {
            return null;
        }
        try {
            return githubUserRepository.findById(Long.parseLong(claims.getSubject())).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
