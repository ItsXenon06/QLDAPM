package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhieuTraHangRepository extends JpaRepository<PhieuTraHang, Integer> {

    Optional<PhieuTraHang> findByMaSoPhieu(String maSoPhieu);

    List<PhieuTraHang> findByHoaDon_MaHoaDon(Integer maHoaDon);

    Page<PhieuTraHang> findByTrangThai(String trangThai, Pageable pageable);
}
