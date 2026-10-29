package com.xenon.qldapm.repository;

import com.xenon.qldapm.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DanhMucRepository extends JpaRepository<DanhMuc, Integer> {

    List<DanhMuc> findByDanhMucChaIsNullAndNgayXoaIsNullOrderByThuTuHienThi();

    List<DanhMuc> findByDanhMucCha_MaDanhMucAndNgayXoaIsNullOrderByThuTuHienThi(Integer maDanhMucCha);
}
