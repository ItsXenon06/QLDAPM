package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuayPOSRepository extends JpaRepository<QuayPOS, Integer> {

    Optional<QuayPOS> findByMaQuayCode(String maQuayCode);

    Optional<QuayPOS> findByDiaChiIP(String diaChiIP);

    List<QuayPOS> findByTrangThai(String trangThai);
}
