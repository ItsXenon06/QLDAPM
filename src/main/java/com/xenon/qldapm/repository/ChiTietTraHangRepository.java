package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietTraHangRepository extends JpaRepository<ChiTietTraHang, Integer> {

    List<ChiTietTraHang> findByPhieuTra_MaPhieuTra(Integer maPhieuTra);

    List<ChiTietTraHang> findByChiTietHoaDon_MaChiTiet(Integer maChiTietHD);
}
