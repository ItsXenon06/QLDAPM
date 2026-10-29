package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NhanVienRepository extends JpaRepository<NhanVien, Integer> {

    Optional<NhanVien> findByMaSoNhanVienAndNgayXoaIsNull(String maSoNhanVien);

    Optional<NhanVien> findByEmailAndNgayXoaIsNull(String email);

    List<NhanVien> findByVaiTro_MaVaiTroAndNgayXoaIsNull(Integer maVaiTro);

    Page<NhanVien> findByNgayXoaIsNull(Pageable pageable);
}
