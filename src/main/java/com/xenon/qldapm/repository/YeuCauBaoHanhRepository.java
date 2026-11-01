package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YeuCauBaoHanhRepository extends JpaRepository<YeuCauBaoHanh, Integer> {

    Optional<YeuCauBaoHanh> findByMaSoTiepNhan(String maSoTiepNhan);

    List<YeuCauBaoHanh> findBySoDienThoaiNguoiGui(String soDienThoai);

    List<YeuCauBaoHanh> findByBaoHanh_MaBaoHanh(Integer maBaoHanh);

    Page<YeuCauBaoHanh> findByTrangThai(String trangThai, Pageable pageable);

    boolean existsByBaoHanh_MaBaoHanhAndTrangThaiIn(Integer maBaoHanh, Collection<String> trangThais);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select y from YeuCauBaoHanh y where y.maYeuCau = :id")
    Optional<YeuCauBaoHanh> findByIdForUpdate(@Param("id") Integer id);
}