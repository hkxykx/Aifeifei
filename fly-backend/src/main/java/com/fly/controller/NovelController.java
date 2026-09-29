package com.fly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.service.NovelService;
import com.fly.security.RequiresAuth;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * reader3 协议兼容端点（小说源 biquga.com）。
 * 响应约定：{isSuccess, data?, errorMsg?}。
 * 书架为用户制：默认为空，搜索/详情页手动添加，可删除。
 */
@RestController
@RequestMapping("/reader3")
public class NovelController {

    private static final Logger log = LoggerFactory.getLogger(NovelController.class);
    private static final String ORIGIN_NAME = "笔趣阁";

    private final NovelService novel;
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    public NovelController(NovelService novel, JdbcTemplate jdbc) {
        this.novel = novel;
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS novel_progress (
                        book_url VARCHAR(128) PRIMARY KEY,
                        chapter_index INTEGER NOT NULL,
                        page_index INTEGER NOT NULL DEFAULT 0,
                        finished BOOLEAN NOT NULL DEFAULT FALSE,
                        updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )""");
            // 旧库补齐（注册/登录功能已关闭，进度标签统一记全局）
            jdbc.execute("ALTER TABLE novel_progress ADD COLUMN IF NOT EXISTS page_index INTEGER NOT NULL DEFAULT 0");
            jdbc.execute("ALTER TABLE novel_progress ADD COLUMN IF NOT EXISTS finished BOOLEAN NOT NULL DEFAULT FALSE");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS novel_bookshelf (
                        book_url VARCHAR(128) PRIMARY KEY,
                        name VARCHAR(255) NOT NULL DEFAULT '',
                        author VARCHAR(255) NOT NULL DEFAULT '',
                        cover_url VARCHAR(512) NOT NULL DEFAULT '',
                        intro TEXT,
                        added_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )""");
        } catch (Exception e) {
            log.error("novel: create tables failed", e);
        }
    }

    private static Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isSuccess", true);
        m.put("data", data);
        return m;
    }

    private static Map<String, Object> fail(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isSuccess", false);
        m.put("errorMsg", msg);
        return m;
    }

    private static String bookUrlOf(Object url) {
        String u = url == null ? "" : String.valueOf(url).trim();
        if (!NovelService.validBookUrl(u)) {
            throw new IllegalStateException("无效的书籍标识");
        }
        return u;
    }

    // ---------------- 书架（用户添加制） ----------------

    /** 书架整体结果缓存（30s）：防多端并发打开时反复触发上游章节解析 */
    private volatile Map<String, Object> shelfCache;
    private volatile long shelfCacheAt;

    /** 仅对最近阅读的前 N 本解析章节名（N+1 上游放大控制） */
    private static final int SHELF_TITLE_LIMIT = 5;

    @GetMapping("/getBookshelf")
    public Map<String, Object> getBookshelf() {
        try {
            Map<String, Object> cached = shelfCache;
            if (cached != null && System.currentTimeMillis() - shelfCacheAt < 30_000) {
                return cached;
            }
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT book_url, name, author, cover_url, intro, added_at FROM novel_bookshelf ORDER BY added_at DESC");

            // 进度标签：全局记录（章节 + 页码 + 是否看完），无需登录
            Map<String, Map<String, Object>> progress = new LinkedHashMap<>();
            jdbc.query("SELECT book_url, chapter_index, page_index, finished, updated_at FROM novel_progress",
                    rs -> {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("chapter", rs.getInt("chapter_index"));
                        p.put("page", rs.getInt("page_index"));
                        p.put("finished", rs.getBoolean("finished"));
                        p.put("at", String.valueOf(rs.getTimestamp("updated_at")));
                        progress.put(rs.getString("book_url"), p);
                    });

            List<Map<String, Object>> withProgress = new ArrayList<>();
            List<Map<String, Object>> withoutProgress = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> b = new LinkedHashMap<>();
                String url = String.valueOf(row.get("book_url"));
                b.put("bookUrl", url);
                b.put("name", row.get("name"));
                b.put("author", row.get("author"));
                b.put("coverUrl", row.get("cover_url"));
                b.put("intro", row.get("intro"));
                Map<String, Object> p = progress.get(url);
                if (p != null) {
                    int idx = (Integer) p.get("chapter");
                    b.put("durChapterIndex", idx);
                    b.put("pageIndex", p.get("page"));
                    b.put("finished", p.get("finished"));
                    b.put("progressAt", p.get("at"));
                    withProgress.add(b);
                } else {
                    withoutProgress.add(b);
                }
            }
            // 标签排第一：最近阅读（有进度）的书在最前，方便继续看书
            withProgress.sort((a, b) -> String.valueOf(b.get("progressAt")).compareTo(String.valueOf(a.get("progressAt"))));
            // 仅最近 N 本解析章节名：避免 1 次书架请求放大为 N 本 × 目录页数次上游抓取
            for (int i = 0; i < withProgress.size() && i < SHELF_TITLE_LIMIT; i++) {
                Map<String, Object> b = withProgress.get(i);
                String url = String.valueOf(b.get("bookUrl"));
                int idx = (Integer) b.get("durChapterIndex");
                try {
                    NovelService.ChapterListResult cl = novel.chapterList(url);
                    if (idx >= 0 && idx < cl.chapters.size()) {
                        b.put("durChapterTitle", cl.chapters.get(idx).title);
                        if (Boolean.TRUE.equals(b.get("finished"))) {
                            b.put("finishedChapterTitle", cl.chapters.get(cl.chapters.size() - 1).title);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            List<Map<String, Object>> books = new ArrayList<>(withProgress.size() + withoutProgress.size());
            books.addAll(withProgress);
            books.addAll(withoutProgress);
            Map<String, Object> result = ok(books);
            shelfCache = result;
            shelfCacheAt = System.currentTimeMillis();
            return result;
        } catch (Exception e) {
            return fail("获取书架失败: " + e.getMessage());
        }
    }

    @PostMapping("/addToBookshelf")
    @RequiresAuth
    public Map<String, Object> addToBookshelf(@RequestBody Map<String, Object> body) {
        try {
            String url = bookUrlOf(body.get("url"));
            com.fasterxml.jackson.databind.JsonNode info = novel.bookInfo(url);
            jdbc.update("""
                    INSERT INTO novel_bookshelf(book_url, name, author, cover_url, intro, added_at)
                    VALUES (?, ?, ?, ?, ?, NOW())
                    ON CONFLICT (book_url) DO UPDATE SET
                        name = EXCLUDED.name, author = EXCLUDED.author, cover_url = EXCLUDED.cover_url""",
                    url,
                    info.path("bookName").asText(""),
                    info.path("authorName").asText(""),
                    info.path("imgUrl").asText(""),
                    info.path("desc").asText(""));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bookUrl", url);
            data.put("name", info.path("bookName").asText(""));
            return ok(data);
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "加入书架失败" : e.getMessage());
        }
    }

    @PostMapping("/removeFromBookshelf")
    @RequiresAuth
    public Map<String, Object> removeFromBookshelf(@RequestBody Map<String, Object> body) {
        try {
            String url = bookUrlOf(body.get("url"));
            jdbc.update("DELETE FROM novel_bookshelf WHERE book_url = ?", url);
            jdbc.update("DELETE FROM novel_progress WHERE book_url = ?", url);
            return ok(true);
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "移除失败" : e.getMessage());
        }
    }

    // ---------------- 目录 ----------------

    @PostMapping("/getChapterList")
    public Map<String, Object> getChapterList(@RequestBody Map<String, Object> body) {
        try {
            String url = bookUrlOf(body.get("url"));
            NovelService.ChapterListResult cl = novel.chapterList(url);
            List<Map<String, Object>> chapters = new ArrayList<>(cl.chapters.size());
            for (int i = 0; i < cl.chapters.size(); i++) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("title", cl.chapters.get(i).title);
                c.put("index", i);
                chapters.add(c);
            }
            return ok(chapters);
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "获取目录失败" : e.getMessage());
        }
    }

    // ---------------- 正文 ----------------

    @PostMapping("/getBookContent")
    public Map<String, Object> getBookContent(@RequestBody Map<String, Object> body) {
        try {
            String url = bookUrlOf(body.get("url"));
            int index = body.get("index") instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(body.get("index")));
            return ok(novel.chapterContent(url, index));
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "获取内容失败" : e.getMessage());
        }
    }

    // ---------------- 进度 ----------------

    @PostMapping("/saveBookProgress")
    public Map<String, Object> saveBookProgress(@RequestBody Map<String, Object> body) {
        try {
            String url = bookUrlOf(body.get("url"));
            int index = body.get("index") instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(body.get("index")));
            int pageIndex = body.get("pageIndex") instanceof Number n ? n.intValue() : 0;
            boolean finished = Boolean.TRUE.equals(body.get("finished"));

            // 进度标签统一记全局（章节 + 页码 + 是否看完），无需登录
            jdbc.update("""
                    INSERT INTO novel_progress(book_url, chapter_index, page_index, finished, updated_at)
                    VALUES (?, ?, ?, ?, NOW())
                    ON CONFLICT (book_url) DO UPDATE SET
                        chapter_index = EXCLUDED.chapter_index,
                        page_index = EXCLUDED.page_index,
                        finished = EXCLUDED.finished,
                        updated_at = NOW()""",
                    url, index, pageIndex, finished);
            return ok(true);
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "保存进度失败" : e.getMessage());
        }
    }

    // ---------------- 搜索（SSE 流式 + 单源） ----------------

    @GetMapping(value = "/searchBookMultiSSE", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter searchBookMultiSSE(@RequestParam("key") String key,
                                         @RequestParam(defaultValue = "-1") int lastIndex,
                                         @RequestParam(defaultValue = "20") int searchSize) {
        SseEmitter emitter = new SseEmitter(60_000L);
        int offset = Math.max(lastIndex, 0);
        try {
            List<Map<String, Object>> books = offset <= 0 ? novel.search(key, 1, searchSize) : List.of();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("lastIndex", offset + books.size());
            payload.put("data", books);
            emitter.send(SseEmitter.event().data(om.writeValueAsString(payload)));
            emitter.send(SseEmitter.event().name("end").data("{}"));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("end").data("{}"));
            } catch (Exception ignore) {
            }
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping("/searchBookMulti")
    public Map<String, Object> searchBookMulti(@RequestBody Map<String, Object> body) {
        try {
            String key = String.valueOf(body.getOrDefault("key", ""));
            return ok(novel.search(key, 1, 20));
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "搜索失败" : e.getMessage());
        }
    }

    @PostMapping("/searchBook")
    public Map<String, Object> searchBook(@RequestBody Map<String, Object> body) {
        try {
            String key = String.valueOf(body.getOrDefault("key", ""));
            return ok(novel.search(key, 1, 20));
        } catch (Exception e) {
            return fail(e.getMessage() == null ? "搜索失败" : e.getMessage());
        }
    }

    @GetMapping("/getBookSources")
    public Map<String, Object> getBookSources(@RequestParam(required = false) String simple) {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("bookSourceName", ORIGIN_NAME);
        src.put("bookSourceUrl", "biquga");
        src.put("bookSourceGroup", "内置");
        src.put("enabled", true);
        src.put("header", "");
        return ok(List.of(src));
    }

    // ---------------- 封面代理 ----------------

    @GetMapping("/cover")
    public ResponseEntity<byte[]> cover(@RequestParam("path") String path) {
        try {
            java.net.URI uri = java.net.URI.create(path);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return ResponseEntity.badRequest().build();
            }
            if (!NovelService.allowedCoverHost(uri.getHost())) {
                return ResponseEntity.badRequest().build();
            }
            java.net.http.HttpResponse<byte[]> resp = novel.fetchCover(path);
            if (resp.statusCode() >= 400) {
                return ResponseEntity.status(resp.statusCode()).build();
            }
            String ct = resp.headers().firstValue("Content-Type").orElse("image/jpeg");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(ct))
                    .header("Cache-Control", "public, max-age=86400")
                    .body(resp.body());
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }
}
