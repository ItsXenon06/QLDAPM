package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/** Chi tiết phiếu trả hàng. */
@Entity
@Table(name = "ChiTietTraHang")
@Getter
@Setter
@NoArgsConstructor
public class ChiTietTraHang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaChiTiet")
    private Integer maChiTiet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaPhieuTra", nullable = false)
    private PhieuTraHang phieuTra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaChiTietHD", nullable = false)
    private ChiTietHoaDon chiTietHoaDon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaSerial")
    private SerialSanPham serial;

    @Column(name = "SoLuong", nullable = false)
    private Integer soLuong = 1;

    @Column(name = "TinhTrangHang", nullable = false, length = 30)
    private String tinhTrangHang;

    @Column(name = "SoTienHoan", nullable = false)
    private BigDecimal soTienHoan;

    @Column(name = "NhapLaiKho", nullable = false)
    private Boolean nhapLaiKho = false;

    @Column(name = "GhiChu", length = 255)
    private String ghiChu;
}
