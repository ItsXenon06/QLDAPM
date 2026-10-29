package com.xenon.qldapm.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có chủ đích (vd: Serial đã bán, tồn kho không đủ). */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
