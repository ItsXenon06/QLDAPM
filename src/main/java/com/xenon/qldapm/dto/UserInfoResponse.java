package com.xenon.qldapm.dto;

import com.xenon.qldapm.security.TaiKhoanUserDetails;

import java.util.List;

public record UserInfoResponse(Integer maNhanVien, String maSoNhanVien, String hoTen,
                               String tenDangNhap, String tenVaiTro, List<String> quyens) {

    public static UserInfoResponse from(TaiKhoanUserDetails u) {
        return new UserInfoResponse(u.getMaNhanVien(), u.getMaSoNhanVien(), u.getHoTen(),
                u.getUsername(), u.getTenVaiTro(),
                u.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList());
    }
}
