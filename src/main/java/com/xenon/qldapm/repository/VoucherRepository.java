package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByMaCode(String maCode);

    List<Voucher> findByKhachHang_MaKhachHangAndTrangThai(Integer maKhachHang, String trangThai);
}
