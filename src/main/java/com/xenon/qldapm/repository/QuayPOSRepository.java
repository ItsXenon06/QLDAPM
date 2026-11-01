package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuayPOSRepository extends JpaRepository<QuayPOS, Integer> {

    Optional<QuayPOS> findByMaQuayCode(String maQuayCode);

    Optional<QuayPOS> findByDiaChiIP(String diaChiIP);

    List<QuayPOS> findByTrangThai(String trangThai);

    /** Khóa quầy khi cấp số hóa đơn (số tăng liên tục theo quầy/ngày). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuayPOS q where q.maQuay = :id")
    Optional<QuayPOS> findByIdForUpdate(@Param("id") Integer id);
}