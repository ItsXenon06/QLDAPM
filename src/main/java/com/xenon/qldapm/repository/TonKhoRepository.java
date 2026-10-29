package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TonKhoRepository extends JpaRepository<TonKho, Integer> {

    Optional<TonKho> findBySanPham_MaSanPham(Integer maSanPham);

    /** Khóa dòng tồn khi trừ/cộng kho. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TonKho t where t.sanPham.maSanPham = :maSanPham")
    Optional<TonKho> findBySanPhamForUpdate(@Param("maSanPham") Integer maSanPham);

    /** Hàng chạm/dưới ngưỡng tồn tối thiểu. */
    @Query("select t from TonKho t join fetch t.sanPham sp "
            + "where sp.ngayXoa is null and t.slTon <= t.tonToiThieu order by t.slTon")
    List<TonKho> findCanhBao();
}
