package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LichSuTonKhoRepository extends JpaRepository<LichSuTonKho, Long> {

    Page<LichSuTonKho> findByTonKho_MaTonKhoOrderByThoiGianDesc(Integer maTonKho, Pageable pageable);

    List<LichSuTonKho> findByLoaiTaiLieuAndMaTaiLieu(String loaiTaiLieu, Integer maTaiLieu);
}
