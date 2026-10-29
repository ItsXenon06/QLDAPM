package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThuongHieuRepository extends JpaRepository<ThuongHieu, Integer> {

    Optional<ThuongHieu> findByTenThuongHieu(String tenThuongHieu);

    List<ThuongHieu> findByNgayXoaIsNull();
}
