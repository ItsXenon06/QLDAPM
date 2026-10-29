package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Vòng đời từng Serial (nhập -> bán -> bảo hành...). */
@Entity
@Table(name = "LichSuSerial")
@Getter
@Setter
@NoArgsConstructor
public class LichSuSerial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaLichSu")
    private Long maLichSu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSerial", nullable = false)
    private SerialSanPham serial;

    @Column(name = "TrangThaiCu", length = 20)
    private String trangThaiCu;

    @Column(name = "TrangThaiMoi", nullable = false, length = 20)
    private String trangThaiMoi;

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
