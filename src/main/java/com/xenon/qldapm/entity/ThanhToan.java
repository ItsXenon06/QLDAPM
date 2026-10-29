package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Thanh toán (một hóa đơn có thể chia nhiều phương thức). */
@Entity
@Table(name = "ThanhToan")
@Getter
@Setter
@NoArgsConstructor
public class ThanhToan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaThanhToan")
    private Integer maThanhToan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaHoaDon", nullable = false)
    private HoaDon hoaDon;

    @Column(name = "PhuongThuc", nullable = false, length = 20)
    private String phuongThuc;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "cho_xu_ly";

    @Column(name = "SoTien", nullable = false)
    private BigDecimal soTien;

    @Column(name = "TienKhachDua")
    private BigDecimal tienKhachDua;

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "TienThoiLai", insertable = false, updatable = false)
    private BigDecimal tienThoiLai;

    @Column(name = "MaThamChieu", length = 100)
    private String maThamChieu;

    @Column(name = "DonViTraGop", length = 100)
    private String donViTraGop;

    @Column(name = "SoKyHan")
    private Integer soKyHan;

    @Column(name = "ThanhToanLuc")
    private LocalDateTime thanhToanLuc;

    @Column(name = "TaoLuc", nullable = false)
    private LocalDateTime taoLuc = LocalDateTime.now();
}
