package com.fly.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控方案 A：后端未处理异常落库，站长后台「异常日志」可查。
 * 防刷：同一错误短窗口内只落库一次 + 表行数上限定期清理，
 * 避免 500 被恶意刷出时 app_error_log 无限膨胀。
 */
@Service
public class ErrorLogService {

    /** 同一错误（method+path+异常类）60 秒内最多落库一次 */
    private static final long RECORD_THROTTLE_MS = 60_000;
    /** 节流键缓存上限：超出即清空（防御键数量被刷爆） */
    private static final int THROTTLE_KEYS_MAX = 512;
    /** 每 200 次落库执行一次行数清理 */
    private static final int PRUNE_EVERY = 200;
    /** 表保留的最大行数（只保留最新 N 行） */
    private static final int MAX_ROWS = 5000;

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Long> lastRecordAt = new ConcurrentHashMap<>();
    private final AtomicLong insertCount = new AtomicLong();

    @Autowired
    public ErrorLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    void initTable() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS app_error_log (
                        id BIGSERIAL PRIMARY KEY,
                        path VARCHAR(512) NOT NULL DEFAULT '',
                        method VARCHAR(16) NOT NULL DEFAULT '',
                        status INTEGER NOT NULL DEFAULT 500,
                        message TEXT NOT NULL DEFAULT '',
                        stack TEXT NOT NULL DEFAULT '',
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )""");
        } catch (Exception e) {
            // 表已存在等
        }
    }

    /** 记录一次异常（任何写库失败都不能影响主流程） */
    public void record(String path, String method, int status, Throwable e) {
        try {
            String key = method + " " + truncate(path, 512) + " " + e.getClass().getName();
            long now = System.currentTimeMillis();
            Long last = lastRecordAt.get(key);
            if (last != null && now - last < RECORD_THROTTLE_MS) {
                return;
            }
            if (lastRecordAt.size() >= THROTTLE_KEYS_MAX) {
                lastRecordAt.clear();
            }
            lastRecordAt.put(key, now);

            String message = e.getClass().getName() + ": " + String.valueOf(e.getMessage());
            if (message.length() > 2000) {
                message = message.substring(0, 2000);
            }
            String stack = stackTrace(e);
            jdbc.update("INSERT INTO app_error_log(path, method, status, message, stack, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                    truncate(path, 512), truncate(method, 16), status, message, stack);
            if (insertCount.incrementAndGet() % PRUNE_EVERY == 0) {
                prune();
            }
        } catch (Exception ignore) {
            // 落库失败仅记日志，不二次抛错
        }
    }

    /** 只保留最新 MAX_ROWS 行，防止表无限增长 */
    private void prune() {
        try {
            jdbc.update("""
                    DELETE FROM app_error_log
                    WHERE id < COALESCE((SELECT MIN(id) FROM (
                        SELECT id FROM app_error_log ORDER BY id DESC LIMIT ?
                    ) t), 0)""", MAX_ROWS);
        } catch (Exception ignore) {
            // 清理失败不影响主流程
        }
    }

    public List<Map<String, Object>> list(int limit) {
        int l = Math.max(1, Math.min(500, limit));
        return jdbc.queryForList("""
                SELECT id, path, method, status, message, stack, created_at
                FROM app_error_log ORDER BY id DESC LIMIT ?""", l);
    }

    public void clear() {
        jdbc.update("DELETE FROM app_error_log");
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM app_error_log WHERE id = ?", id) > 0;
    }

    private static String stackTrace(Throwable e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String s = sw.toString();
            return s.length() > 4000 ? s.substring(0, 4000) : s;
        } catch (Exception ex) {
            return "";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
