package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LichSuSerialRepository extends JpaRepository<LichSuSerial, Long> {

    List<LichSuSerial> findBySerial_MaSerialOrderByThoiGianDesc(Integer maSerial);
}
