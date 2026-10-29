package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Yêu cầu bảo hành (chỉ tiếp nhận & tra cứu). */
@Entity
@Table(name = "YeuCauBaoHanh")
@Getter
@Setter
@NoArgsConstructor
public class YeuCauBaoHanh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaYeuCau")
    private Integer maYeuCau;

    @Column(name = "MaSoTiepNhan", nullable = false, length = 50)
    private String maSoTiepNhan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaBaoHanh", nullable = false)
    private BaoHanh baoHanh;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NhanVienTiepNhan", nullable = false)
    private NhanVien nhanVienTiepNhan;

    @Column(name = "TenNguoiGui", nullable = false, length = 150)
    private String tenNguoiGui;

    @Column(name = "SoDienThoaiNguoiGui", nullable = false, length = 20)
    private String soDienThoaiNguoiGui;

    @Column(name = "MoTaSuCo", nullable = false, length = 2000)
    private String moTaSuCo;

    @Column(name = "TinhTrangNhanMay", length = 500)
    private String tinhTrangNhanMay;

    @Column(name = "PhuKienKemTheo", length = 500)
    private String phuKienKemTheo;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "tiep_nhan";

    @Column(name = "KetQuaXuLy", length = 2000)
    private String ketQuaXuLy;

    @Column(name = "NgayHenTra")
    private LocalDateTime ngayHenTra;

    @Column(name = "TiepNhanLuc", nullable = false)
    private LocalDateTime tiepNhanLuc = LocalDateTime.now();

    @Column(name = "HoanTatLuc")
    private LocalDateTime hoanTatLuc;

    @Column(name = "TraKhachLuc")
    private LocalDateTime traKhachLuc;
}
