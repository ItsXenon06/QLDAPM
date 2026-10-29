package com.xenon.qldapm.security;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Principal lưu trong session: chỉ chứa dữ liệu thuần, không giữ entity. Authority = MaCode của Quyen. */
public class TaiKhoanUserDetails implements UserDetails, CredentialsContainer {

    @Getter private final Integer maTaiKhoan;
    @Getter private final Integer maNhanVien;
    @Getter private final String maSoNhanVien;
    @Getter private final String hoTen;
    @Getter private final String tenVaiTro;
    private final String tenDangNhap;
    private String matKhauHash;
    private final boolean enabled;
    private final boolean khoaThuCong;
    private final LocalDateTime khoaDen;
    private final List<GrantedAuthority> authorities;

    public TaiKhoanUserDetails(Integer maTaiKhoan, Integer maNhanVien, String maSoNhanVien, String hoTen,
                               String tenVaiTro, String tenDangNhap, String matKhauHash, boolean enabled,
                               boolean khoaThuCong, LocalDateTime khoaDen, List<GrantedAuthority> authorities) {
        this.maTaiKhoan = maTaiKhoan;
        this.maNhanVien = maNhanVien;
        this.maSoNhanVien = maSoNhanVien;
        this.hoTen = hoTen;
        this.tenVaiTro = tenVaiTro;
        this.tenDangNhap = tenDangNhap;
        this.matKhauHash = matKhauHash;
        this.enabled = enabled;
        this.khoaThuCong = khoaThuCong;
        this.khoaDen = khoaDen;
        this.authorities = authorities;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return matKhauHash; }
    @Override public String getUsername() { return tenDangNhap; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    /** Khóa thủ công (trạng thái bi_khoa/tam_khoa) hoặc khóa tạm do nhập sai quá số lần (KhoaDen). */
    @Override
    public boolean isAccountNonLocked() {
        return !khoaThuCong && (khoaDen == null || khoaDen.isBefore(LocalDateTime.now()));
    }

    /** Xóa hash mật khẩu sau khi xác thực để không lưu vào session. */
    @Override public void eraseCredentials() { this.matKhauHash = null; }
}
