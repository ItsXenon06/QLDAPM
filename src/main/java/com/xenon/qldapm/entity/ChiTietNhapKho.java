package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/** Chi tiết phiếu nhập kho. */
@Entity
@Table(name = "ChiTietNhapKho")
@Getter
@Setter
@NoArgsConstructor
public class ChiTietNhapKho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaChiTiet")
    private Integer maChiTiet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaPhieuNhap", nullable = false)
    private PhieuNhapKho phieuNhap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSanPham", nullable = false)
    private SanPham sanPham;

    @Column(name = "SLDatHang", nullable = false)
    private Integer slDatHang;

    @Column(name = "SLDaNhan", nullable = false)
    private Integer slDaNhan = 0;

    @Column(name = "GiaNhap", nullable = false)
    private BigDecimal giaNhap;

    @Column(name = "ChietKhauPhanTram", nullable = false)
    private BigDecimal chietKhauPhanTram = BigDecimal.ZERO;

    @Column(name = "ThueSuat", nullable = false)
    private BigDecimal thueSuat = new BigDecimal("10");

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "ThanhTien", insertable = false, updatable = false)
    private BigDecimal thanhTien;

    @Column(name = "GhiChu", length = 255)
    private String ghiChu;
}
