package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SanPhamRepository extends JpaRepository<SanPham, Integer> {

    Optional<SanPham> findByMaSKUAndNgayXoaIsNull(String maSKU);

    Optional<SanPham> findByMaVachAndNgayXoaIsNull(String maVach);

    boolean existsByMaSKUAndNgayXoaIsNull(String maSKU);

    boolean existsByMaVachAndNgayXoaIsNull(String maVach);

    Page<SanPham> findByDanhMuc_MaDanhMucAndNgayXoaIsNull(Integer maDanhMuc, Pageable pageable);

    Page<SanPham> findByThuongHieu_MaThuongHieuAndNgayXoaIsNull(Integer maThuongHieu, Pageable pageable);

    /** Tìm nhanh theo tên / SKU / mã vạch (collation Vietnamese_CI_AS đã không phân biệt hoa thường). */
    @Query("select s from SanPham s where s.ngayXoa is null and "
            + "(s.tenSanPham like concat('%', :kw, '%') or s.maSKU like concat('%', :kw, '%') or s.maVach = :kw)")
    Page<SanPham> search(@Param("kw") String keyword, Pageable pageable);
}
