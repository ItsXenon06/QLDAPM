package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Chương trình khuyến mãi: giảm %, giảm tiền, mua kèm giá sốc, tặng quà, tặng voucher. */
@Entity
@Table(name = "KhuyenMai")
@Getter
@Setter
@NoArgsConstructor
public class KhuyenMai {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaKhuyenMai")
    private Integer maKhuyenMai;

    @Column(name = "TenChuongTrinh", nullable = false, length = 255)
    private String tenChuongTrinh;

    @Column(name = "MaCode", length = 50)
    private String maCode;

    @Column(name = "LoaiKhuyenMai", nullable = false, length = 30)
    private String loaiKhuyenMai;

    @Column(name = "PhanTramGiam")
    private BigDecimal phanTramGiam;

    @Column(name = "SoTienGiamToiDa")
    private BigDecimal soTienGiamToiDa;

    @Column(name = "GiamCodinh")
    private BigDecimal giamCodinh;

    @Column(name = "GiaTriVoucher")
    private BigDecimal giaTriVoucher;

    @Column(name = "GiaTriDonToiThieu", nullable = false)
    private BigDecimal giaTriDonToiThieu = BigDecimal.ZERO;

    @Column(name = "TuDongApDung", nullable = false)
    private Boolean tuDongApDung = true;

    @Column(name = "ChoPhepCongDon", nullable = false)
    private Boolean choPhepCongDon = false;

    @Column(name = "DoUuTien", nullable = false)
    private Integer doUuTien = 0;

    @Column(name = "ApDungToanBo", nullable = false)
    private Boolean apDungToanBo = false;

    @Column(name = "SoLuongToiDa")
    private Integer soLuongToiDa;

    @Column(name = "SoLuongDaDung", nullable = false)
    private Integer soLuongDaDung = 0;

    @Column(name = "NgayBatDau", nullable = false)
    private LocalDateTime ngayBatDau;

    @Column(name = "NgayKetThuc", nullable = false)
    private LocalDateTime ngayKetThuc;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "hoat_dong";

    @Column(name = "MoTa", length = 2000)
    private String moTa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NguoiTao", nullable = false)
    private NhanVien nguoiTao;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @OneToMany(mappedBy = "khuyenMai", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KhuyenMaiSanPham> sanPhams = new ArrayList<>();

    public void addKhuyenMaiSanPham(KhuyenMaiSanPham item) {
        sanPhams.add(item);
        item.setKhuyenMai(this);
    }
}
