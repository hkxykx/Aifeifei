package com.fly.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计（对应原 app/api/dashboard.py）。
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // 总数统计
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("posts", count("select count(*) from post where status = 'published'"));
        counts.put("drafts", count("select count(*) from post where status = 'draft'"));
        counts.put("categories", count("select count(*) from category"));
        counts.put("tags", count("select count(*) from tag"));
        counts.put("comments", count("select count(*) from comment"));
        counts.put("messages", count("select count(*) from message"));
        counts.put("visitors", count("select count(*) from visitor"));

        // 趋势
        List<Map<String, Object>> postTrend = fillTrend(thirtyDaysAgo, jdbc.queryForList(
                "select to_char(published_at, 'YYYY-MM-DD') as d, count(*) as c "
                        + "from post where published_at >= ? and status = 'published' "
                        + "group by 1 order by 1", thirtyDaysAgo));
        List<Map<String, Object>> visitorTrend = fillTrend(thirtyDaysAgo, jdbc.queryForList(
                "select to_char(created_at, 'YYYY-MM-DD') as d, count(*) as c "
                        + "from visitor where created_at >= ? "
                        + "group by 1 order by 1", thirtyDaysAgo));

        // 分类分布
        List<Map<String, Object>> categoryDistribution = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select name, post_count from category where post_count > 0")) {
            categoryDistribution.add(Map.of(
                    "name", String.valueOf(row.get("name")),
                    "value", ((Number) row.get("post_count")).intValue()));
        }

        // 浏览器分布
        List<Map<String, Object>> browserDistribution = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select coalesce(nullif(browser, ''), '未知') as name, count(*) as c "
                        + "from visitor group by 1 order by count(*) desc")) {
            browserDistribution.add(Map.of(
                    "name", String.valueOf(row.get("name")),
                    "value", ((Number) row.get("c")).intValue()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("counts", counts);
        result.put("post_trend", postTrend);
        result.put("visitor_trend", visitorTrend);
        result.put("category_distribution", categoryDistribution);
        result.put("browser_distribution", browserDistribution);
        return result;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    /** 补全 30 天空缺日期 */
    private List<Map<String, Object>> fillTrend(LocalDateTime from, List<Map<String, Object>> rows) {
        Map<String, Number> rowMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("d");
            if (key != null) {
                rowMap.put(String.valueOf(key), (Number) row.get("c"));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate start = from.toLocalDate();
        for (int i = 0; i < 30; i++) {
            String date = start.plusDays(i).format(DATE);
            Number count = rowMap.get(date);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("count", count == null ? 0 : count.longValue());
            result.add(item);
        }
        return result;
    }
}
