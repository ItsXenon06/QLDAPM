package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Nhật ký thao tác nhạy cảm (đổi giá, hủy hóa đơn, điều chỉnh kho...). */
@Entity
@Table(name = "NhatKyHeThong")
@Getter
@Setter
@NoArgsConstructor
public class NhatKyHeThong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaNhatKy")
    private Long maNhatKy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaNhanVien")
    private NhanVien nhanVien;

    @Column(name = "HanhDong", nullable = false, length = 50)
    private String hanhDong;

    @Column(name = "BangTacDong", length = 100)
    private String bangTacDong;

    @Column(name = "MaBanGhi", length = 50)
    private String maBanGhi;

    @Column(name = "GiaTriCu")
    private String giaTriCu;

    @Column(name = "GiaTriMoi")
    private String giaTriMoi;

    @Column(name = "DiaChiIP", length = 45)
    private String diaChiIP;

    @Column(name = "ThoiGian", nullable = false)
    private LocalDateTime thoiGian = LocalDateTime.now();
}
