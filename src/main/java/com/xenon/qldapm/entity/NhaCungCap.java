package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Nhà cung cấp. */
@Entity
@Table(name = "NhaCungCap")
@Getter
@Setter
@NoArgsConstructor
public class NhaCungCap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaNCC")
    private Integer maNCC;

    @Column(name = "TenNCC", nullable = false, length = 150)
    private String tenNCC;

    @Column(name = "Email", length = 255)
    private String email;

    @Column(name = "SoDienThoai", length = 20)
    private String soDienThoai;

    @Column(name = "DiaChi", length = 500)
    private String diaChi;

    @Column(name = "NguoiLienHe", length = 150)
    private String nguoiLienHe;

    @Column(name = "MaSoThue", length = 20)
    private String maSoThue;

    @Column(name = "TrangThai", nullable = false)
    private Boolean trangThai = true;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
