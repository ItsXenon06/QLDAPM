package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChinhSachBaoHanhRepository extends JpaRepository<ChinhSachBaoHanh, Integer> {

    Optional<ChinhSachBaoHanh> findByTenChinhSach(String tenChinhSach);

    List<ChinhSachBaoHanh> findByTrangThaiTrue();
}
