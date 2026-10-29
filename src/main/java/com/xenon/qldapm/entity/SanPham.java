package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

/** Mỗi dòng là MỘT SKU bán được. Giá niêm yết đã gồm VAT. */
@Entity
@Table(name = "SanPham")
@Getter
@Setter
@NoArgsConstructor
public class SanPham {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaSanPham")
    private Integer maSanPham;

    @Column(name = "MaSKU", nullable = false, length = 60)
    private String maSKU;

    @Column(name = "MaVach", length = 60)
    private String maVach;

    @Column(name = "TenSanPham", nullable = false, length = 255)
    private String tenSanPham;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaDanhMuc", nullable = false)
    private DanhMuc danhMuc;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaThuongHieu", nullable = false)
    private ThuongHieu thuongHieu;

    @Column(name = "DonViTinh", nullable = false, length = 20)
    private String donViTinh = "cái";

    @Column(name = "MoTa", length = 4000)
    private String moTa;

    @Column(name = "ThongSoKyThuat")
    private String thongSoKyThuat;

    @Column(name = "GiaNiemYet", nullable = false)
    private BigDecimal giaNiemYet;

    @Column(name = "GiaToiThieu")
    private BigDecimal giaToiThieu;

    @Column(name = "ThueSuatVAT", nullable = false)
    private BigDecimal thueSuatVAT = new BigDecimal("10");

    @Column(name = "BaoHanhThang", nullable = false)
    private Integer baoHanhThang = 0;

    @Column(name = "CoTheoDoiSerial", nullable = false)
    private Boolean coTheoDoiSerial = false;

    @Column(name = "TrangThai", nullable = false, length = 30)
    private String trangThai = "dang_kinh_doanh";

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "CapNhatLuc", nullable = false)
    private LocalDateTime capNhatLuc = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
