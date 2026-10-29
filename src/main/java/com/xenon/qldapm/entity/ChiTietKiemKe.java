package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chi tiết kiểm kê theo SKU. */
@Entity
@Table(name = "ChiTietKiemKe")
@Getter
@Setter
@NoArgsConstructor
public class ChiTietKiemKe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaChiTiet")
    private Integer maChiTiet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaPhieuKiemKe", nullable = false)
    private PhieuKiemKe phieuKiemKe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSanPham", nullable = false)
    private SanPham sanPham;

    @Column(name = "SLHeThong", nullable = false)
    private Integer slHeThong;

    @Column(name = "SLThucTe")
    private Integer slThucTe;

    // Cột tính toán (computed) trong SQL Server: chỉ đọc
    @Column(name = "ChenhLech", insertable = false, updatable = false)
    private Integer chenhLech;

    @Column(name = "LyDoChenhLech", length = 255)
    private String lyDoChenhLech;

    @Column(name = "DaDieuChinh", nullable = false)
    private Boolean daDieuChinh = false;
}
