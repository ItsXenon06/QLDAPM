package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Tài khoản đăng nhập (1 nhân viên : 1 tài khoản đang dùng). */
@Entity
@Table(name = "TaiKhoan")
@Getter
@Setter
@NoArgsConstructor
public class TaiKhoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaTaiKhoan")
    private Integer maTaiKhoan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaNhanVien", nullable = false)
    private NhanVien nhanVien;

    @Column(name = "TenDangNhap", nullable = false, length = 100)
    private String tenDangNhap;

    @Column(name = "MatKhauHash", nullable = false, length = 500)
    private String matKhauHash;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "hoat_dong";

    @Column(name = "SoLanDangNhapSai", nullable = false)
    private Integer soLanDangNhapSai = 0;

    @Column(name = "KhoaDen")
    private LocalDateTime khoaDen;

    @Column(name = "DangNhapLanCuoi")
    private LocalDateTime dangNhapLanCuoi;

    @Column(name = "DoiMatKhauLuc")
    private LocalDateTime doiMatKhauLuc;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
