package com.xenon.qldapm.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        return build(e.getStatus(), e.getCode(), e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> invalidParam(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Nội dung yêu cầu không đúng định dạng", Map.of());
    }

    // ---- xác thực / phân quyền ----
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, "SAI_THONG_TIN_DANG_NHAP", "Sai tên đăng nhập hoặc mật khẩu", Map.of());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiError> locked(LockedException e) {
        return build(HttpStatus.LOCKED, "TAI_KHOAN_BI_KHOA",
                "Tài khoản đang bị khóa, vui lòng thử lại sau hoặc liên hệ quản trị", Map.of());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> disabled(DisabledException e) {
        return build(HttpStatus.FORBIDDEN, "TAI_KHOAN_VO_HIEU", "Tài khoản đã bị vô hiệu hóa", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Xác thực không thành công", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", Map.of());
    }

    // ---- ràng buộc CSDL (vd: trùng Serial/IMEI - rủi ro R01) ----
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        log.warn("Vi phạm ràng buộc CSDL: {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY",
                "Dữ liệu trùng lặp hoặc vi phạm ràng buộc (ví dụ: Serial/IMEI, mã đã tồn tại)", Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, Map<String, String> errors) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message, errors));
    }
}
