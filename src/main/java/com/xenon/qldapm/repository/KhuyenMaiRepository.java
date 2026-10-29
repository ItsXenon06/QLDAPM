package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KhuyenMaiRepository extends JpaRepository<KhuyenMai, Integer> {

    Optional<KhuyenMai> findByMaCode(String maCode);

    Page<KhuyenMai> findByTrangThai(String trangThai, Pageable pageable);

    /** KM tự động áp dụng đang có hiệu lực và còn lượt dùng, ưu tiên cao xếp trước. */
    @Query("select k from KhuyenMai k where k.trangThai = 'hoat_dong' and k.tuDongApDung = true "
            + "and k.ngayBatDau <= :now and k.ngayKetThuc > :now "
            + "and (k.soLuongToiDa is null or k.soLuongDaDung < k.soLuongToiDa) "
            + "order by k.doUuTien desc")
    List<KhuyenMai> findDangHoatDongTuDong(@Param("now") LocalDateTime now);
}
