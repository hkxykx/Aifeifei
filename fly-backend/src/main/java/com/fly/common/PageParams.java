package com.fly.common;

/**
 * 分页参数校验（与原 FastAPI Query(ge=..., le=...) 行为对应，越界返回 422）。
 */
public final class PageParams {

    private PageParams() {
    }

    public static int page(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw new ApiError(422, "页码必须大于等于 1");
        }
        return page;
    }

    public static int size(Integer size, int defaultValue, int max) {
        if (size == null) {
            return defaultValue;
        }
        if (size < 1 || size > max) {
            throw new ApiError(422, "每页数量必须在 1-" + max + " 之间");
        }
        return size;
    }
}
