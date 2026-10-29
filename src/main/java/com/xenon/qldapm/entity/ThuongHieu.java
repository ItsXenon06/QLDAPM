package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Thương hiệu. */
@Entity
@Table(name = "ThuongHieu")
@Getter
@Setter
@NoArgsConstructor
public class ThuongHieu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaThuongHieu")
    private Integer maThuongHieu;

    @Column(name = "TenThuongHieu", nullable = false, length = 100)
    private String tenThuongHieu;

    @Column(name = "QuocGia", length = 100)
    private String quocGia;

    @Column(name = "MoTa", length = 2000)
    private String moTa;

    @Column(name = "TrangThai", nullable = false)
    private Boolean trangThai = true;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
