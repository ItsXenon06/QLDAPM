package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.PosDtos.KhachHangRequest;
import com.xenon.qldapm.dto.PosDtos.KhachHangResponse;
import com.xenon.qldapm.entity.KhachHang;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.KhachHangRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Khách hàng. Quy tắc: SĐT là khóa tra cứu, duy nhất trong hàng chưa xóa; chuẩn hóa về dạng 0xxxxxxxxx
 * (bỏ khoảng trắng/dấu, +84 -> 0); có mã số thuế thì phải có tên công ty (xuất hóa đơn VAT);
 * MST 10 số hoặc 10 số-3 số. Xóa mềm (hóa đơn cũ vẫn giữ liên kết).
 */
@Service
@RequiredArgsConstructor
public class KhachHangService {

    private final KhachHangRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<KhachHangResponse> tim(String keyword, Pageable pageable) {
        String kw = keyword == null ? "" : keyword.trim();
        return PageResponse.of(repository.search(kw, pageable).map(KhachHangService::toResponse));
    }

    @Transactional(readOnly = true)
    public KhachHangResponse chiTiet(Integer id) { return toResponse(lay(id)); }

    @Transactional(readOnly = true)
    public KhachHangResponse traCuuSdt(String sdt) {
        return toResponse(repository.findBySoDienThoaiAndNgayXoaIsNull(chuanHoaSdt(sdt))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng với số điện thoại này")));
    }

    @Transactional
    public KhachHangResponse tao(KhachHangRequest r) {
        String sdt = chuanHoaSdt(r.soDienThoai());
        if (repository.findBySoDienThoaiAndNgayXoaIsNull(sdt).isPresent()) {
            throw BusinessException.conflict("SDT_TRUNG", "Số điện thoại đã thuộc về khách hàng khác");
        }
        KhachHang k = new KhachHang();
        apply(k, r, sdt);
        return toResponse(repository.save(k));
    }

    @Transactional
    public KhachHangResponse sua(Integer id, KhachHangRequest r) {
        KhachHang k = lay(id);
        String sdt = chuanHoaSdt(r.soDienThoai());
        var trung = repository.findBySoDienThoaiAndNgayXoaIsNull(sdt);
        if (trung.isPresent() && !trung.get().getMaKhachHang().equals(id)) {
            throw BusinessException.conflict("SDT_TRUNG", "Số điện thoại đã thuộc về khách hàng khác");
        }
        apply(k, r, sdt);
        return toResponse(k);
    }

    @Transactional
    public void xoa(Integer id) {
        lay(id).setNgayXoa(LocalDateTime.now());
    }

    // ---------------- helpers ----------------

    static String chuanHoaSdt(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[\\s.\\-()]", "");
        if (s.startsWith("+84")) s = "0" + s.substring(3);
        else if (s.startsWith("84") && s.length() >= 11) s = "0" + s.substring(2);
        if (!s.matches("0\\d{9,10}")) {
            throw BusinessException.badRequest("SDT_KHONG_HOP_LE", "Số điện thoại không hợp lệ (10-11 chữ số, bắt đầu bằng 0)");
        }
        return s;
    }

    private static void apply(KhachHang k, KhachHangRequest r, String sdt) {
        String mst = blankToNull(r.maSoThue());
        String congTy = blankToNull(r.tenCongTy());
        if (mst != null) {
            if (!mst.matches("\\d{10}(-\\d{3})?")) {
                throw BusinessException.badRequest("MST_KHONG_HOP_LE", "Mã số thuế gồm 10 số hoặc 10 số-3 số");
            }
            if (congTy == null) {
                throw BusinessException.badRequest("THIEU_TEN_CONG_TY", "Có mã số thuế thì phải nhập tên công ty");
            }
        }
        k.setHoTen(r.hoTen().trim());
        k.setSoDienThoai(sdt);
        k.setEmail(blankToNull(r.email()));
        k.setDiaChi(blankToNull(r.diaChi()));
        k.setTenCongTy(congTy);
        k.setMaSoThue(mst);
    }

    private KhachHang lay(Integer id) {
        return repository.findById(id).filter(k -> k.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static KhachHangResponse toResponse(KhachHang k) {
        return new KhachHangResponse(k.getMaKhachHang(), k.getHoTen(), k.getSoDienThoai(), k.getEmail(),
                k.getDiaChi(), k.getTenCongTy(), k.getMaSoThue());
    }
}