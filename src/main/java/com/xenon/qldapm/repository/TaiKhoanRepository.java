package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaiKhoanRepository extends JpaRepository<TaiKhoan, Integer> {

    Optional<TaiKhoan> findByTenDangNhapAndNgayXoaIsNull(String tenDangNhap);

    Optional<TaiKhoan> findByNhanVien_MaNhanVienAndNgayXoaIsNull(Integer maNhanVien);

    /** Dùng cho đăng nhập: nạp luôn nhân viên, vai trò, quyền (open-in-view đang tắt). */
    @Query("select t from TaiKhoan t join fetch t.nhanVien nv join fetch nv.vaiTro vt "
            + "left join fetch vt.quyens where t.tenDangNhap = :ten and t.ngayXoa is null")
    Optional<TaiKhoan> findForLogin(@Param("ten") String tenDangNhap);
}
