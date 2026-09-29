package com.fly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "fly")
public class FlyProperties {

    /** JWT 签名密钥 */
    private String secretKey = "";
    /** JWT 有效期（小时） */
    private int tokenExpireHours = 72;
    /** CORS 允许来源，逗号分隔 */
    private String corsOrigins = "http://localhost:3000";
    /** 本地上传目录 */
    private String uploadsDir = "uploads";
    /** 管理后台构建产物目录 */
    private String adminDist = "admin/dist";
    /** 本地音乐目录（分号分隔，扫描导入用） */
    private String musicDirs = "";
    private final Oss oss = new Oss();

    public List<String> corsOriginList() {
        List<String> result = new ArrayList<>();
        for (String s : corsOrigins.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getTokenExpireHours() {
        return tokenExpireHours;
    }

    public void setTokenExpireHours(int tokenExpireHours) {
        this.tokenExpireHours = tokenExpireHours;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public String getUploadsDir() {
        return uploadsDir;
    }

    public void setUploadsDir(String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    public String getAdminDist() {
        return adminDist;
    }

    public void setAdminDist(String adminDist) {
        this.adminDist = adminDist;
    }

    public String getMusicDirs() {
        return musicDirs;
    }

    public void setMusicDirs(String musicDirs) {
        this.musicDirs = musicDirs;
    }

    /** 解析为规范化后的目录列表（跳过空项） */
    public List<java.nio.file.Path> musicDirList() {
        List<java.nio.file.Path> result = new ArrayList<>();
        if (musicDirs == null || musicDirs.isBlank()) {
            return result;
        }
        for (String s : musicDirs.split("[;；]")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(java.nio.file.Paths.get(trimmed).toAbsolutePath().normalize());
            }
        }
        return result;
    }

    public Oss getOss() {
        return oss;
    }

    public static class Oss {
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String bucketName = "";
        private String endpoint = "";
        private String customDomain = "";
        private String prefix = "Boke/";

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getCustomDomain() {
            return customDomain;
        }

        public void setCustomDomain(String customDomain) {
            this.customDomain = customDomain;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}
