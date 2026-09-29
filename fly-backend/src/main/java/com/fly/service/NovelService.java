package com.fly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小说源：biquga.com（免费站，无付费章）。
 * - 搜索：POST /search.html (s=关键词)
 * - 书籍页：/{bid}_{sub}/ （书名/作者/简介/封面）
 * - 目录：/{bid}_{sub}/dindex_N.html 倒序分页，onclick read_tz(章节ID)
 * - 正文：章节页内 qsbs.bb('base64') 多段，标准 base64+UTF-8 解码
 */
@Service
public class NovelService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(NovelService.class);

    private static final String BASE = "https://www.biquga.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern BOOK_URL = Pattern.compile("^/\\d+_\\d+/$");
    private static final Pattern DINDEX_MAX = Pattern.compile("dindex_(\\d+)\\.html");
    private static final Pattern READ_TZ = Pattern.compile("read_tz\\((\\d+)\\)");
    private static final Pattern BB_SEGMENT = Pattern.compile("qsbs\\.bb\\('([^']+)'\\)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(DurationHolder.CONNECT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper om = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    private final Map<String, ChapterListResult> chapterCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ChapterListResult> e) {
                    return size() > 512;
                }
            });
    private final Map<String, JsonNode> infoCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, JsonNode> e) {
                    return size() > 256;
                }
            });

    /** 目录抓取按 bookUrl 分段锁（64 条带），替代整方法 synchronized 造成的全局串行 */
    private final Object[] chapterLocks = new Object[64];

    {
        for (int i = 0; i < chapterLocks.length; i++) chapterLocks[i] = new Object();
    }

    private Object lockFor(String bookUrl) {
        return chapterLocks[(bookUrl.hashCode() & 0x7fffffff) % chapterLocks.length];
    }

    static final class DurationHolder {
        static final java.time.Duration CONNECT = java.time.Duration.ofSeconds(8);
        static final java.time.Duration REQUEST = java.time.Duration.ofSeconds(20);
    }

    public static class ChapterFlat {
        public final String title;
        public final String path;

        public ChapterFlat(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }

    public static class ChapterListResult {
        public final List<ChapterFlat> chapters;

        public ChapterListResult(List<ChapterFlat> chapters) {
            this.chapters = chapters;
        }
    }

    @Override
    public void afterPropertiesSet() {
        log.info("novel: biquga source initialized");
    }

    public static boolean validBookUrl(String url) {
        return url != null && BOOK_URL.matcher(url).matches();
    }

    // ---------------- HTTP ----------------

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Referer", BASE + "/")
                .timeout(DurationHolder.REQUEST)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private String postForm(String url, Map<String, String> form) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Referer", BASE + "/")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(DurationHolder.REQUEST)
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    // ---------------- 搜索 ----------------

    public List<Map<String, Object>> search(String key, int page, int size) {
        if (page > 1) {
            return List.of();
        }
        try {
            String html = postForm(BASE + "/search.html", Map.of("s", key));
            Document doc = Jsoup.parse(html, BASE);
            List<Map<String, Object>> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Element li : doc.select("li:has(span.s2)")) {
                Element a = li.selectFirst("span.s2 a");
                if (a == null) continue;
                String href = a.attr("href").trim();
                if (!validBookUrl(href) || !seen.add(href)) continue;
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("bookUrl", href);
                b.put("name", a.text().trim());
                Element author = li.selectFirst("span.s3 a");
                b.put("author", author != null ? author.text().trim() : "");
                b.put("coverUrl", coverOf(href));
                Element latest = li.selectFirst("span.s4 a");
                if (latest != null && !latest.text().trim().isEmpty()) {
                    b.put("latestChapterTitle", latest.text().trim());
                }
                b.put("intro", "");
                b.put("origin", "biquga");
                b.put("originName", "笔趣阁");
                out.add(b);
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("未找到相关小说");
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("搜索失败: " + e.getMessage());
        }
    }

    /** 由书籍路径 /2369_xxx/ 或 /2_2369/ 推导封面 /img/{sub}.jpg */
    public static String coverOf(String bookUrl) {
        Matcher m = Pattern.compile("^/(\\d+)_(\\d+)/$").matcher(bookUrl);
        if (m.matches()) {
            return BASE + "/img/" + m.group(2) + ".jpg";
        }
        return "";
    }

    // ---------------- 书籍信息 ----------------

    private static String absolute(String src) {
        if (src == null || src.isEmpty()) return "";
        if (src.startsWith("http")) return src;
        if (src.startsWith("//")) return "https:" + src;
        if (src.startsWith("/")) return BASE + src;
        return BASE + "/" + src;
    }

    public JsonNode bookInfo(String bookUrl) {
        if (!validBookUrl(bookUrl)) {
            throw new IllegalStateException("无效的书籍标识");
        }
        String key = "info:" + bookUrl;
        JsonNode cached = infoCache.get(key);
        if (cached != null) return cached;
        try {
            String html = get(BASE + bookUrl);
            Document doc = Jsoup.parse(html, BASE + bookUrl);
            String name = text(doc, "div.info h1", "h1");
            if (name.isEmpty()) {
                throw new IllegalStateException("书籍页面异常");
            }
            String author = text(doc, "p.p_author a", "p.p_author");
            if (author.startsWith("作")) {
                author = author.replaceFirst("^作\\s*者[：:]", "").trim();
            }
            String intro = text(doc, "div.desc", "div.intro", "#intro");
            String cover = "";
            Element byName = doc.selectFirst("img[alt=" + name + "]");
            if (byName != null) {
                cover = absolute(byName.attr("src"));
            }
            if (cover.isEmpty()) {
                Element img = doc.selectFirst("img[src^=/img/]");
                if (img != null) cover = absolute(img.attr("src"));
            }
            String latest = text(doc, "div.info .fix a[href$=.html]:last-of-type");
            String updated = "";
            for (Element p : doc.select("div.info p")) {
                String t = p.text();
                if (t.contains("更新")) {
                    updated = t.replaceFirst("^.*?更新[：:]", "").trim();
                    break;
                }
            }
            var node = om.createObjectNode();
            node.put("bookName", name);
            node.put("authorName", author);
            node.put("desc", intro);
            node.put("imgUrl", cover);
            node.put("lastChapter", latest);
            node.put("updated", updated);
            infoCache.put(key, node);
            return node;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("获取书籍信息失败: " + e.getMessage());
        }
    }

    private static String text(Document doc, String... selectors) {
        for (String s : selectors) {
            Element e = doc.selectFirst(s);
            if (e != null && !e.text().trim().isEmpty()) return e.text().trim();
        }
        return "";
    }

    // ---------------- 目录 ----------------

    public ChapterListResult chapterList(String bookUrl) {
        if (!validBookUrl(bookUrl)) {
            throw new IllegalStateException("无效的书籍标识");
        }
        String key = "cl:" + bookUrl;
        ChapterListResult cached = chapterCache.get(key);
        if (cached != null) return cached;

        synchronized (lockFor(bookUrl)) {
            cached = chapterCache.get(key);
            if (cached != null) return cached;

            Map<Long, ChapterFlat> byCid = new LinkedHashMap<>();
            int maxPage = 1;
            boolean dindexOk = false;
            try {
                String html1 = get(BASE + bookUrl + "dindex_1.html");
                dindexOk = true;
                parseDindex(html1, bookUrl, byCid);
                Matcher m = DINDEX_MAX.matcher(html1);
                while (m.find()) {
                    maxPage = Math.max(maxPage, Integer.parseInt(m.group(1)));
                }
            } catch (Exception e) {
                log.warn("novel: dindex_1 failed for {}: {}", bookUrl, e.getMessage());
            }

            if (dindexOk && maxPage > 1) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int n = 2; n <= maxPage; n++) {
                    final int page = n;
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            String html = get(BASE + bookUrl + "dindex_" + page + ".html");
                            synchronized (byCid) {
                                parseDindex(html, bookUrl, byCid);
                            }
                        } catch (Exception e) {
                            log.warn("novel: dindex_{} failed for {}: {}", page, bookUrl, e.getMessage());
                        }
                    }, pool));
                }
                try {
                    // 总超时：防单本书持锁分钟级（上游慢/被墙时整体挂死小说模块）
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .join();
                } catch (java.util.concurrent.CompletionException ex) {
                    log.warn("novel: chapter pages degraded for {} ({} chapters so far): {}",
                            bookUrl, byCid.size(), ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                    if (byCid.isEmpty()) {
                        throw new IllegalStateException("获取目录超时");
                    }
                }
            } else if (!dindexOk) {
                try {
                    String html = get(BASE + bookUrl);
                    Document doc = Jsoup.parse(html, BASE + bookUrl);
                    Pattern link = Pattern.compile(Pattern.quote(bookUrl) + "(\\d+)\\.html");
                    for (Element a : doc.select("a[href]")) {
                        String href = a.attr("href");
                        Matcher m = link.matcher(href);
                        if (m.find()) {
                            String title = a.text().trim();
                            if (title.equals("开始阅读") || title.equals("加入书签") || title.equals("新章节目录")) continue;
                            byCid.putIfAbsent(Long.parseLong(m.group(1)), new ChapterFlat(title, href));
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("获取目录失败: " + e.getMessage());
                }
            }

            if (byCid.isEmpty()) {
                throw new IllegalStateException("目录为空");
            }
            List<ChapterFlat> flat;
            synchronized (byCid) {
                flat = byCid.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .toList();
            }
            ChapterListResult r = new ChapterListResult(flat);
            chapterCache.put(key, r);
            return r;
        }
    }

    private static void parseDindex(String html, String bookUrl, Map<Long, ChapterFlat> into) {
        Document doc = Jsoup.parse(html, BASE + bookUrl);
        Elements items = doc.select("ul.section-list li a[onclick], .section-list li a[onclick]");
        for (Element a : items) {
            Matcher m = READ_TZ.matcher(a.attr("onclick"));
            if (!m.find()) continue;
            long cid = Long.parseLong(m.group(1));
            String title = a.text().trim();
            if (title.isEmpty()) continue;
            into.putIfAbsent(cid, new ChapterFlat(title, bookUrl + cid + ".html"));
        }
    }

    // ---------------- 正文 ----------------

    public String chapterContent(String bookUrl, int index) {
        ChapterListResult cl = chapterList(bookUrl);
        if (index < 0 || index >= cl.chapters.size()) {
            throw new IllegalStateException("章节序号无效");
        }
        ChapterFlat ch = cl.chapters.get(index);
        try {
            String html = get(BASE + ch.path);
            Matcher m = BB_SEGMENT.matcher(html);
            StringBuilder raw = new StringBuilder();
            int count = 0;
            while (m.find()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(m.group(1).getBytes(StandardCharsets.US_ASCII));
                    raw.append(new String(bytes, StandardCharsets.UTF_8)).append('\n');
                    count++;
                } catch (IllegalArgumentException ignore) {
                }
            }
            if (count == 0 || raw.isEmpty()) {
                throw new IllegalStateException("正文解析失败");
            }
            return cleanContent(raw.toString());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("获取内容失败: " + e.getMessage());
        }
    }

    /** 清洗：p/br→换行、剥标签、去广告行、还原实体 */
    public static String cleanContent(String raw) {
        String s = raw
                .replace("<br/>", "\n").replace("<br>", "\n").replace("<br />", "\n")
                .replace("</p>", "\n").replace("</div>", "\n").replace("<p>", "");
        s = s.replaceAll("<script[\\s\\S]*?</script>", "");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        StringBuilder out = new StringBuilder(s.length());
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (isAdLine(t)) continue;
            out.append(t).append('\n');
        }
        String result = out.toString().trim();
        if (result.isEmpty()) {
            throw new IllegalStateException("正文为空");
        }
        return result;
    }

    private static boolean isAdLine(String t) {
        String lower = t.toLowerCase();
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.")) return true;
        if (t.contains("公众号") || t.contains("笔趣阁") || t.contains("起点读书")) return true;
        if (t.contains("请收藏") || t.contains("本站域名") || t.contains("域名请记")) return true;
        if ((t.contains("下载") || t.contains("安装")) && (lower.contains("app") || t.contains("客户端"))) return true;
        if (t.contains("一秒记住") || t.contains("手机用户请浏览")) return true;
        return false;
    }

    // ---------------- 封面代理 ----------------

    public static boolean allowedCoverHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("www.biquga.com") || h.endsWith("biquga.com") || h.endsWith("biquge.com")
                || h.endsWith("11222.cn") || h.endsWith("shuqi.com") || h.endsWith("shuqireader.com");
    }

    public HttpResponse<byte[]> fetchCover(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Referer", BASE + "/")
                .timeout(DurationHolder.REQUEST)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    }
}
