package com.campuspedia.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.error("未捕获异常", e);
        if (isSse(response)) {
            writeSseError(response, "服务器错误：" + e.getMessage());
            return null;
        }
        return Result.error(500, "服务器错误：" + e.getMessage());
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