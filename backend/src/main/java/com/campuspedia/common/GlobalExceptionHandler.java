package com.campuspedia.common;

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

    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.error("未捕获异常", e);
        if (isSse(response)) {
            writeSseError(response, "服务器内部错误，请稍后重试");
            return null;
        }
        response.setStatus(500);
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

    private void writeSseError(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("text/event-stream;charset=UTF-8");
        String json = "{\"type\":\"error\",\"data\":\"" + escapeJson(msg) + "\"}";
        response.getOutputStream().write(("data:" + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}