package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Voucher phát hành từ chương trình 'tang_voucher'. */
@Entity
@Table(name = "Voucher")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaVoucher")
    private Integer maVoucher;

    @Column(name = "MaCode", nullable = false, length = 50)
    private String maCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaKhuyenMai", nullable = false)
    private KhuyenMai khuyenMai;

    @Column(name = "GiaTri", nullable = false)
    private BigDecimal giaTri;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaKhachHang")
    private KhachHang khachHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HoaDonPhatHanh")
    private HoaDon hoaDonPhatHanh;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HoaDonSuDung")
    private HoaDon hoaDonSuDung;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "chua_dung";

    @Column(name = "NgayPhatHanh", nullable = false)
    private LocalDateTime ngayPhatHanh = LocalDateTime.now();

    @Column(name = "HanSuDung", nullable = false)
    private LocalDateTime hanSuDung;

    @Column(name = "NgaySuDung")
    private LocalDateTime ngaySuDung;
}
