package com.fly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.entity.Music;
import com.fly.repository.MusicRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为本地音乐匹配平台封面与歌词（网易云优先，QQ音乐兜底）。
 * 结果缓存进 music 表（cover/lrc 字段），只写数据库，绝不改动本地音乐文件。
 */
@Service
public class MusicMatchService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final MusicRepository musicRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** 进行中的匹配（同一首歌并发请求只跑一次） */
    private final ConcurrentHashMap<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    public MusicMatchService(MusicRepository musicRepository) {
        this.musicRepository = musicRepository;
    }

    public record MatchResult(String cover, String lrc, String source) {
    }

    /** 匹配一首歌：有缓存直接返回，否则搜索平台并落库 */
    public MatchResult match(Long id) {
        Music music = musicRepository.findById(id)
                .orElse(null);
        if (music == null) {
            return new MatchResult("", "", "");
        }
        boolean hasCover = music.cover != null && !music.cover.isBlank();
        boolean hasLrc = music.lrc != null && !music.lrc.isBlank();
        if (hasCover && hasLrc) {
            return new MatchResult(music.cover, music.lrc, "cache");
        }

        if (inFlight.putIfAbsent(id, Boolean.TRUE) != null) {
            // 已有相同请求在跑，返回当前已缓存部分
            return new MatchResult(music.cover, music.lrc, "busy");
        }
        try {
            String query = buildQuery(music);
            String cover = hasCover ? music.cover : "";
            String lrc = hasLrc ? music.lrc : "";
            String source = "";

            JsonNode netease = searchNetease(query);
            if (netease != null) {
                if (cover.isEmpty()) {
                    cover = neteaseCover(netease);
                }
                if (lrc.isEmpty()) {
                    lrc = neteaseLrc(netease.path("id").asLong(0));
                }
                source = "netease";
            }

            if ((cover.isEmpty() || lrc.isEmpty())) {
                JsonNode qq = searchQq(query);
                if (qq != null) {
                    if (cover.isEmpty()) {
                        cover = qqCover(qq);
                    }
                    if (lrc.isEmpty()) {
                        lrc = qqLrc(qq);
                    }
                    if (!source.isEmpty()) {
                        source += "+qq";
                    } else {
                        source = "qq";
                    }
                }
            }

            if (!cover.equals(music.cover) || !lrc.equals(music.lrc)) {
                music.cover = cover;
                music.lrc = lrc;
                musicRepository.save(music);
            }
            return new MatchResult(cover, lrc, source);
        } catch (Exception e) {
            return new MatchResult(music.cover, music.lrc, "error:" + e.getMessage());
        } finally {
            inFlight.remove(id);
        }
    }

    /** 搜索关键词：优先"歌名 歌手"，剔除本地目录的占位歌手 */
    private String buildQuery(Music music) {
        String title = music.title == null ? "" : music.title.trim();
        String artist = music.artist == null ? "" : music.artist.trim();
        if (artist.equals("本地目录") || artist.equals("未知歌手") || artist.isBlank()) {
            // 文件名常见格式："歌名-歌手" → 只取歌名部分搜索，提高命中率
            int dash = title.indexOf('-');
            if (dash > 0 && dash < title.length() - 1) {
                return title.substring(0, dash).trim();
            }
            return title;
        }
        return title + " " + artist;
    }

    // ========== 网易云 ==========

    private JsonNode searchNetease(String keyword) {
        try {
            String url = "https://music.163.com/api/search/get/web?s="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&type=1&limit=5";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://music.163.com/")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode songs = root.path("result").path("songs");
            if (!songs.isArray() || songs.isEmpty()) {
                return null;
            }
            return songs.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String neteaseCover(JsonNode song) {
        try {
            // 搜索结果自带 album.picUrl 时直接用，否则查详情
            String pic = song.path("album").path("picUrl").asText("");
            if (pic == null || pic.isBlank() || pic.equals("null")) {
                long id = song.path("id").asLong(0);
                String url = "https://music.163.com/api/song/detail?ids=[" + id + "]";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", UA)
                        .header("Referer", "https://music.163.com/")
                        .timeout(Duration.ofSeconds(8))
                        .GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode songs = mapper.readTree(resp.body()).path("songs");
                if (songs.isArray() && !songs.isEmpty()) {
                    pic = songs.get(0).path("album").path("picUrl").asText("");
                }
            }
            return pic == null ? "" : pic.replace("http://", "https://");
        } catch (Exception e) {
            return "";
        }
    }

    private String neteaseLrc(long songId) {
        if (songId <= 0) {
            return "";
        }
        try {
            String url = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=1&kv=1&tv=-1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://music.163.com/")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "";
            }
            String lrc = mapper.readTree(resp.body()).path("lrc").path("lyric").asText("");
            return lrc == null ? "" : lrc;
        } catch (Exception e) {
            return "";
        }
    }

    // ========== QQ音乐 ==========

    private JsonNode searchQq(String keyword) {
        try {
            String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&format=json&n=5&p=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://y.qq.com/")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            String body = resp.body();
            // 原始响应可能被 callback 包裹（jsonCallback(...)）
            int start = body.indexOf('{');
            int end = body.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonNode list = mapper.readTree(body.substring(start, end + 1))
                    .path("data").path("song").path("list");
            if (!list.isArray() || list.isEmpty()) {
                return null;
            }
            return list.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String qqCover(JsonNode song) {
        try {
            String albumMid = song.path("albummid").asText("");
            if (albumMid.isBlank()) {
                return "";
            }
            return "https://y.qq.com/music/photo/public/T002R500x500M000" + albumMid + ".jpg@500w_500h_1e_1c.jpg";
        } catch (Exception e) {
            return "";
        }
    }

    private String qqLrc(JsonNode song) {
        try {
            String songMid = song.path("songmid").asText("");
            if (songMid.isBlank()) {
                return "";
            }
            String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                    + songMid + "&format=json&nobase64=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://c.y.qq.com/")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "";
            }
            String body = resp.body();
            int start = body.indexOf('{');
            int end = body.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return "";
            }
            String lrc = mapper.readTree(body.substring(start, end + 1)).path("lyric").asText("");
            return lrc == null ? "" : lrc;
        } catch (Exception e) {
            return "";
        }
    }

    public static Map<String, Object> toMap(MatchResult r) {
        return Map.of("cover", r.cover(), "lrc", r.lrc(), "source", r.source());
    }
}
