package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhieuTraHangRepository extends JpaRepository<PhieuTraHang, Integer> {

    Optional<PhieuTraHang> findByMaSoPhieu(String maSoPhieu);

    List<PhieuTraHang> findByHoaDon_MaHoaDon(Integer maHoaDon);

    Page<PhieuTraHang> findByTrangThai(String trangThai, Pageable pageable);

    /** Hóa đơn đã có phiếu trả nào chưa bị từ chối? */
    boolean existsByHoaDon_MaHoaDonAndTrangThaiNot(Integer maHoaDon, String trangThai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PhieuTraHang p where p.maPhieuTra = :id")
    Optional<PhieuTraHang> findByIdForUpdate(@Param("id") Integer id);
}