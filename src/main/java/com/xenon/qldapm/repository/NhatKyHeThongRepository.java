package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NhatKyHeThongRepository extends JpaRepository<NhatKyHeThong, Long> {

    Page<NhatKyHeThong> findByNhanVien_MaNhanVienOrderByThoiGianDesc(Integer maNhanVien, Pageable pageable);

    List<NhatKyHeThong> findByBangTacDongAndMaBanGhiOrderByThoiGianDesc(String bangTacDong, String maBanGhi);

    Page<NhatKyHeThong> findByThoiGianBetweenOrderByThoiGianDesc(LocalDateTime tu, LocalDateTime den, Pageable pageable);
}
