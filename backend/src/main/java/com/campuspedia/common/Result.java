package com.campuspedia.common;
/**
 * 通用层：统一响应体 {code, msg, data}，供各 Controller 与 GlobalExceptionHandler 共同复用。
 * <p>用于收敛所有接口的返回结构，使前端按同一契约解析业务状态。</p>
 */

import java.util.Map;

/**
 * 统一响应体：{code, msg, data}，code=200 成功。
 */
public record Result<T>(int code, String msg, T data) {

    /**
     * 构造成功响应，使用默认提示文案。
     *
     * @param data 业务数据，允许为 null
     * @return code 为 200 的统一响应体
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "成功", data);
    }

    /**
     * 构造成功响应并自定义返回给前端的提示文案。
     *
     * @param data 业务数据，允许为 null
     * @param msg  自定义提示文案
     * @return code 为 200 的统一响应体
     */
    public static <T> Result<T> ok(T data, String msg) {
        return new Result<>(200, msg, data);
    }

    /**
     * 构造默认业务失败响应，错误码固定为 500。
     *
     * @param msg 返回给前端的错误提示
     * @return code 为 500、data 为 null 的响应体
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /**
     * 构造指定错误码的失败响应，供业务区分不同失败场景。
     *
     * @param code 业务错误码
     * @param msg  返回给前端的错误提示
     * @return data 为 null 的响应体
     */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /** 分页响应：data 内含 list/total/page/size。 */
    public static Result<Map<String, Object>> page(Object data, long total, long page, long size) {
        return ok(Map.of("list", data, "total", total, "page", page, "size", size));
    }
}
