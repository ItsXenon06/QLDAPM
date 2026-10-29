package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Nhân viên (chỉ phục vụ tài khoản đăng nhập, không phải HRM). */
@Entity
@Table(name = "NhanVien")
@Getter
@Setter
@NoArgsConstructor
public class NhanVien {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaNhanVien")
    private Integer maNhanVien;

    @Column(name = "MaSoNhanVien", nullable = false, length = 20)
    private String maSoNhanVien;

    @Column(name = "HoTen", nullable = false, length = 150)
    private String hoTen;

    @Column(name = "Email", length = 255)
    private String email;

    @Column(name = "SoDienThoai", length = 20)
    private String soDienThoai;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaVaiTro", nullable = false)
    private VaiTro vaiTro;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "hoat_dong";

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
