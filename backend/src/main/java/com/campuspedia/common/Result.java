package com.campuspedia.common;

import java.util.Map;

/**
 * 统一响应体：{code, msg, data}，code=200 成功。
 */
public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "成功", data);
    }

    public static <T> Result<T> ok(T data, String msg) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /** 分页响应：data 内含 list/total/page/size。 */
    public static Result<Map<String, Object>> page(Object data, long total, long page, long size) {
        return ok(Map.of("list", data, "total", total, "page", page, "size", size));
    }
}
