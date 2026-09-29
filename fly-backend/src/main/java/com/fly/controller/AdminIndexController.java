package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.config.FlyProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 管理后台 SPA：/admin 下文件存在则直接返回；带扩展名的缺失资源维持 404；
 * 无扩展名的深层路径（如直接访问/刷新子路由）返回 index.html，
 * 避免未命中静态资源时返回 JSON 404（对应原 FastAPI StaticFiles(html=True) 行为）。
 */
@RestController
public class AdminIndexController {

    private final FlyProperties properties;

    public AdminIndexController(FlyProperties properties) {
        this.properties = properties;
    }

    @GetMapping({"/admin", "/admin/"})
    public ResponseEntity<Resource> index() throws Exception {
        return indexHtml();
    }

    @GetMapping("/admin/**")
    public ResponseEntity<Resource> spa(HttpServletRequest request) throws Exception {
        Path dist = Paths.get(properties.getAdminDist()).toAbsolutePath().normalize();
        String uri = request.getRequestURI();
        String rel = uri.startsWith("/admin/") ? uri.substring("/admin/".length()) : "";
        if (!rel.isEmpty()) {
            rel = URLDecoder.decode(rel, StandardCharsets.UTF_8);
            Path target = dist.resolve(rel).normalize();
            if (target.startsWith(dist) && Files.isRegularFile(target)) {
                return ResponseEntity.ok()
                        .contentType(mediaTypeFor(target.getFileName().toString()))
                        .body(new UrlResource(target.toUri()));
            }
            int slash = rel.lastIndexOf('/');
            String lastSegment = rel.substring(slash + 1);
            if (lastSegment.contains(".")) {
                throw ApiError.notFound("Not Found");
            }
        }
        return indexHtml();
    }

    private ResponseEntity<Resource> indexHtml() throws Exception {
        Path indexPath = Paths.get(properties.getAdminDist(), "index.html").toAbsolutePath().normalize();
        if (!Files.isRegularFile(indexPath)) {
            throw ApiError.notFound("Not Found");
        }
        Resource resource = new UrlResource(indexPath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }

    private MediaType mediaTypeFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        return switch (ext) {
            case "html" -> MediaType.TEXT_HTML;
            case "js", "mjs" -> new MediaType("text", "javascript", StandardCharsets.UTF_8);
            case "css" -> new MediaType("text", "css", StandardCharsets.UTF_8);
            case "json", "map" -> MediaType.APPLICATION_JSON;
            case "svg" -> new MediaType("image", "svg+xml");
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "ico" -> new MediaType("image", "x-icon");
            case "woff" -> new MediaType("font", "woff");
            case "woff2" -> new MediaType("font", "woff2");
            case "ttf" -> new MediaType("font", "ttf");
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
