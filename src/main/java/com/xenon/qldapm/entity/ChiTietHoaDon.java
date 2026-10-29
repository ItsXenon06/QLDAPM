package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/** Dòng hóa đơn (snapshot tên/SKU/giá vốn tại thời điểm bán). */
@Entity
@Table(name = "ChiTietHoaDon")
@Getter
@Setter
@NoArgsConstructor
public class ChiTietHoaDon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaChiTiet")
    private Integer maChiTiet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaHoaDon", nullable = false)
    private HoaDon hoaDon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSanPham", nullable = false)
    private SanPham sanPham;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaSerial")
    private SerialSanPham serial;

    @Column(name = "TenHang", nullable = false, length = 255)
    private String tenHang;

    @Column(name = "MaSKU", nullable = false, length = 60)
    private String maSKU;

    @Column(name = "SoLuong", nullable = false)
    private Integer soLuong = 1;

    @Column(name = "DonGia", nullable = false)
    private BigDecimal donGia;

    @Column(name = "GiamGia", nullable = false)
    private BigDecimal giamGia = BigDecimal.ZERO;

    @Column(name = "ThueSuat", nullable = false)
    private BigDecimal thueSuat = new BigDecimal("10");

    @Column(name = "GiaVon")
    private BigDecimal giaVon;

    @Column(name = "LaQuaTang", nullable = false)
    private Boolean laQuaTang = false;

    @Column(name = "HieuLuc", nullable = false)
    private Boolean hieuLuc = true;

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "ThanhTien", insertable = false, updatable = false)
    private BigDecimal thanhTien;

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "TienThue", insertable = false, updatable = false)
    private BigDecimal tienThue;
}
