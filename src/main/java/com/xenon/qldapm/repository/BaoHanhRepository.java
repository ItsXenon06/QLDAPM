package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaoHanhRepository extends JpaRepository<BaoHanh, Integer> {

    List<BaoHanh> findByChiTietHoaDon_MaChiTiet(Integer maChiTietHD);

    List<BaoHanh> findBySerial_MaSerial(Integer maSerial);

    List<BaoHanh> findByTrangThaiAndNgayKetThucBefore(String trangThai, LocalDateTime moc);
}
