package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Sổ cái biến động tồn kho (chỉ thêm, không sửa). */
@Entity
@Table(name = "LichSuTonKho")
@Getter
@Setter
@NoArgsConstructor
public class LichSuTonKho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaLichSu")
    private Long maLichSu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaTonKho", nullable = false)
    private TonKho tonKho;

    @Column(name = "LoaiBienDong", nullable = false, length = 30)
    private String loaiBienDong;

    @Column(name = "SoBienDong", nullable = false)
    private Integer soBienDong;

    @Column(name = "SLSau", nullable = false)
    private Integer slSau;

    @Column(name = "LoaiTaiLieu", length = 30)
    private String loaiTaiLieu;

    @Column(name = "MaTaiLieu")
    private Integer maTaiLieu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ThucHienBoi")
    private NhanVien thucHienBoi;

    @Column(name = "GhiChu", length = 255)
    private String ghiChu;

    @Column(name = "ThoiGian", nullable = false)
    private LocalDateTime thoiGian = LocalDateTime.now();
}
