package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Bảo hành phát sinh khi bán: 1 dòng cho mỗi thiết bị/dòng hàng. */
@Entity
@Table(name = "BaoHanh")
@Getter
@Setter
@NoArgsConstructor
public class BaoHanh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaBaoHanh")
    private Integer maBaoHanh;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaChiTietHD", nullable = false)
    private ChiTietHoaDon chiTietHoaDon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaSerial")
    private SerialSanPham serial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaChinhSach")
    private ChinhSachBaoHanh chinhSach;

    @Column(name = "LoaiBaoHanh", nullable = false, length = 20)
    private String loaiBaoHanh = "hang";

    @Column(name = "NgayBatDau", nullable = false)
    private LocalDateTime ngayBatDau;

    @Column(name = "NgayKetThuc", nullable = false)
    private LocalDateTime ngayKetThuc;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "con_han";

    @Column(name = "TaoLuc", nullable = false)
    private LocalDateTime taoLuc = LocalDateTime.now();
}
