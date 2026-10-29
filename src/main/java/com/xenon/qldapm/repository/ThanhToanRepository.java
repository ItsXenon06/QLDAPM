package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThanhToanRepository extends JpaRepository<ThanhToan, Integer> {

    List<ThanhToan> findByHoaDon_MaHoaDon(Integer maHoaDon);

    Optional<ThanhToan> findByMaThamChieu(String maThamChieu);
}
