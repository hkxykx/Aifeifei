package com.fly.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP / 路径头解析（与原 FastAPI Request 头读取逻辑一致）。
 */
public final class ClientIps {

    private ClientIps() {
    }

    public static String from(HttpServletRequest request) {
        String ip = firstHeader(request, "x-forwarded-for");
        if (ip.isEmpty()) {
            ip = firstHeader(request, "x-real-ip");
        }
        if (ip.isEmpty()) {
            ip = request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
        }
        return ip;
    }

    public static String path(HttpServletRequest request) {
        String path = request.getHeader("x-path");
        return path == null ? "" : path;
    }

    public static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("user-agent");
        return ua == null ? "" : ua;
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String header = request.getHeader(name);
        if (header == null || header.isBlank()) {
            return "";
        }
        return header.split(",")[0].trim();
    }
}
