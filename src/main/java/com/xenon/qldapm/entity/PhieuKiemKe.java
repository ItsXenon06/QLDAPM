package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Phiếu kiểm kê kho. */
@Entity
@Table(name = "PhieuKiemKe")
@Getter
@Setter
@NoArgsConstructor
public class PhieuKiemKe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaPhieuKiemKe")
    private Integer maPhieuKiemKe;

    @Column(name = "MaSoPhieu", nullable = false, length = 50)
    private String maSoPhieu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NguoiLap", nullable = false)
    private NhanVien nguoiLap;

    @Column(name = "TrangThai", nullable = false, length = 20)
    private String trangThai = "dang_kiem";

    @Column(name = "NgayKiemKe", nullable = false)
    private LocalDateTime ngayKiemKe = LocalDateTime.now();

    @Column(name = "HoanThanhLuc")
    private LocalDateTime hoanThanhLuc;

    @Column(name = "GhiChu", length = 500)
    private String ghiChu;

    @OneToMany(mappedBy = "phieuKiemKe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChiTietKiemKe> chiTiets = new ArrayList<>();

    public void addChiTietKiemKe(ChiTietKiemKe item) {
        chiTiets.add(item);
        item.setPhieuKiemKe(this);
    }
}
