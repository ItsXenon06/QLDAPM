package com.xenon.qldapm.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public final class AdminDtos {
    private AdminDtos() {}
    public record UserRequest(@NotBlank @Size(max=20) String maSoNhanVien,
            @NotBlank @Size(max=150) String hoTen, @Email @Size(max=255) String email,
            @Size(max=20) String soDienThoai, @NotNull Integer maVaiTro,
            @NotBlank @Size(max=100) String tenDangNhap, @Size(min=8,max=100) String matKhau,
            @NotBlank String trangThai) {}
    public record RoleRequest(@NotBlank @Size(max=50) String tenVaiTro,
            @Size(max=500) String moTa, @NotNull Boolean trangThai, @NotNull Set<Integer> maQuyens) {}
    public record CounterRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,20}") String maQuayCode,
            @NotBlank @Size(max=100) String tenQuay, @Size(max=100) String viTri,
            @Size(max=45) String diaChiIP, @Size(max=100) String tenMayQuet,
            @Size(max=100) String tenMayIn, @NotBlank String trangThai) {}
}
