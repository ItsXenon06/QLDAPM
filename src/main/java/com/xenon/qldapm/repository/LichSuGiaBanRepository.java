package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LichSuGiaBanRepository extends JpaRepository<LichSuGiaBan, Integer> {

    List<LichSuGiaBan> findBySanPham_MaSanPhamOrderByNgayHieuLucDesc(Integer maSanPham);

    Optional<LichSuGiaBan> findFirstBySanPham_MaSanPhamOrderByNgayHieuLucDesc(Integer maSanPham);
}
