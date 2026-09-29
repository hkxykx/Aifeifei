package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.config.FlyProperties;
import com.fly.entity.Music;
import com.fly.repository.MusicRepository;
import com.fly.security.RequiresAuth;
import com.fly.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 本地音乐：三种来源合并进播放列表——
 * 1. 平台歌单（Next.js /api/music，Meting）；
 * 2. 本地目录扫描导入（fly.music-dirs 配置，登记路径不复制文件，/api/music/stream 流式播放）；
 * 3. 管理后台上传（复制到 uploads/music/）。
 */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private static final Set<String> AUDIO_EXT = Set.of("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma");

    private final MusicRepository musicRepository;
    private final UploadService uploadService;
    private final FlyProperties properties;
    private final com.fly.service.MusicMatchService matchService;

    public MusicController(MusicRepository musicRepository, UploadService uploadService,
                           FlyProperties properties,
                           com.fly.service.MusicMatchService matchService) {
        this.musicRepository = musicRepository;
        this.uploadService = uploadService;
        this.properties = properties;
        this.matchService = matchService;
    }

    /** 距上次自动扫描的最小间隔（毫秒），防止频繁遍历目录 */
    private static final long AUTO_SCAN_INTERVAL_MS = 30_000;
    private volatile long lastAutoScanAt = 0;

    /**
     * 本地音乐列表（公开，供前端播放器合并展示）。
     * 每次获取时自动增量扫描配置的音乐目录（30 秒节流），
     * 新放入目录的歌曲无需任何手动操作即可出现在播放列表。
     */
    @GetMapping("/local")
    public List<Map<String, Object>> local() {
        autoScan();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Music m : musicRepository.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(m.id));
            item.put("title", m.title);
            item.put("artist", m.artist);
            item.put("album", m.album);
            item.put("cover", m.cover);
            item.put("src", m.url);
            item.put("source", m.source);
            if (m.lrc != null && !m.lrc.isBlank()) {
                item.put("lrcUrl", "/api/music/lrc/" + m.id);
            }
            item.put("duration", m.duration);
            item.put("created_at", m.createdAt);
            result.add(item);
        }
        return result;
    }

    /**
     * 扫描配置的本地音乐目录并登记进数据库（不复制文件）。
     * 已登记的路径跳过；目录未配置/不存在时返回提示。
     */
    @PostMapping("/scan")
    @RequiresAuth
    public Map<String, Object> scan() {
        lastAutoScanAt = System.currentTimeMillis();
        return doScan();
    }

    /** 自动扫描（节流 + 异常兜底，不影响列表返回） */
    private void autoScan() {
        long now = System.currentTimeMillis();
        if (now - lastAutoScanAt < AUTO_SCAN_INTERVAL_MS) {
            return;
        }
        lastAutoScanAt = now;
        try {
            doScan();
        } catch (Exception ignored) {
            // 目录不存在或不可读时静默跳过
        }
    }

    private Map<String, Object> doScan() {
        List<Path> dirs = properties.musicDirList();
        if (dirs.isEmpty()) {
            return Map.of("added", 0, "skipped", 0, "dirs", 0,
                    "message", "未配置音乐目录（fly.music-dirs）");
        }
        Set<String> existing = new HashSet<>();
        for (Music m : musicRepository.findAll()) {
            if (m.path != null && !m.path.isBlank()) {
                existing.add(m.path);
            }
        }
        int added = 0;
        int skipped = 0;
        int foundDirs = 0;
        List<String> missing = new ArrayList<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                missing.add(dir.toString());
                continue;
            }
            foundDirs++;
            try (Stream<Path> walk = Files.walk(dir)) {
                List<Path> files = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> AUDIO_EXT.contains(ext(p)))
                        .sorted()
                        .toList();
                for (Path file : files) {
                    String abs = file.toAbsolutePath().normalize().toString();
                    if (existing.contains(abs)) {
                        skipped++;
                        continue;
                    }
                    Music m = new Music();
                    String name = file.getFileName().toString();
                    m.title = cap(name.substring(0, name.lastIndexOf('.') > 0 ? name.lastIndexOf('.') : name.length()));
                    m.artist = "本地目录";
                    m.path = abs;
                    m.source = "dir";
                    m.url = "/api/music/stream?path=" + URLEncoder.encode(abs, StandardCharsets.UTF_8);
                    musicRepository.save(m);
                    existing.add(abs);
                    added++;
                }
            } catch (IOException e) {
                throw new ApiError(500, "扫描目录失败: " + dir + " — " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("skipped", skipped);
        result.put("dirs", foundDirs);
        result.put("missing", missing);
        return result;
    }

    /**
     * 为本地音乐匹配平台封面与歌词（网易云 → QQ音乐兜底），
     * 结果缓存进数据库；只读平台接口，不改动本地音乐文件。
     */
    @GetMapping("/match/{id:\\d+}")
    public Map<String, Object> match(@PathVariable Long id) {
        return com.fly.service.MusicMatchService.toMap(matchService.match(id));
    }

    /** 已缓存的 LRC 歌词（文本） */
    @GetMapping("/lrc/{id:\\d+}")
    public ResponseEntity<String> lrc(@PathVariable Long id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> ApiError.notFound("音乐不存在"));
        String lrc = music.lrc == null ? "" : music.lrc;
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .body(lrc);
    }

    /**
     * 流式播放本地目录音乐（支持 HTTP Range，浏览器可拖动进度条）。
     * 仅允许访问 fly.music-dirs 配置内的音频文件。
     */
    @GetMapping("/stream")
    public void stream(@RequestParam("path") String path,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        Path file = resolveAllowed(path);
        if (file == null || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        long size = Files.size(file);
        String ext = ext(file);
        response.setContentType(contentTypeOf(ext));
        response.setHeader("Accept-Ranges", "bytes");

        long start = 0;
        long end = size - 1;
        int status = 200;
        String range = request.getHeader("Range");
        if (range != null && range.startsWith("bytes=") && size > 0) {
            String spec = range.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            if (dash >= 0) {
                try {
                    String startStr = spec.substring(0, dash).trim();
                    String endStr = spec.substring(dash + 1).trim();
                    if (startStr.isEmpty()) {
                        // 后缀范围 bytes=-N（最后 N 字节）
                        long suffix = Long.parseLong(endStr);
                        if (suffix > 0) {
                            start = Math.max(0, size - suffix);
                        }
                    } else {
                        start = Long.parseLong(startStr);
                        if (!endStr.isEmpty()) {
                            end = Math.min(Long.parseLong(endStr), size - 1);
                        }
                    }
                    if (start >= 0 && start <= end) {
                        status = 206;
                    } else {
                        start = 0;
                        end = size - 1;
                    }
                } catch (NumberFormatException ignored) {
                    start = 0;
                    end = size - 1;
                    status = 200;
                }
            }
        }

        long length = end - start + 1;
        response.setStatus(status);
        response.setContentLengthLong(length);
        if (status == 206) {
            response.setHeader("Content-Range",
                    "bytes " + start + "-" + end + "/" + size);
        }

        try (InputStream in = Files.newInputStream(file)) {
            long skipped = 0;
            while (skipped < start) {
                long n = in.skip(start - skipped);
                if (n <= 0) {
                    break;
                }
                skipped += n;
            }
            byte[] buf = new byte[64 * 1024];
            long remaining = length;
            var out = response.getOutputStream();
            while (remaining > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (r < 0) {
                    break;
                }
                out.write(buf, 0, r);
                remaining -= r;
            }
            out.flush();
        }
    }

    /** music 表 title/artist 列为 varchar(255)，超长截断避免保存被数据库拒绝 */
    private static String cap(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    /** 上传音频（管理后台），复制进 uploads/music/ */
    @PostMapping("/upload")
    @RequiresAuth
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "title", required = false) String title,
                                      @RequestParam(value = "artist", required = false) String artist) {
        Map<String, Object> uploaded = uploadService.uploadAudio(file);
        String url = String.valueOf(uploaded.get("url"));

        Music music = new Music();
        String original = file.getOriginalFilename();
        music.title = cap((title != null && !title.isBlank()) ? title.trim()
                : (original != null && original.contains(".")
                    ? original.substring(0, original.lastIndexOf('.'))
                    : "未知歌曲"));
        music.artist = cap((artist != null && !artist.isBlank()) ? artist.trim() : "未知歌手");
        music.url = url;
        music.source = "upload";
        music.path = "";
        Object duration = uploaded.get("duration");
        if (duration instanceof Number n) {
            music.duration = n.doubleValue();
        }
        music = musicRepository.save(music);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", String.valueOf(music.id));
        item.put("title", music.title);
        item.put("artist", music.artist);
        item.put("cover", music.cover);
        item.put("src", music.url);
        item.put("duration", music.duration);
        return item;
    }

    /** 编辑标题/歌手（管理后台） */
    @PutMapping("/{id:\\d+}")
    @RequiresAuth
    public Map<String, Object> update(@PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> ApiError.notFound("音乐不存在"));
        String title = body.get("title");
        String artist = body.get("artist");
        if (title != null && !title.isBlank()) {
            music.title = cap(title.trim());
        }
        if (artist != null && !artist.isBlank()) {
            music.artist = cap(artist.trim());
        }
        music = musicRepository.save(music);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", String.valueOf(music.id));
        item.put("title", music.title);
        item.put("artist", music.artist);
        item.put("src", music.url);
        item.put("duration", music.duration);
        return item;
    }

    /**
     * 删除（管理后台）：目录导入的只移除记录，保留源文件；
     * 上传的同时删除上传文件。
     */
    @DeleteMapping("/{id:\\d+}")
    @RequiresAuth
    public Map<String, Object> delete(@PathVariable Long id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> ApiError.notFound("音乐不存在"));
        boolean fromDir = "dir".equals(music.source);
        if (!fromDir) {
            uploadService.deleteLocalUpload(music.url);
        }
        musicRepository.delete(music);
        return Map.of("ok", true, "source_removed", fromDir);
    }

    /** 校验路径必须位于配置的音乐目录内，返回规范化路径（非法返回 null） */
    private Path resolveAllowed(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path target;
        try {
            target = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
        for (Path dir : properties.musicDirList()) {
            if (target.startsWith(dir)) {
                String ext = ext(target);
                if (AUDIO_EXT.contains(ext)) {
                    return target;
                }
                return null;
            }
        }
        return null;
    }

    private static String ext(Path p) {
        String name = p.getFileName().toString();
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String contentTypeOf(String ext) {
        return switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            case "wma" -> "audio/x-ms-wma";
            default -> "application/octet-stream";
        };
    }
}
