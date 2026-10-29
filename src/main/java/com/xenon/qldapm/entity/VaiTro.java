package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

/** Vai trò người dùng hệ thống. */
@Entity
@Table(name = "VaiTro")
@Getter
@Setter
@NoArgsConstructor
public class VaiTro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaVaiTro")
    private Integer maVaiTro;

    @Column(name = "TenVaiTro", nullable = false, length = 50)
    private String tenVaiTro;

    @Column(name = "MoTa", length = 500)
    private String moTa;

    @Column(name = "TrangThai", nullable = false)
    private Boolean trangThai = true;

    @ManyToMany
    @JoinTable(name = "VaiTroQuyen",
            joinColumns = @JoinColumn(name = "MaVaiTro"),
            inverseJoinColumns = @JoinColumn(name = "MaQuyen"))
    private Set<Quyen> quyens = new HashSet<>();
}
