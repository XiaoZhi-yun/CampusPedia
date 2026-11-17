package com.campuspedia.common;
/**
 * 通用层：全局异常拦截，统一把异常转换为 Result 响应或 SSE 错误帧。
 * <p>承接各 Controller 抛出的 BizException 与兜底异常，集中处理状态码与错误文案脱敏。</p>
 */

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理：BizException 返回业务错误码，其他异常兜底 500。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常：把错误码映射为真实 HTTP 状态并回传统一响应体。
     * <p>SSE 响应则改为下发错误帧，副作用是直接写 response 输出流。</p>
     *
     * @param e        携带业务错误码与可回显文案的异常
     * @param request  当前请求
     * @param response 用于设置状态码或写 SSE 错误帧
     * @return 非 SSE 时返回 Result 错误体；SSE 时返回 null
     * @throws IOException 写 SSE 错误帧失败时抛出
     */
    @ExceptionHandler(BizException.class)
    public Object handleBiz(BizException e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isSse(response)) {
            writeSseError(response, e.getMessage());
            return null;
        }
        response.setStatus(httpStatus(e.getCode()));
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 请求体缺失/格式错误属于客户端问题，返回 400 且不回显内部方法签名。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleBadBody(HttpMessageNotReadableException e, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(400);
        return Result.error(400, "请求参数格式有误");
    }

    /**
     * 兜底处理未预期异常：记录日志后统一返回 500。
     *
     * @param e        未捕获的异常，仅写日志不回显
     * @param request  当前请求
     * @param response 用于设置 500 或写 SSE 错误帧
     * @return 非 SSE 时返回 Result 错误体；SSE 时返回 null
     * @throws IOException 写 SSE 错误帧失败时抛出
     */
    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.error("未捕获异常", e);
        if (isSse(response)) {
            writeSseError(response, "服务器内部错误，请稍后重试");
            return null;
        }
        response.setStatus(500);
        /** 固定文案不回显异常堆栈或内部信息，避免敏感细节外泄。 */
        return Result.error(500, "服务器内部错误，请稍后重试");
    }

    /** 业务码映射到真实 HTTP 状态，避免「HTTP 200 + code=500」让客户端误判成功。 */
    private int httpStatus(int code) {
        return code >= 400 && code < 600 ? code : 500;
    }

    private boolean isSse(HttpServletResponse response) {
        String ct = response.getContentType();
        return ct != null && ct.contains("text/event-stream");
    }

    /** SSE 已建立时无法再改 HTTP 状态，只能以 event-stream 错误帧把失败告知前端。 */
    private void writeSseError(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("text/event-stream;charset=UTF-8");
        String json = "{\"type\":\"error\",\"data\":\"" + escapeJson(msg) + "\"}";
        response.getOutputStream().write(("data:" + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /** 错误文案可能含引号或换行，转义后再拼进 SSE data 帧，避免破坏帧结构。 */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}