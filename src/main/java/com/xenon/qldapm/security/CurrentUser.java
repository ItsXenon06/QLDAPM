package com.xenon.qldapm.security;

import com.xenon.qldapm.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Lấy người dùng đang đăng nhập từ bất kỳ service nào. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<TaiKhoanUserDetails> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof TaiKhoanUserDetails u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static TaiKhoanUserDetails require() {
        return get().orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa đăng nhập"));
    }
}
