package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoaDonKhuyenMaiRepository extends JpaRepository<HoaDonKhuyenMai, Integer> {

    List<HoaDonKhuyenMai> findByHoaDon_MaHoaDon(Integer maHoaDon);
}
