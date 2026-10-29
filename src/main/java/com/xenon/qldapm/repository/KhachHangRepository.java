package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KhachHangRepository extends JpaRepository<KhachHang, Integer> {

    Optional<KhachHang> findBySoDienThoaiAndNgayXoaIsNull(String soDienThoai);

    @Query("select k from KhachHang k where k.ngayXoa is null and "
            + "(k.hoTen like concat('%', :kw, '%') or k.soDienThoai like concat('%', :kw, '%'))")
    Page<KhachHang> search(@Param("kw") String keyword, Pageable pageable);
}
