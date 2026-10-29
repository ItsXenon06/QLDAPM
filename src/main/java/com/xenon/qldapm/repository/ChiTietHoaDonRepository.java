package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietHoaDonRepository extends JpaRepository<ChiTietHoaDon, Integer> {

    List<ChiTietHoaDon> findByHoaDon_MaHoaDon(Integer maHoaDon);

    Optional<ChiTietHoaDon> findBySerial_MaSerialAndHieuLucTrue(Integer maSerial);

    boolean existsBySerial_MaSerialAndHieuLucTrue(Integer maSerial);
}
