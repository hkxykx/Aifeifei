package com.fly.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.fly.common.ApiError;
import com.fly.config.FlyProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传（对应原 app/api/upload.py）：校验 → 判断方向 → 上传阿里云 OSS；
 * OSS 未配置时保存到本地 uploads/ 目录。
 */
@Service
public class UploadService {

    private static final long MAX_SIZE = 10 * 1024 * 1024;

    private final FlyProperties properties;

    public UploadService(FlyProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> uploadImage(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw ApiError.badRequest("读取文件失败");
        }
        if (content.length == 0) {
            throw ApiError.badRequest("文件为空");
        }
        if (content.length > MAX_SIZE) {
            throw ApiError.badRequest("文件大小不能超过 10MB");
        }

        // 魔数校验：只信任文件内容，不信任客户端 Content-Type / 文件名（两者均可伪造）
        String ext = detectImageExt(content);
        if (ext == null) {
            throw ApiError.badRequest("文件内容不是受支持的图片格式（jpg/png/gif/webp）");
        }

        // 检测方向（与原实现一致：宽度 >= 高度为横向）
        String orientation = "landscape";
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(content));
            if (img != null) {
                orientation = img.getWidth() >= img.getHeight() ? "landscape" : "portrait";
            }
        } catch (Exception ignored) {
            // 无法解析的格式（如 WebP）时保持默认
        }

        // 生成文件名：扩展名取自内容检测结果（而非上传者提供的文件名），
        // 杜绝伪装成图片的 .html/.svg/.php 等可执行或脚本类型落盘
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        FlyProperties.Oss oss = properties.getOss();
        boolean ossConfigured = isNotBlank(oss.getEndpoint())
                && isNotBlank(oss.getAccessKeyId())
                && isNotBlank(oss.getAccessKeySecret())
                && isNotBlank(oss.getBucketName());

        String url;
        if (ossConfigured) {
            // 上传到 OSS
            String objectName = oss.getPrefix() + name;
            OSS client = new OSSClientBuilder().build(
                    oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
            try {
                client.putObject(oss.getBucketName(), objectName, new ByteArrayInputStream(content));
            } catch (Exception e) {
                throw new ApiError(500, "OSS 上传失败: " + e.getMessage());
            } finally {
                client.shutdown();
            }
            url = oss.getCustomDomain() + "/" + objectName;
        } else {
            // OSS 未配置：保存到本地 uploads/ 目录（由 /uploads/** 静态资源提供访问）
            Path dir = Paths.get(properties.getUploadsDir()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(dir);
                Files.write(dir.resolve(name), content);
            } catch (Exception e) {
                throw new ApiError(500, "保存文件失败: " + e.getMessage());
            }
            url = "/uploads/" + name;
        }
        return Map.of("url", url, "orientation", orientation);
    }

    private static final Set<String> ALLOWED_AUDIO_EXT = Set.of("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma");
    private static final long MAX_AUDIO_SIZE = 100L * 1024 * 1024;

    /**
     * 上传音频（管理后台音乐库）：仅本地保存到 uploads/music/，
     * 返回 {url: "/uploads/music/xxx"}；音频不走 OSS。
     */
    public Map<String, Object> uploadAudio(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        String contentType = file.getContentType();
        boolean typeOk = contentType != null && contentType.startsWith("audio/");
        if (!typeOk && !ALLOWED_AUDIO_EXT.contains(ext)) {
            throw ApiError.badRequest("不支持的音频类型: " + (contentType != null ? contentType : ext));
        }
        if (!ALLOWED_AUDIO_EXT.contains(ext)) {
            throw ApiError.badRequest("不支持的音频格式: ." + ext + "（支持 " + String.join("/", ALLOWED_AUDIO_EXT) + "）");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw ApiError.badRequest("读取文件失败");
        }
        if (content.length > MAX_AUDIO_SIZE) {
            throw ApiError.badRequest("音频大小不能超过 100MB");
        }
        if (content.length == 0) {
            throw ApiError.badRequest("音频文件为空");
        }

        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(properties.getUploadsDir()).toAbsolutePath().normalize().resolve("music");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), content);
        } catch (Exception e) {
            throw new ApiError(500, "保存音频失败: " + e.getMessage());
        }
        return Map.of("url", "/uploads/music/" + name);
    }

    /** 删除本地上传文件（仅限 /uploads/ 前缀，防路径穿越；OSS 文件跳过） */
    public void deleteLocalUpload(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        Path base = Paths.get(properties.getUploadsDir()).toAbsolutePath().normalize();
        Path target = base.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(base)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (Exception ignored) {
            // 文件不存在或被占用时忽略，数据库记录仍会删除
        }
    }

    /**
     * 按文件头魔数识别图片类型，返回安全扩展名；无法识别返回 null。
     * SVG 不在允许范围内（内联打开可执行脚本，存在存储型 XSS 风险）。
     */
    private static String detectImageExt(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
