package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.*;
import com.xenon.qldapm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Điểm duy nhất được phép đổi SLTon (khóa dòng + ghi sổ cái LichSuTonKho) và ghi vòng đời Serial.
 * Các phân hệ nhập kho, kiểm kê, POS, trả hàng đều gọi qua đây.
 */
@Service
@RequiredArgsConstructor
public class TonKhoService {

    private final InventoryCountGuard inventoryCountGuard;

    private final TonKhoRepository tonKhoRepository;
    private final LichSuTonKhoRepository lichSuTonKhoRepository;
    private final SerialSanPhamRepository serialRepository;
    private final LichSuSerialRepository lichSuSerialRepository;
    private final ChiTietHoaDonRepository chiTietHoaDonRepository;
    private final NhanVienRepository nhanVienRepository;

    /** Cộng (+) / trừ (-) tồn. Bắt buộc chạy trong transaction của nghiệp vụ gọi nó. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TonKho dieuChinh(Integer maSanPham, int delta, String loaiBienDong, String loaiTaiLieu,
                            Integer maTaiLieu, Integer maNhanVien, String ghiChu) {
        inventoryCountGuard.checkProduct(maSanPham, loaiTaiLieu, maTaiLieu);
        if (delta == 0) {
            throw BusinessException.badRequest("SO_LUONG_KHONG_HOP_LE", "Số lượng biến động phải khác 0");
        }
        TonKho tk = tonKhoRepository.findBySanPhamForUpdate(maSanPham)
                .orElseThrow(() -> new NotFoundException("Sản phẩm chưa có bản ghi tồn kho"));
        int sau = tk.getSlTon() + delta;
        if (sau < 0) {
            throw BusinessException.conflict("TON_KHO_KHONG_DU", "Tồn kho không đủ (hiện có " + tk.getSlTon() + ")");
        }
        tk.setSlTon(sau);

        LichSuTonKho ls = new LichSuTonKho();
        ls.setTonKho(tk);
        ls.setLoaiBienDong(loaiBienDong);
        ls.setSoBienDong(delta);
        ls.setSlSau(sau);
        ls.setLoaiTaiLieu(loaiTaiLieu);
        ls.setMaTaiLieu(maTaiLieu);
        if (maNhanVien != null) ls.setThucHienBoi(nhanVienRepository.getReferenceById(maNhanVien));
        ls.setGhiChu(ghiChu);
        lichSuTonKhoRepository.save(ls);
        return tk;
    }

    /** Ghi 1 dòng vòng đời Serial (người gọi tự đổi SerialSanPham.trangThai). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ghiLichSuSerial(SerialSanPham s, String cu, String moi, String loaiTaiLieu, Integer maTaiLieu,
                                Integer maNhanVien, String ghiChu) {
        inventoryCountGuard.checkProduct(s.getSanPham().getMaSanPham(), loaiTaiLieu, maTaiLieu);
        LichSuSerial ls = new LichSuSerial();
        ls.setSerial(s);
        ls.setTrangThaiCu(cu);
        ls.setTrangThaiMoi(moi);
        ls.setLoaiTaiLieu(loaiTaiLieu);
        ls.setMaTaiLieu(maTaiLieu);
        if (maNhanVien != null) ls.setThucHienBoi(nhanVienRepository.getReferenceById(maNhanVien));
        ls.setGhiChu(ghiChu);
        lichSuSerialRepository.save(ls);
    }

    // ---------------- truy vấn ----------------

    @Transactional(readOnly = true)
    public TonKhoResponse layTon(Integer maSanPham) {
        TonKho tk = tonKhoRepository.findBySanPham_MaSanPham(maSanPham)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tồn kho của sản phẩm"));
        return toResponse(tk);
    }

    @Transactional(readOnly = true)
    public List<TonKhoResponse> canhBao() {
        return tonKhoRepository.findCanhBao().stream().map(TonKhoService::toResponse).toList();
    }

    @Transactional
    public TonKhoResponse capNhatNguong(Integer maSanPham, TonKhoNguongRequest r) {
        if (r.tonToiDa() != null && r.tonToiDa() < r.tonToiThieu()) {
            throw BusinessException.badRequest("NGUONG_KHONG_HOP_LE", "Tồn tối đa phải lớn hơn hoặc bằng tồn tối thiểu");
        }
        TonKho tk = tonKhoRepository.findBySanPham_MaSanPham(maSanPham)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tồn kho của sản phẩm"));
        tk.setTonToiThieu(r.tonToiThieu());
        tk.setTonToiDa(r.tonToiDa());
        tk.setViTriKho(r.viTriKho() == null || r.viTriKho().isBlank() ? null : r.viTriKho().trim());
        return toResponse(tk);
    }

    /** Tra cứu bằng Serial hoặc IMEI/IMEI2: thiết bị + hóa đơn mua + vòng đời. */
    @Transactional(readOnly = true)
    public SerialChiTietResponse traCuuSerial(String ma) {
        SerialSanPham s = serialRepository.findByAnyCode(ma.trim())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thiết bị với mã: " + ma));
        String soHoaDon = null;
        java.time.LocalDateTime ngayMua = null;
        var ct = chiTietHoaDonRepository.findBySerial_MaSerialAndHieuLucTrue(s.getMaSerial());
        if (ct.isPresent() && "da_thanh_toan".equals(ct.get().getHoaDon().getTrangThai())) {
            soHoaDon = ct.get().getHoaDon().getSoHoaDon();
            ngayMua = ct.get().getHoaDon().getThoiGianThanhToan();
        }
        List<LichSuSerialResponse> lichSu = lichSuSerialRepository
                .findBySerial_MaSerialOrderByThoiGianDesc(s.getMaSerial()).stream()
                .map(l -> new LichSuSerialResponse(l.getTrangThaiCu(), l.getTrangThaiMoi(), l.getLoaiTaiLieu(),
                        l.getMaTaiLieu(), l.getThucHienBoi() == null ? null : l.getThucHienBoi().getHoTen(),
                        l.getGhiChu(), l.getThoiGian()))
                .toList();
        return new SerialChiTietResponse(toSerialResponse(s), soHoaDon, ngayMua, lichSu);
    }

    @Transactional(readOnly = true)
    public PageResponse<SerialResponse> timSerial(Integer maSanPham, String trangThai, Pageable pageable) {
        return PageResponse.of(serialRepository
                .search(maSanPham, trangThai == null || trangThai.isBlank() ? null : trangThai, pageable)
                .map(TonKhoService::toSerialResponse));
    }

    // ---------------- mapping ----------------

    /** Giá vốn chỉ hiện cho người có quyền liên quan (thu ngân không thấy giá vốn). */
    static boolean thayGiaVon() {
        return CurrentUser.get().map(u -> u.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("KHO_NHAP") || a.getAuthority().equals("BAO_CAO_XEM")
                        || a.getAuthority().equals("GIA_QUAN_LY"))).orElse(false);
    }

    static TonKhoResponse toResponse(TonKho tk) {
        SanPham sp = tk.getSanPham();
        String mucDo = tk.getSlTon() == 0 ? "HET_HANG" : tk.getSlTon() <= tk.getTonToiThieu() ? "SAP_HET" : "BINH_THUONG";
        return new TonKhoResponse(sp.getMaSanPham(), sp.getMaSKU(), sp.getTenSanPham(), tk.getSlTon(),
                tk.getTonToiThieu(), tk.getTonToiDa(), tk.getViTriKho(), mucDo, tk.getCapNhatLuc());
    }

    static SerialResponse toSerialResponse(SerialSanPham s) {
        SanPham sp = s.getSanPham();
        return new SerialResponse(s.getMaSerial(), sp.getMaSanPham(), sp.getMaSKU(), sp.getTenSanPham(),
                s.getSoSerial(), s.getImei(), s.getImei2(), s.getTrangThai(),
                thayGiaVon() ? s.getGiaVon() : null, s.getNgayNhap());
    }
}
