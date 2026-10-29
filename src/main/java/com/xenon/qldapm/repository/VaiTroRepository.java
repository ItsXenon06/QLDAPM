package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaiTroRepository extends JpaRepository<VaiTro, Integer> {

    Optional<VaiTro> findByTenVaiTro(String tenVaiTro);
}
