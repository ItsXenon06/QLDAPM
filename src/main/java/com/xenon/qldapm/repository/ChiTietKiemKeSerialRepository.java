package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietKiemKeSerialRepository extends JpaRepository<ChiTietKiemKeSerial, Integer> {

    List<ChiTietKiemKeSerial> findByChiTietKiemKe_MaChiTiet(Integer maChiTiet);
}
