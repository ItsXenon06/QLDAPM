package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

/** Mỗi thiết bị vật lý = 1 dòng. Serial/IMEI duy nhất toàn hệ thống (R01). */
@Entity
@Table(name = "SerialSanPham")
@Getter
@Setter
@NoArgsConstructor
public class SerialSanPham {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaSerial")
    private Integer maSerial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSanPham", nullable = false)
    private SanPham sanPham;

    @Column(name = "SoSerial", nullable = false, length = 100)
    private String soSerial;

    @Column(name = "IMEI", length = 15)
    private String imei;

    @Column(name = "IMEI2", length = 15)
    private String imei2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaChiTietNhap")
    private ChiTietNhapKho chiTietNhap;

    @Column(name = "GiaVon")
    private BigDecimal giaVon;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "trong_kho";

    @Column(name = "NgayNhap", nullable = false)
    private LocalDateTime ngayNhap = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "CapNhatLuc", nullable = false)
    private LocalDateTime capNhatLuc = LocalDateTime.now();
}
