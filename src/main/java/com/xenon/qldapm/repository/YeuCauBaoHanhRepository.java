package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YeuCauBaoHanhRepository extends JpaRepository<YeuCauBaoHanh, Integer> {

    Optional<YeuCauBaoHanh> findByMaSoTiepNhan(String maSoTiepNhan);

    List<YeuCauBaoHanh> findBySoDienThoaiNguoiGui(String soDienThoai);

    List<YeuCauBaoHanh> findByBaoHanh_MaBaoHanh(Integer maBaoHanh);

    Page<YeuCauBaoHanh> findByTrangThai(String trangThai, Pageable pageable);
}
