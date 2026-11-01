package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByMaCode(String maCode);

    List<Voucher> findByKhachHang_MaKhachHangAndTrangThai(Integer maKhachHang, String trangThai);

    List<Voucher> findByHoaDonPhatHanh_MaHoaDon(Integer maHoaDon);

    @Modifying
    @Query("update Voucher v set v.trangThai = 'het_han' where v.trangThai = 'chua_dung' and v.hanSuDung <= :now")
    int danhDauHetHan(@Param("now") LocalDateTime now);
}