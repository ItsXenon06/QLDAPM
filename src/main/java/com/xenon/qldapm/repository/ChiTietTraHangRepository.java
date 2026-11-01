package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChiTietTraHangRepository extends JpaRepository<ChiTietTraHang, Integer> {

    List<ChiTietTraHang> findByPhieuTra_MaPhieuTra(Integer maPhieuTra);

    List<ChiTietTraHang> findByChiTietHoaDon_MaChiTiet(Integer maChiTietHD);

    /** SL đã trả hoặc đang chờ trả (mọi phiếu chưa bị từ chối). */
    @Query("select coalesce(sum(t.soLuong), 0L) from ChiTietTraHang t "
            + "where t.chiTietHoaDon.maChiTiet = :id and t.phieuTra.trangThai <> 'tu_choi'")
    long daTraHoacDangTra(@Param("id") Integer id);

    /** SL đã trả xong (phiếu hoàn tất). */
    @Query("select coalesce(sum(t.soLuong), 0L) from ChiTietTraHang t "
            + "where t.chiTietHoaDon.maChiTiet = :id and t.phieuTra.trangThai = 'hoan_tat'")
    long daTraXong(@Param("id") Integer id);

    /** Tiền đã hoàn/đang chờ hoàn của 1 dòng (phiếu chưa bị từ chối). */
    @Query("select coalesce(sum(t.soTienHoan), 0) from ChiTietTraHang t "
            + "where t.chiTietHoaDon.maChiTiet = :id and t.phieuTra.trangThai <> 'tu_choi'")
    BigDecimal tongHoan(@Param("id") Integer id);
}