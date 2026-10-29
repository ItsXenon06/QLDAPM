package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietNhapKhoRepository extends JpaRepository<ChiTietNhapKho, Integer> {

    List<ChiTietNhapKho> findByPhieuNhap_MaPhieuNhap(Integer maPhieuNhap);
}
