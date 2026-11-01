package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaoHanhRepository extends JpaRepository<BaoHanh, Integer> {

    List<BaoHanh> findByChiTietHoaDon_MaChiTiet(Integer maChiTietHD);

    List<BaoHanh> findBySerial_MaSerial(Integer maSerial);

    List<BaoHanh> findByTrangThaiAndNgayKetThucBefore(String trangThai, LocalDateTime moc);

    /** Tra cứu theo số hóa đơn. */
    List<BaoHanh> findByChiTietHoaDon_HoaDon_SoHoaDon(String soHoaDon);

    /** Tra cứu theo khách hàng (mọi hóa đơn của khách), bảo hành mới nhất trước. */
    @Query("select b from BaoHanh b where b.chiTietHoaDon.hoaDon.khachHang.maKhachHang = :kh "
            + "order by b.ngayKetThuc desc")
    List<BaoHanh> timTheoKhachHang(@Param("kh") Integer maKhachHang);

    @Modifying
    @Query("update BaoHanh b set b.trangThai = 'het_han' where b.trangThai = 'con_han' and b.ngayKetThuc <= :now")
    int danhDauHetHan(@Param("now") LocalDateTime now);
}