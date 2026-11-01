package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByMaCode(String maCode);

    List<Voucher> findByKhachHang_MaKhachHangAndTrangThai(Integer maKhachHang, String trangThai);

    List<Voucher> findByHoaDonPhatHanh_MaHoaDon(Integer maHoaDon);

    /** Lọc tùy chọn theo trạng thái / khách hàng / chương trình (tham số null = bỏ qua). */
    @Query("select v from Voucher v left join v.khachHang kh where (:tt is null or v.trangThai = :tt) "
            + "and (:kh is null or kh.maKhachHang = :kh) and (:km is null or v.khuyenMai.maKhuyenMai = :km)")
    Page<Voucher> loc(@Param("tt") String trangThai, @Param("kh") Integer maKhachHang,
                      @Param("km") Integer maKhuyenMai, Pageable pageable);

    /** Khóa voucher khi hủy thủ công (KhuyenMaiEngine.chot cũng khóa khi dùng). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.maVoucher = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") Integer id);

    @Modifying
    @Query("update Voucher v set v.trangThai = 'het_han' where v.trangThai = 'chua_dung' and v.hanSuDung <= :now")
    int danhDauHetHan(@Param("now") LocalDateTime now);
}