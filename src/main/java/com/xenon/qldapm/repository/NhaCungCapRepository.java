package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NhaCungCapRepository extends JpaRepository<NhaCungCap, Integer> {

    Optional<NhaCungCap> findByMaSoThueAndNgayXoaIsNull(String maSoThue);

    List<NhaCungCap> findByNgayXoaIsNull();
}
