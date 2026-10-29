package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuyenRepository extends JpaRepository<Quyen, Integer> {

    Optional<Quyen> findByMaCode(String maCode);

    List<Quyen> findByNhom(String nhom);
}
