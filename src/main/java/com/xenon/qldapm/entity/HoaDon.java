package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Hóa đơn bán hàng tại quầy POS. Tiền hàng đã gồm VAT. */
@Entity
@Table(name = "HoaDon")
@Getter
@Setter
@NoArgsConstructor
public class HoaDon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaHoaDon")
    private Integer maHoaDon;

    @Column(name = "SoHoaDon", nullable = false, length = 30)
    private String soHoaDon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaQuay", nullable = false)
    private QuayPOS quay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaThuNgan", nullable = false)
    private NhanVien thuNgan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaKhachHang")
    private KhachHang khachHang;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "dang_lap";

    @Column(name = "TongTienHang", nullable = false)
    private BigDecimal tongTienHang = BigDecimal.ZERO;

    @Column(name = "TongGiamGia", nullable = false)
    private BigDecimal tongGiamGia = BigDecimal.ZERO;

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "TongThanhToan", insertable = false, updatable = false)
    private BigDecimal tongThanhToan;

    @Column(name = "TongTienThue", nullable = false)
    private BigDecimal tongTienThue = BigDecimal.ZERO;

    @Column(name = "SoLanIn", nullable = false)
    private Integer soLanIn = 0;

    @Column(name = "ThoiGianTao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "ThoiGianThanhToan")
    private LocalDateTime thoiGianThanhToan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HuyBoi")
    private NhanVien huyBoi;

    @Column(name = "LyDoHuy", length = 255)
    private String lyDoHuy;

    @Column(name = "GhiChu", length = 500)
    private String ghiChu;

    @OneToMany(mappedBy = "hoaDon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChiTietHoaDon> chiTiets = new ArrayList<>();

    public void addChiTietHoaDon(ChiTietHoaDon item) {
        chiTiets.add(item);
        item.setHoaDon(this);
    }
}
