package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kiểm kê đến từng Serial/IMEI. */
@Entity
@Table(name = "ChiTietKiemKeSerial")
@Getter
@Setter
@NoArgsConstructor
public class ChiTietKiemKeSerial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaChiTietSerial")
    private Integer maChiTietSerial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaChiTiet", nullable = false)
    private ChiTietKiemKe chiTietKiemKe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSerial", nullable = false)
    private SerialSanPham serial;

    @Column(name = "TrangThaiHeThong", nullable = false, length = 20)
    private String trangThaiHeThong;

    @Column(name = "TimThay")
    private Boolean timThay;

    @Column(name = "GhiChu", length = 255)
    private String ghiChu;
}
