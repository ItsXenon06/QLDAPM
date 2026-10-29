package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhieuKiemKeRepository extends JpaRepository<PhieuKiemKe, Integer> {

    Optional<PhieuKiemKe> findByMaSoPhieu(String maSoPhieu);

    Page<PhieuKiemKe> findByTrangThai(String trangThai, Pageable pageable);

    @Query("select p from PhieuKiemKe p left join fetch p.chiTiets where p.maPhieuKiemKe = :id")
    Optional<PhieuKiemKe> findWithChiTiets(@Param("id") Integer id);
}
