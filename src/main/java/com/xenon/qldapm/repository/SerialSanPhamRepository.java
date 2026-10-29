package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SerialSanPhamRepository extends JpaRepository<SerialSanPham, Integer> {

    Optional<SerialSanPham> findBySoSerial(String soSerial);

    Optional<SerialSanPham> findByImei(String imei);

    boolean existsBySoSerial(String soSerial);

    /** POS: thu ngân gõ/quét Serial hoặc IMEI (cả IMEI2) vào cùng 1 ô tìm kiếm. */
    @Query("select s from SerialSanPham s where s.soSerial = :code or s.imei = :code or s.imei2 = :code")
    Optional<SerialSanPham> findByAnyCode(@Param("code") String code);

    /** Khóa dòng khi chốt bán, chống 2 quầy cùng bán 1 thiết bị. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerialSanPham s where s.maSerial = :id")
    Optional<SerialSanPham> findByIdForUpdate(@Param("id") Integer id);

    Page<SerialSanPham> findBySanPham_MaSanPhamAndTrangThai(Integer maSanPham, String trangThai, Pageable pageable);

    long countBySanPham_MaSanPhamAndTrangThai(Integer maSanPham, String trangThai);

    /** Báo cáo hàng tồn lâu ngày. */
    @Query("select s from SerialSanPham s join fetch s.sanPham "
            + "where s.trangThai = 'trong_kho' and s.ngayNhap < :truoc order by s.ngayNhap")
    List<SerialSanPham> findTonLau(@Param("truoc") LocalDateTime truoc);
}
