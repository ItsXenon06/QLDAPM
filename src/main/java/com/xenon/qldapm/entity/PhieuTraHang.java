package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Phiếu trả hàng tại quầy. */
@Entity
@Table(name = "PhieuTraHang")
@Getter
@Setter
@NoArgsConstructor
public class PhieuTraHang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaPhieuTra")
    private Integer maPhieuTra;

    @Column(name = "MaSoPhieu", nullable = false, length = 50)
    private String maSoPhieu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaHoaDon", nullable = false)
    private HoaDon hoaDon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NguoiXuLy", nullable = false)
    private NhanVien nguoiXuLy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NguoiDuyet")
    private NhanVien nguoiDuyet;

    @Column(name = "LyDo", nullable = false, length = 500)
    private String lyDo;

    @Column(name = "PhuongThucHoan", length = 20)
    private String phuongThucHoan;

    @Column(name = "TongTienHoan", nullable = false)
    private BigDecimal tongTienHoan = BigDecimal.ZERO;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "cho_duyet";

    @Column(name = "TaoLuc", nullable = false)
    private LocalDateTime taoLuc = LocalDateTime.now();

    @Column(name = "DuyetLuc")
    private LocalDateTime duyetLuc;

    @Column(name = "HoanTatLuc")
    private LocalDateTime hoanTatLuc;

    @OneToMany(mappedBy = "phieuTra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChiTietTraHang> chiTiets = new ArrayList<>();

    public void addChiTietTraHang(ChiTietTraHang item) {
        chiTiets.add(item);
        item.setPhieuTra(this);
    }
}
