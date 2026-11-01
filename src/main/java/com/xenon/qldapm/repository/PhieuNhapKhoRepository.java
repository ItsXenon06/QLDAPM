package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhieuNhapKhoRepository extends JpaRepository<PhieuNhapKho, Integer> {

    Optional<PhieuNhapKho> findByMaSoPhieu(String maSoPhieu);

    Page<PhieuNhapKho> findByTrangThai(String trangThai, Pageable pageable);

    Page<PhieuNhapKho> findByNcc_MaNCC(Integer maNCC, Pageable pageable);

    @Query("select p from PhieuNhapKho p left join fetch p.chiTiets where p.maPhieuNhap = :id")
    Optional<PhieuNhapKho> findWithChiTiets(@Param("id") Integer id);

    /** Khóa phiếu khi nhận hàng/hủy, chống 2 người nhận cùng 1 dòng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PhieuNhapKho p where p.maPhieuNhap = :id")
    Optional<PhieuNhapKho> findByIdForUpdate(@Param("id") Integer id);
}