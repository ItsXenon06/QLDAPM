package com.xenon.qldapm.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** Định dạng lỗi chuẩn cho toàn bộ API. */
public record ApiError(LocalDateTime timestamp, int status, String code, String message, Map<String, String> errors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(LocalDateTime.now(), status, code, message, Map.of());
    }

    public static ApiError of(int status, String code, String message, Map<String, String> errors) {
        return new ApiError(LocalDateTime.now(), status, code, message, errors);
    }
}
