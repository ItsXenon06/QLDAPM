package com.xenon.qldapm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập") @Size(max = 100) String tenDangNhap,
        @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(max = 100) String matKhau) {
}
