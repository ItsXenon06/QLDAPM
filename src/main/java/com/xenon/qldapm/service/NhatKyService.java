package com.xenon.qldapm.service;

import com.xenon.qldapm.entity.NhatKyHeThong;
import com.xenon.qldapm.repository.NhanVienRepository;
import com.xenon.qldapm.repository.NhatKyHeThongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi nhật ký thao tác nhạy cảm (đăng nhập, đổi giá, hủy hóa đơn, điều chỉnh kho...). */
@Service
@RequiredArgsConstructor
public class NhatKyService {

    private final NhatKyHeThongRepository nhatKyRepository;
    private final NhanVienRepository nhanVienRepository;

    @Transactional
    public void ghi(Integer maNhanVien, String hanhDong, String bangTacDong, String maBanGhi,
                    String giaTriCu, String giaTriMoi, String diaChiIP) {
        NhatKyHeThong nk = new NhatKyHeThong();
        if (maNhanVien != null) {
            nk.setNhanVien(nhanVienRepository.getReferenceById(maNhanVien));
        }
        nk.setHanhDong(hanhDong);
        nk.setBangTacDong(bangTacDong);
        nk.setMaBanGhi(maBanGhi);
        nk.setGiaTriCu(giaTriCu);
        nk.setGiaTriMoi(giaTriMoi);
        nk.setDiaChiIP(diaChiIP);
        nhatKyRepository.save(nk);
    }
}
