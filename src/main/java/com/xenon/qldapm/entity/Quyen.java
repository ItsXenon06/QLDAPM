package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Quyền chức năng (vd: POS_BAN_HANG, KHO_NHAP). */
@Entity
@Table(name = "Quyen")
@Getter
@Setter
@NoArgsConstructor
public class Quyen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaQuyen")
    private Integer maQuyen;

    @Column(name = "MaCode", nullable = false, length = 60)
    private String maCode;

    @Column(name = "TenQuyen", nullable = false, length = 150)
    private String tenQuyen;

    @Column(name = "Nhom", nullable = false, length = 50)
    private String nhom;

    @Column(name = "MoTa", length = 500)
    private String moTa;
}
