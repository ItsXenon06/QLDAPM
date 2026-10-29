package com.xenon.qldapm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DoiMatKhauRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String matKhauCu,
        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Size(min = 8, max = 100, message = "Mật khẩu mới phải từ 8 đến 100 ký tự") String matKhauMoi) {
}
