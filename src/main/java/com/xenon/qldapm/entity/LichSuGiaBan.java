package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lịch sử thay đổi giá niêm yết / giá sàn. */
@Entity
@Table(name = "LichSuGiaBan")
@Getter
@Setter
@NoArgsConstructor
public class LichSuGiaBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaLichSuGia")
    private Integer maLichSuGia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSanPham", nullable = false)
    private SanPham sanPham;

    @Column(name = "GiaNiemYet", nullable = false)
    private BigDecimal giaNiemYet;

    @Column(name = "GiaToiThieu")
    private BigDecimal giaToiThieu;

    @Column(name = "NgayHieuLuc", nullable = false)
    private LocalDateTime ngayHieuLuc = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NguoiThayDoi")
    private NhanVien nguoiThayDoi;

    @Column(name = "GhiChu", length = 255)
    private String ghiChu;
}
