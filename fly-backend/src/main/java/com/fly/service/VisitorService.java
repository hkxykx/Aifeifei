package com.fly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.common.PageParams;
import com.fly.dto.Responses.VisitorOut;
import com.fly.entity.Visitor;
import com.fly.repository.VisitorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访客记录与 IP 归属地解析（对应原 app/services/visitor_service.py）。
 */
@Service
public class VisitorService {

    private static final Map<String, Map<String, Object>> GEO_CACHE = new ConcurrentHashMap<>();
    /** GEO 解析缓存上限：超过后整体清空，防止长期运行内存无界增长 */
    private static final int GEO_CACHE_MAX = 1000;
    /** 访客记录保留上限：后台默认 20 条/页 × 10 页，超出自动删除最旧记录 */
    public static final int MAX_RECORDS = 200;
    private static final RestClient REST_CLIENT;

    static {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        REST_CLIENT = RestClient.builder().requestFactory(factory).build();
    }

    /** 英文省份 → 中文 */
    private static final Map<String, String> PROVINCE_MAP = Map.ofEntries(
            Map.entry("beijing", "北京"), Map.entry("tianjin", "天津"),
            Map.entry("shanghai", "上海"), Map.entry("chongqing", "重庆"),
            Map.entry("hebei", "河北"), Map.entry("shanxi", "山西"),
            Map.entry("inner mongolia", "内蒙古"), Map.entry("liaoning", "辽宁"),
            Map.entry("jilin", "吉林"), Map.entry("heilongjiang", "黑龙江"),
            Map.entry("jiangsu", "江苏"), Map.entry("zhejiang", "浙江"),
            Map.entry("anhui", "安徽"), Map.entry("fujian", "福建"),
            Map.entry("jiangxi", "江西"), Map.entry("shandong", "山东"),
            Map.entry("henan", "河南"), Map.entry("hubei", "湖北"),
            Map.entry("hunan", "湖南"), Map.entry("guangdong", "广东"),
            Map.entry("guangxi", "广西"), Map.entry("hainan", "海南"),
            Map.entry("sichuan", "四川"), Map.entry("guizhou", "贵州"),
            Map.entry("yunnan", "云南"), Map.entry("tibet", "西藏"),
            Map.entry("shaanxi", "陕西"), Map.entry("gansu", "甘肃"),
            Map.entry("qinghai", "青海"), Map.entry("ningxia", "宁夏"),
            Map.entry("xinjiang", "新疆"), Map.entry("hong kong", "香港"),
            Map.entry("macau", "澳门"), Map.entry("taiwan", "台湾"));

    /** 科技/云厂商关键词 → 中文 */
    private static final Map<String, String> VENDOR_MAP = Map.ofEntries(
            Map.entry("alibaba", "阿里巴巴"), Map.entry("aliyun", "阿里云"),
            Map.entry("tencent", "腾讯"), Map.entry("tencent cloud", "腾讯云"),
            Map.entry("baidu", "百度"), Map.entry("baidu cloud", "百度云"),
            Map.entry("huawei", "华为"), Map.entry("huawei cloud", "华为云"),
            Map.entry("bytedance", "字节跳动"), Map.entry("volcengine", "火山引擎"),
            Map.entry("kuaishou", "快手"), Map.entry("meituan", "美团"),
            Map.entry("xiaomi", "小米"), Map.entry("jd.com", "京东"),
            Map.entry("netease", "网易"), Map.entry("kingsoft", "金山云"),
            Map.entry("qihoo 360", "奇虎360"), Map.entry("sina", "新浪"),
            Map.entry("sohu", "搜狐"), Map.entry("douban", "豆瓣"),
            Map.entry("amazon", "亚马逊"), Map.entry("aws", "亚马逊云"),
            Map.entry("microsoft", "微软"), Map.entry("azure", "微软云"),
            Map.entry("google", "谷歌"), Map.entry("google cloud", "谷歌云"),
            Map.entry("cloudflare", "Cloudflare"), Map.entry("oracle", "甲骨文"),
            Map.entry("ovh", "OVH"), Map.entry("digitalocean", "DigitalOcean"),
            Map.entry("hetzner", "Hetzner"), Map.entry("apple", "苹果"));

    /** ASN 号 → 运营商 */
    private static final Map<String, String> ASN_ORG_MAP = Map.ofEntries(
            Map.entry("56041", "中国移动"), Map.entry("56042", "中国移动"),
            Map.entry("56043", "中国移动"), Map.entry("56044", "中国移动"),
            Map.entry("56045", "中国移动"), Map.entry("56046", "中国移动"),
            Map.entry("56047", "中国移动"), Map.entry("56048", "中国移动"),
            Map.entry("58453", "中国移动"), Map.entry("9808", "中国移动"),
            Map.entry("24400", "中国移动"), Map.entry("24444", "中国移动"),
            Map.entry("24445", "中国移动"), Map.entry("4808", "中国联通"),
            Map.entry("4837", "中国联通"), Map.entry("9929", "中国联通"),
            Map.entry("17816", "中国联通"), Map.entry("4134", "中国电信"),
            Map.entry("4809", "中国电信"), Map.entry("4812", "中国电信"),
            Map.entry("23724", "中国电信"), Map.entry("140061", "中国电信"),
            Map.entry("4538", "中国教育网"), Map.entry("45102", "阿里云"),
            Map.entry("37963", "阿里云"), Map.entry("45090", "阿里云"),
            Map.entry("45069", "腾讯云"), Map.entry("132203", "腾讯云"),
            Map.entry("136907", "华为云"), Map.entry("55967", "百度云"),
            Map.entry("137696", "火山引擎"), Map.entry("13335", "Cloudflare"),
            Map.entry("209242", "Cloudflare"));

    private final VisitorRepository visitorRepository;
    private final ObjectMapper objectMapper;

    public VisitorService(VisitorRepository visitorRepository, ObjectMapper objectMapper) {
        this.visitorRepository = visitorRepository;
        this.objectMapper = objectMapper;
    }

    public List<VisitorOut> recent(Integer page, Integer size) {
        var pageable = PageRequest.of(PageParams.page(page) - 1, PageParams.size(size, 20, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return visitorRepository.findAll(pageable).getContent().stream()
                .map(v -> new VisitorOut(v.id, v.ip, v.path, v.city, v.region, v.country,
                        v.district, v.org, getOrgCn(v.org, v.asn), v.asn, v.isMobile,
                        v.isProxy, v.isHosting, v.browser, v.os, v.deviceType, v.createdAt))
                .toList();
    }

    public long count() {
        return visitorRepository.count();
    }

    /**
     * 记录访问：同一 IP 不重复插入，只保留该 IP 最后一次操作；
     * 超过 10 页（{@link #MAX_RECORDS} 条）时自动删除最旧记录。
     */
    @Transactional
    public Visitor recordVisit(String ip, String path, String userAgent) {
        Map<String, String> uaInfo = parseUa(userAgent == null ? "" : userAgent);
        String normIp = ip == null ? "" : ip;
        Map<String, Object> geo = fetchGeo(normIp);

        Visitor visitor = visitorRepository.findFirstByIpOrderByCreatedAtDesc(normIp)
                .orElseGet(Visitor::new);
        visitor.ip = normIp;
        visitor.path = path == null ? "" : path;
        visitor.userAgent = userAgent == null ? "" : userAgent;
        visitor.city = str(geo.get("city"));
        visitor.region = str(geo.get("region"));
        visitor.country = str(geo.get("country"));
        visitor.district = str(geo.get("district"));
        visitor.org = str(geo.get("org"));
        visitor.asn = str(geo.get("asn"));
        visitor.isMobile = Boolean.TRUE.equals(geo.get("is_mobile"));
        visitor.isProxy = Boolean.TRUE.equals(geo.get("is_proxy"));
        visitor.isHosting = Boolean.TRUE.equals(geo.get("is_hosting"));
        visitor.browser = uaInfo.get("browser");
        visitor.os = uaInfo.get("os");
        visitor.deviceType = uaInfo.get("device_type");
        visitor.createdAt = java.time.LocalDateTime.now();
        Visitor saved = visitorRepository.save(visitor);
        trimOverflow();
        return saved;
    }

    /** 超出保留上限时删除最旧记录 */
    private void trimOverflow() {
        long total = visitorRepository.count();
        if (total > MAX_RECORDS) {
            visitorRepository.deleteOldest((int) (total - MAX_RECORDS));
        }
    }

    public Map<String, Object> location(String ip) {
        return fetchGeo(ip == null ? "" : ip);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Transactional
    public void delete(Long visitorId) {
        visitorRepository.findById(visitorId).ifPresent(visitorRepository::delete);
    }

    @Transactional
    public void clear() {
        visitorRepository.deleteAll();
    }

    // ---- IP 归属地 ----

    public Map<String, Object> fetchGeo(String ip) {
        Map<String, Object> cached = GEO_CACHE.get(ip);
        if (cached != null) {
            return cached;
        }
        if (ip.isEmpty() || "127.0.0.1".equals(ip) || "::1".equals(ip)
                || ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return Map.of();
        }

        Map<String, Object> result = new java.util.HashMap<>();

        // uapis.cn（国内 API）
        try {
            String body = REST_CLIENT.get()
                    .uri("https://uapis.cn/api/v1/network/ipinfo?ip={ip}", ip)
                    .retrieve()
                    .body(String.class);
            JsonNode data = objectMapper.readTree(body == null ? "" : body);
            if (data.hasNonNull("ip")) {
                String[] parts = data.path("region").asText("").split(" ");
                result.put("country", parts.length >= 1 ? parts[0] : "");
                result.put("region", parts.length >= 2 ? parts[1] : "");
                result.put("city", parts.length >= 3 ? parts[2] : "");
                result.put("district", "");
                result.put("org", data.path("isp").asText(""));
                String asn = data.hasNonNull("asn") ? data.get("asn").asText("")
                        : data.path("as").asText("");
                result.put("asn", asn == null ? "" : asn);
                result.put("is_mobile", false);
                result.put("is_proxy", false);
                result.put("is_hosting", false);
            }
        } catch (Exception ignored) {
            // 回退到 ip-api.com
        }

        // 回退：ip-api.com（仅 IPv4）
        if (result.isEmpty() && !ip.contains(":")) {
            try {
                String body = REST_CLIENT.get()
                        .uri("http://ip-api.com/json/{ip}?lang=zh-CN&fields=66846719", ip)
                        .retrieve()
                        .body(String.class);
                JsonNode data = objectMapper.readTree(body == null ? "" : body);
                if ("success".equals(data.path("status").asText(""))) {
                    result.put("city", data.path("city").asText(""));
                    result.put("region", data.path("regionName").asText(""));
                    result.put("country", data.path("country").asText(""));
                    result.put("district", data.path("district").asText(""));
                    String org = data.path("org").asText("");
                    if (org.isEmpty()) {
                        org = data.path("isp").asText("");
                    }
                    result.put("org", org);
                    result.put("asn", data.path("as").asText(""));
                    result.put("is_mobile", data.path("mobile").asBoolean(false));
                    result.put("is_proxy", data.path("proxy").asBoolean(false));
                    result.put("is_hosting", data.path("hosting").asBoolean(false));
                }
            } catch (Exception ignored) {
                // 两个来源都失败则返回空
            }
        }

        // 解析结果（含失败的空结果）都入缓存：失败也缓存，避免每次访问都重试外呼
        if (GEO_CACHE.size() >= GEO_CACHE_MAX) {
            GEO_CACHE.clear();
        }
        GEO_CACHE.put(ip, result);
        return result;
    }

    // ---- 组织名中文化 ----

    String getOrgCn(String org, String asn) {
        if (org == null || org.isEmpty()) {
            return "";
        }
        String orgLower = org.toLowerCase()
                .replace(",", "").replace(".", "").replace("&", "and");

        String foundProvince = "";
        for (Map.Entry<String, String> e : PROVINCE_MAP.entrySet()) {
            if (orgLower.contains(e.getKey())) {
                foundProvince = e.getValue();
                break;
            }
        }

        boolean isMobile = orgLower.contains("china mobile") || orgLower.contains("chinamobile")
                || orgLower.contains("cmcc");
        boolean isUnicom = orgLower.contains("china unicom") || orgLower.contains("chinaunicom")
                || orgLower.contains("cucc");
        boolean isTelecom = orgLower.contains("china telecom") || orgLower.contains("chinatelecom")
                || orgLower.contains("chinanet") || orgLower.contains("ctcc");
        boolean isCernet = orgLower.contains("cernet") || orgLower.contains("cernt")
                || orgLower.contains("china education");

        if (isMobile) {
            return foundProvince.isEmpty() ? "中国移动" : "中国移动(" + foundProvince + ")";
        }
        if (isUnicom) {
            return foundProvince.isEmpty() ? "中国联通" : "中国联通(" + foundProvince + ")";
        }
        if (isTelecom) {
            return foundProvince.isEmpty() ? "中国电信" : "中国电信(" + foundProvince + ")";
        }
        if (isCernet) {
            return "中国教育网";
        }

        if (orgLower.contains("mobile") || org.contains("移动")) {
            return foundProvince.isEmpty() ? "移动运营商" : foundProvince + "移动";
        }
        if (orgLower.contains("unicom") || orgLower.contains("united network")
                || orgLower.contains("uninet")) {
            return foundProvince.isEmpty() ? "中国联通" : foundProvince + "联通";
        }
        if (orgLower.contains("telecom") || orgLower.contains("chinanet")
                || orgLower.contains("telecommunications")) {
            return foundProvince.isEmpty() ? "中国电信" : foundProvince + "电信";
        }
        if (orgLower.contains("netcom") || orgLower.contains("cnc")) {
            return "中国网通";
        }

        if (orgLower.contains("cable")) {
            return foundProvince.isEmpty() ? "有线电视网络" : foundProvince + "广电";
        }
        if (orgLower.contains("broadcast") || orgLower.contains("radio")
                || orgLower.contains("tv")) {
            return foundProvince.isEmpty() ? "广电网络" : foundProvince + "广电";
        }
        if (orgLower.contains("huashu") || orgLower.contains("wasu")) {
            return "华数传媒";
        }
        if (orgLower.contains("inter-exchange") || orgLower.contains("interexchange")) {
            return "互联交换网络";
        }
        if (orgLower.contains("china networks")) {
            return "中国网络交换";
        }

        for (Map.Entry<String, String> e : VENDOR_MAP.entrySet()) {
            if (orgLower.contains(e.getKey())) {
                return e.getValue();
            }
        }

        if (asn != null && !asn.isEmpty()) {
            String asnNum = asn.replace("AS", "").replace("as", "").trim();
            String mapped = ASN_ORG_MAP.get(asnNum);
            if (mapped != null) {
                return mapped;
            }
        }

        if (asn != null && !asn.isEmpty()) {
            String asnLower = asn.toLowerCase();
            if (asnLower.contains("china mobile") || asnLower.contains("chinamobile")
                    || asnLower.contains("cmcc")) {
                return "中国移动";
            }
            if (asnLower.contains("china unicom") || asnLower.contains("chinaunicom")) {
                return "中国联通";
            }
            if (asnLower.contains("china telecom") || asnLower.contains("chinatelecom")
                    || asnLower.contains("chinanet")) {
                return "中国电信";
            }
        }
        return "";
    }

    private Map<String, String> parseUa(String ua) {
        String browser = "Unknown";
        if (ua.contains("Edg/")) {
            browser = "Edge";
        } else if (ua.contains("Chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("Firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("Safari/")) {
            browser = "Safari";
        }

        String os = "Unknown";
        if (ua.contains("Win")) {
            os = "Windows";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad")) {
            os = "iOS";
        } else if (ua.contains("Mac")) {
            os = "macOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        }

        boolean mobile = ua.contains("Mobi") || ua.contains("Android") || ua.contains("iPhone");
        return Map.of("browser", browser, "os", os, "device_type", mobile ? "手机" : "电脑");
    }
}
