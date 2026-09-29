package com.fly.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.common.ApiError;
import com.fly.dto.Requests.ProjectCreateReq;
import com.fly.dto.Requests.ProjectUpdateReq;
import com.fly.dto.Responses.ProjectOut;
import com.fly.entity.Project;
import com.fly.repository.ProjectRepository;
import com.fly.security.RequiresAuth;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 项目展示（对应原 app/api/projects.py） */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public ProjectController(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<ProjectOut> list() {
        return projectRepository.findAll(Sort.by(Sort.Direction.ASC, "sort")).stream()
                .map(this::toOut)
                .toList();
    }

    @GetMapping("/{slug}")
    public ProjectOut getBySlug(@PathVariable String slug) {
        return projectRepository.findBySlug(slug)
                .map(this::toOut)
                .orElseThrow(() -> ApiError.notFound("项目不存在"));
    }

    @PostMapping
    @RequiresAuth
    public ProjectOut create(@RequestBody ProjectCreateReq data) {
        if (data.name == null || data.slug == null) {
            throw new ApiError(422, "name 和 slug 为必填字段");
        }
        Project p = new Project();
        p.name = data.name;
        p.slug = data.slug;
        p.description = emptyIfNull(data.description);
        p.longDescription = emptyIfNull(data.longDescription);
        p.coverImage = emptyIfNull(data.coverImage);
        p.techStack = toJson(data.techStack);
        p.linkGithub = emptyIfNull(data.linkGithub);
        p.linkGitee = emptyIfNull(data.linkGitee);
        p.linkLive = emptyIfNull(data.linkLive);
        p.linkDocs = emptyIfNull(data.linkDocs);
        p.status = data.status == null ? "developing" : data.status;
        p.statusLabel = emptyIfNull(data.statusLabel);
        p.isFeatured = data.isFeatured;
        p.sort = data.sort;
        return toOut(projectRepository.save(p));
    }

    @PutMapping("/{projectId}")
    @RequiresAuth
    public ProjectOut update(@PathVariable Long projectId, @RequestBody ProjectUpdateReq data) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiError.notFound("项目不存在"));
        if (data.name != null) {
            p.name = data.name;
        }
        if (data.slug != null) {
            p.slug = data.slug;
        }
        if (data.description != null) {
            p.description = data.description;
        }
        if (data.longDescription != null) {
            p.longDescription = data.longDescription;
        }
        if (data.coverImage != null) {
            p.coverImage = data.coverImage;
        }
        if (data.techStack != null) {
            p.techStack = toJson(data.techStack);
        }
        if (data.linkGithub != null) {
            p.linkGithub = data.linkGithub;
        }
        if (data.linkGitee != null) {
            p.linkGitee = data.linkGitee;
        }
        if (data.linkLive != null) {
            p.linkLive = data.linkLive;
        }
        if (data.linkDocs != null) {
            p.linkDocs = data.linkDocs;
        }
        if (data.status != null) {
            p.status = data.status;
        }
        if (data.statusLabel != null) {
            p.statusLabel = data.statusLabel;
        }
        if (data.isFeatured != null) {
            p.isFeatured = data.isFeatured;
        }
        if (data.sort != null) {
            p.sort = data.sort;
        }
        return toOut(projectRepository.save(p));
    }

    @DeleteMapping("/{projectId}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiError.notFound("项目不存在"));
        projectRepository.delete(p);
        return Map.of("ok", true);
    }

    private ProjectOut toOut(Project p) {
        return new ProjectOut(p.id, p.name, p.slug, p.description, p.longDescription,
                p.coverImage, parseTechStack(p.techStack), p.linkGithub, p.linkGitee,
                p.linkLive, p.linkDocs, p.status, p.statusLabel, p.isFeatured, p.sort,
                p.createdAt);
    }

    private List<String> parseTechStack(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
