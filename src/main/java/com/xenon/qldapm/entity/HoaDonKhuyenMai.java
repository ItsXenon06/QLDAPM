package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Khuyến mãi đã áp dụng thực tế vào hóa đơn. */
@Entity
@Table(name = "HoaDonKhuyenMai")
@Getter
@Setter
@NoArgsConstructor
public class HoaDonKhuyenMai {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaHDKM")
    private Integer maHDKM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaHoaDon", nullable = false)
    private HoaDon hoaDon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaChiTiet")
    private ChiTietHoaDon chiTiet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaKhuyenMai", nullable = false)
    private KhuyenMai khuyenMai;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaVoucher")
    private Voucher voucher;

    @Column(name = "SoTienGiam", nullable = false)
    private BigDecimal soTienGiam = BigDecimal.ZERO;

    @Column(name = "ApDungLuc", nullable = false)
    private LocalDateTime apDungLuc = LocalDateTime.now();
}
