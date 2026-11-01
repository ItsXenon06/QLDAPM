package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhuyenMaiDtos.VoucherResponse;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.entity.Voucher;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.VoucherRepository;
import com.xenon.qldapm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tra cứu & quản lý voucher. Voucher chỉ được phát hành tự động khi thanh toán (KhuyenMaiEngine.chot);
 * ở đây chỉ xem, tra cứu và hủy thủ công voucher chưa dùng.
 * Trạng thái trả về là trạng thái HIỆU LỰC: voucher quá hạn nhưng job chưa kịp đánh dấu vẫn hiện 'het_han'.
 */
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository repository;
    private final NhatKyService nhatKyService;

    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> tim(String trangThai, Integer maKhachHang, Integer maKhuyenMai, Pageable pageable) {
        String tt = trangThai == null || trangThai.isBlank() ? null : trangThai.trim();
        return PageResponse.of(repository.loc(tt, maKhachHang, maKhuyenMai, pageable).map(VoucherService::toResponse));
    }

    @Transactional(readOnly = true)
    public VoucherResponse traCuu(String ma) {
        return toResponse(repository.findByMaCode(ma.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher")));
    }

    /** Voucher còn dùng được của 1 khách (hiển thị khi gắn khách vào hóa đơn). */
    @Transactional(readOnly = true)
    public List<VoucherResponse> khaDungCuaKhach(Integer maKhachHang) {
        LocalDateTime now = LocalDateTime.now();
        return repository.findByKhachHang_MaKhachHangAndTrangThai(maKhachHang, "chua_dung").stream()
                .filter(v -> v.getHanSuDung().isAfter(now)).map(VoucherService::toResponse).toList();
    }

    @Transactional
    public VoucherResponse huy(Integer id, String lyDo, String ip) {
        Voucher v = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy voucher"));
        if (!"chua_dung".equals(v.getTrangThai())) {
            throw BusinessException.conflict("VOUCHER_KHONG_HUY_DUOC", "Chỉ hủy được voucher chưa dùng (hiện: " + v.getTrangThai() + ")");
        }
        v.setTrangThai("huy");
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "huy_voucher", "Voucher", String.valueOf(id),
                "chua_dung " + v.getMaCode(), "huy: " + lyDo.trim(), ip);
        return toResponse(v);
    }

    private static VoucherResponse toResponse(Voucher v) {
        String tt = "chua_dung".equals(v.getTrangThai()) && !v.getHanSuDung().isAfter(LocalDateTime.now())
                ? "het_han" : v.getTrangThai();
        return new VoucherResponse(v.getMaVoucher(), v.getMaCode(), v.getKhuyenMai().getMaKhuyenMai(), v.getGiaTri(),
                v.getKhachHang() == null ? null : v.getKhachHang().getMaKhachHang(), tt, v.getNgayPhatHanh(),
                v.getHanSuDung(), v.getNgaySuDung(),
                v.getKhachHang() == null ? null : v.getKhachHang().getHoTen(),
                v.getKhachHang() == null ? null : v.getKhachHang().getSoDienThoai(),
                v.getKhuyenMai().getTenChuongTrinh(),
                v.getHoaDonPhatHanh() == null ? null : v.getHoaDonPhatHanh().getSoHoaDon());
    }
}