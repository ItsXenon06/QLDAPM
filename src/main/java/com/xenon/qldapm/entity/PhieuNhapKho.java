package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.UpdateTimestamp;

/** Phiếu nhập kho (lập thủ công). */
@Entity
@Table(name = "PhieuNhapKho")
@Getter
@Setter
@NoArgsConstructor
public class PhieuNhapKho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaPhieuNhap")
    private Integer maPhieuNhap;

    @Column(name = "MaSoPhieu", nullable = false, length = 50)
    private String maSoPhieu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaNCC", nullable = false)
    private NhaCungCap ncc;

    @Column(name = "SoChungTuNCC", length = 50)
    private String soChungTuNCC;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "nhap";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NguoiLap", nullable = false)
    private NhanVien nguoiLap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NguoiNhanHang")
    private NhanVien nguoiNhanHang;

    @Column(name = "NgayDuKien")
    private LocalDateTime ngayDuKien;

    @Column(name = "NgayNhanThuc")
    private LocalDateTime ngayNhanThuc;

    @Column(name = "TongTienHang", nullable = false)
    private BigDecimal tongTienHang = BigDecimal.ZERO;

    @Column(name = "TongTienThue", nullable = false)
    private BigDecimal tongTienThue = BigDecimal.ZERO;

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "TongCong", insertable = false, updatable = false)
    private BigDecimal tongCong;

    @Column(name = "GhiChu", length = 500)
    private String ghiChu;

    @Column(name = "TaoLuc", nullable = false)
    private LocalDateTime taoLuc = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "CapNhatLuc", nullable = false)
    private LocalDateTime capNhatLuc = LocalDateTime.now();

    @OneToMany(mappedBy = "phieuNhap", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChiTietNhapKho> chiTiets = new ArrayList<>();

    public void addChiTietNhapKho(ChiTietNhapKho item) {
        chiTiets.add(item);
        item.setPhieuNhap(this);
    }
}
