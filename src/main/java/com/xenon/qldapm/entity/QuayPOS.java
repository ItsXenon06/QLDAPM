package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Quầy thu ngân POS. */
@Entity
@Table(name = "QuayPOS")
@Getter
@Setter
@NoArgsConstructor
public class QuayPOS {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaQuay")
    private Integer maQuay;

    @Column(name = "MaQuayCode", nullable = false, length = 20)
    private String maQuayCode;

    @Column(name = "TenQuay", nullable = false, length = 100)
    private String tenQuay;

    @Column(name = "ViTri", length = 100)
    private String viTri;

    @Column(name = "DiaChiIP", length = 45)
    private String diaChiIP;

    @Column(name = "TenMayQuet", length = 100)
    private String tenMayQuet;

    @Column(name = "TenMayIn", length = 100)
    private String tenMayIn;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "hoat_dong";

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();
}
