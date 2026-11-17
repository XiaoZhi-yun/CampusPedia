package com.campuspedia.common;
/**
 * 通用层：携带业务错误码的运行时异常，由业务代码主动抛出以表示可预期的失败。
 * <p>GlobalExceptionHandler 捕获后把错误码映射为真实 HTTP 状态，是业务失败的主通道。</p>
 */

/**
 * 业务异常，携带业务错误码（默认 400）。
 */
public class BizException extends RuntimeException {

    /** 错误码同时用作 HTTP 状态码，由 GlobalExceptionHandler 写入响应。 */
    private final int code;

    /**
     * 以默认错误码 400 构造业务异常。
     *
     * @param message 可直接回显给前端的错误提示
     */
    public BizException(String message) {
        this(400, message);
    }

    /**
     * 以指定错误码构造业务异常，用于区分 401/403 等鉴权失败。
     *
     * @param code    业务错误码，决定最终 HTTP 状态
     * @param message 可直接回显给前端的错误提示
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 供 GlobalExceptionHandler 读取错误码以映射真实 HTTP 状态。
     *
     * @return 构造时传入的业务错误码
     */
    public int getCode() {
        return code;
    }
}
