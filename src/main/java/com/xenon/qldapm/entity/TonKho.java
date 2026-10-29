package com.xenon.qldapm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

/** Tồn kho theo SKU. */
@Entity
@Table(name = "TonKho")
@Getter
@Setter
@NoArgsConstructor
public class TonKho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaTonKho")
    private Integer maTonKho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaSanPham", nullable = false)
    private SanPham sanPham;

    @Column(name = "SLTon", nullable = false)
    private Integer slTon = 0;

    @Column(name = "TonToiThieu", nullable = false)
    private Integer tonToiThieu = 0;

    @Column(name = "TonToiDa")
    private Integer tonToiDa;

    @Column(name = "ViTriKho", length = 100)
    private String viTriKho;

    @UpdateTimestamp
    @Column(name = "CapNhatLuc", nullable = false)
    private LocalDateTime capNhatLuc = LocalDateTime.now();
}
