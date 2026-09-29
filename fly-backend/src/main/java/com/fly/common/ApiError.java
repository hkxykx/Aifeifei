package com.fly.common;

/**
 * 业务异常，统一以 HTTP 状态码 + {"detail": "..."} 返回（与 FastAPI HTTPException 一致）。
 */
public class ApiError extends RuntimeException {

    private final int status;

    public ApiError(int status, String detail) {
        super(detail);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiError notFound(String detail) {
        return new ApiError(404, detail);
    }

    public static ApiError unauthorized(String detail) {
        return new ApiError(401, detail);
    }

    public static ApiError badRequest(String detail) {
        return new ApiError(400, detail);
    }
}
