package com.xenon.qldapm.service;

import com.xenon.qldapm.repository.TaiKhoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Đếm lần đăng nhập sai, khóa tạm tài khoản, ghi nhật ký. */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final TaiKhoanRepository taiKhoanRepository;
    private final NhatKyService nhatKyService;

    @Value("${app.security.max-failed-logins:5}")
    private int maxFailed;

    @Value("${app.security.lock-minutes:15}")
    private int lockMinutes;

    @Transactional
    public void onFailure(String tenDangNhap, String ip) {
        taiKhoanRepository.findByTenDangNhapAndNgayXoaIsNull(tenDangNhap).ifPresent(tk -> {
            Integer maNv = tk.getNhanVien().getMaNhanVien();
            int n = tk.getSoLanDangNhapSai() + 1;
            if (n >= maxFailed) {
                tk.setSoLanDangNhapSai(0);
                tk.setKhoaDen(LocalDateTime.now().plusMinutes(lockMinutes));
                nhatKyService.ghi(maNv, "khoa_tai_khoan", "TaiKhoan", String.valueOf(tk.getMaTaiKhoan()),
                        null, "Khóa " + lockMinutes + " phút do nhập sai " + maxFailed + " lần", ip);
            } else {
                tk.setSoLanDangNhapSai(n);
                nhatKyService.ghi(maNv, "dang_nhap_that_bai", "TaiKhoan", String.valueOf(tk.getMaTaiKhoan()),
                        null, "Lần sai thứ " + n, ip);
            }
        });
    }

    @Transactional
    public void onSuccess(String tenDangNhap, String ip) {
        taiKhoanRepository.findByTenDangNhapAndNgayXoaIsNull(tenDangNhap).ifPresent(tk -> {
            tk.setSoLanDangNhapSai(0);
            tk.setKhoaDen(null);
            tk.setDangNhapLanCuoi(LocalDateTime.now());
            nhatKyService.ghi(tk.getNhanVien().getMaNhanVien(), "dang_nhap", "TaiKhoan",
                    String.valueOf(tk.getMaTaiKhoan()), null, null, ip);
        });
    }
}
