package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChiTietKiemKeRepository extends JpaRepository<ChiTietKiemKe, Integer> {

    List<ChiTietKiemKe> findByPhieuKiemKe_MaPhieuKiemKe(Integer maPhieuKiemKe);
}
