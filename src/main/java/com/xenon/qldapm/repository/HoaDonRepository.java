package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoaDonRepository extends JpaRepository<HoaDon, Integer> {

    Optional<HoaDon> findBySoHoaDon(String soHoaDon);

    @Query("select h from HoaDon h left join fetch h.chiTiets where h.maHoaDon = :id")
    Optional<HoaDon> findWithChiTiets(@Param("id") Integer id);

    Page<HoaDon> findByQuay_MaQuayOrderByThoiGianTaoDesc(Integer maQuay, Pageable pageable);

    List<HoaDon> findByThuNgan_MaNhanVienAndTrangThai(Integer maThuNgan, String trangThai);

    Page<HoaDon> findByTrangThaiAndThoiGianThanhToanBetween(String trangThai, LocalDateTime tu, LocalDateTime den, Pageable pageable);

    /** Doanh thu trong khoảng [tu, den). */
    @Query("select coalesce(sum(h.tongThanhToan), 0) from HoaDon h "
            + "where h.trangThai = 'da_thanh_toan' and h.thoiGianThanhToan >= :tu and h.thoiGianThanhToan < :den")
    BigDecimal tongDoanhThu(@Param("tu") LocalDateTime tu, @Param("den") LocalDateTime den);
}
