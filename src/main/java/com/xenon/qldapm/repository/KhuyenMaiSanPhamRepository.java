package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KhuyenMaiSanPhamRepository extends JpaRepository<KhuyenMaiSanPham, Integer> {

    List<KhuyenMaiSanPham> findByKhuyenMai_MaKhuyenMai(Integer maKhuyenMai);

    List<KhuyenMaiSanPham> findByKhuyenMai_MaKhuyenMaiAndVaiTro(Integer maKhuyenMai, String vaiTro);
}
