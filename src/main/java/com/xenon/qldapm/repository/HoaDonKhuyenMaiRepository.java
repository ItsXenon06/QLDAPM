package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoaDonKhuyenMaiRepository extends JpaRepository<HoaDonKhuyenMai, Integer> {

    List<HoaDonKhuyenMai> findByHoaDon_MaHoaDon(Integer maHoaDon);

    List<HoaDonKhuyenMai> findByChiTiet_MaChiTiet(Integer maChiTiet);

    /** Tổng giảm do khuyến mãi/voucher trên 1 dòng (phần còn lại của GiamGia là giảm tay). */
    @Query("select coalesce(sum(x.soTienGiam), 0) from HoaDonKhuyenMai x where x.chiTiet.maChiTiet = :id")
    BigDecimal tongGiamCuaDong(@Param("id") Integer id);
}