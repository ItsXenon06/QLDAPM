package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chính sách bảo hành. */
@Entity
@Table(name = "ChinhSachBaoHanh")
@Getter
@Setter
@NoArgsConstructor
public class ChinhSachBaoHanh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaChinhSach")
    private Integer maChinhSach;

    @Column(name = "TenChinhSach", nullable = false, length = 150)
    private String tenChinhSach;

    @Column(name = "ThoiHanThang", nullable = false)
    private Integer thoiHanThang;

    @Column(name = "NoiDung", length = 4000)
    private String noiDung;

    @Column(name = "TrangThai", nullable = false)
    private Boolean trangThai = true;
}
