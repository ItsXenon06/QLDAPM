package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/** Phạm vi/điều kiện KM. VaiTro: 'dieu_kien' | 'uu_dai'. Đúng 1 trong (sanPham, danhMuc). */
@Entity
@Table(name = "KhuyenMaiSanPham")
@Getter
@Setter
@NoArgsConstructor
public class KhuyenMaiSanPham {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaKMSP")
    private Integer maKMSP;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaKhuyenMai", nullable = false)
    private KhuyenMai khuyenMai;

    @Column(name = "VaiTro", nullable = false, length = 20)
    private String vaiTro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaSanPham")
    private SanPham sanPham;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaDanhMuc")
    private DanhMuc danhMuc;

    @Column(name = "SoLuong", nullable = false)
    private Integer soLuong = 1;

    @Column(name = "GiaUuDai")
    private BigDecimal giaUuDai;
}
