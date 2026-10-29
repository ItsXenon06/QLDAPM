package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Khách hàng (NULL trên hóa đơn = khách vãng lai). */
@Entity
@Table(name = "KhachHang")
@Getter
@Setter
@NoArgsConstructor
public class KhachHang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaKhachHang")
    private Integer maKhachHang;

    @Column(name = "HoTen", nullable = false, length = 150)
    private String hoTen;

    @Column(name = "SoDienThoai", nullable = false, length = 20)
    private String soDienThoai;

    @Column(name = "Email", length = 255)
    private String email;

    @Column(name = "DiaChi", length = 500)
    private String diaChi;

    @Column(name = "TenCongTy", length = 200)
    private String tenCongTy;

    @Column(name = "MaSoThue", length = 20)
    private String maSoThue;

    @Column(name = "GhiChu", length = 500)
    private String ghiChu;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
