package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Ngành hàng dạng cây (cột MaDanhMucChaNN là cột tính toán, không map). */
@Entity
@Table(name = "DanhMuc")
@Getter
@Setter
@NoArgsConstructor
public class DanhMuc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaDanhMuc")
    private Integer maDanhMuc;

    @Column(name = "TenDanhMuc", nullable = false, length = 100)
    private String tenDanhMuc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaDanhMucCha")
    private DanhMuc danhMucCha;

    @Column(name = "MoTa", length = 2000)
    private String moTa;

    @Column(name = "ThuTuHienThi", nullable = false)
    private Integer thuTuHienThi = 0;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();

    @Column(name = "NgayXoa")
    private LocalDateTime ngayXoa;
}
