package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.*;
import com.xenon.qldapm.security.CurrentUser;
import com.xenon.qldapm.security.TaiKhoanUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phiếu nhập: nhap -> mot_phan -> hoan_thanh | huy.
 * Quy tắc: mỗi dòng nhận không vượt SL đặt; hàng theo dõi Serial phải nhập đúng 1 serial/đơn vị
 * (Serial/IMEI duy nhất toàn hệ thống - R01); mỗi lần nhận cộng tồn có khóa dòng + ghi sổ cái;
 * chỉ hủy được phiếu chưa nhận gì; phiếu nhận thiếu có thể "kết thúc" để chốt số đã nhận.
 */
@Service
@RequiredArgsConstructor
public class NhapKhoService {

    private final PhieuNhapKhoRepository phieuRepository;
    private final NhaCungCapRepository nhaCungCapRepository;
    private final SanPhamRepository sanPhamRepository;
    private final SerialSanPhamRepository serialRepository;
    private final NhanVienRepository nhanVienRepository;
    private final TonKhoService tonKhoService;

    @Transactional
    public PhieuNhapResponse tao(PhieuNhapRequest r) {
        TaiKhoanUserDetails u = CurrentUser.require();
        NhaCungCap ncc = nhaCungCapRepository.findById(r.maNCC()).filter(n -> n.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));

        PhieuNhapKho p = new PhieuNhapKho();
        p.setMaSoPhieu("TMP-" + UUID.randomUUID().toString().substring(0, 12));   // đổi thành mã thật sau khi có ID
        p.setNcc(ncc);
        p.setSoChungTuNCC(blankToNull(r.soChungTuNCC()));
        p.setNguoiLap(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        p.setNgayDuKien(r.ngayDuKien());
        p.setGhiChu(blankToNull(r.ghiChu()));

        Set<Integer> daCo = new HashSet<>();
        for (DongNhapRequest d : r.chiTiets()) {
            if (!daCo.add(d.maSanPham())) {
                throw BusinessException.badRequest("SAN_PHAM_TRUNG_DONG", "Mỗi sản phẩm chỉ xuất hiện 1 dòng trong phiếu");
            }
            SanPham sp = sanPhamRepository.findById(d.maSanPham()).filter(s -> s.getNgayXoa() == null)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm #" + d.maSanPham()));
            ChiTietNhapKho ct = new ChiTietNhapKho();
            ct.setSanPham(sp);
            ct.setSlDatHang(d.slDatHang());
            ct.setGiaNhap(d.giaNhap());
            if (d.chietKhauPhanTram() != null) ct.setChietKhauPhanTram(d.chietKhauPhanTram());
            ct.setThueSuat(d.thueSuat() != null ? d.thueSuat() : sp.getThueSuatVAT());
            p.addChiTietNhapKho(ct);
        }
        tinhTong(p, false);
        p = phieuRepository.saveAndFlush(p);
        p.setMaSoPhieu(String.format("PN-%s-%06d", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), p.getMaPhieuNhap()));
        return toResponse(p, true);
    }

    @Transactional
    public PhieuNhapResponse nhanHang(Integer id, NhanHangRequest r) {
        TaiKhoanUserDetails u = CurrentUser.require();
        PhieuNhapKho p = phieuRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu nhập"));
        if (!List.of("nhap", "mot_phan").contains(p.getTrangThai())) {
            throw BusinessException.conflict("PHIEU_KHONG_NHAN_DUOC", "Phiếu ở trạng thái '" + p.getTrangThai() + "' không thể nhận hàng");
        }
        Map<Integer, ChiTietNhapKho> theoId = p.getChiTiets().stream()
                .collect(Collectors.toMap(ChiTietNhapKho::getMaChiTiet, c -> c));
        Set<Integer> dongDaXuLy = new HashSet<>();
        Set<String> maTrongYeuCau = new HashSet<>();

        for (DongNhanRequest d : r.dongs()) {
            ChiTietNhapKho ct = theoId.get(d.maChiTiet());
            if (ct == null) throw BusinessException.badRequest("DONG_KHONG_THUOC_PHIEU", "Dòng #" + d.maChiTiet() + " không thuộc phiếu này");
            if (!dongDaXuLy.add(d.maChiTiet())) throw BusinessException.badRequest("DONG_TRUNG", "Dòng #" + d.maChiTiet() + " xuất hiện nhiều lần");
            int sl = d.soLuong();
            if (ct.getSlDaNhan() + sl > ct.getSlDatHang()) {
                throw BusinessException.badRequest("NHAN_QUA_SO_LUONG",
                        "Nhận vượt số lượng đặt (đặt " + ct.getSlDatHang() + ", đã nhận " + ct.getSlDaNhan() + ")");
            }
            SanPham sp = ct.getSanPham();
            List<SerialInput> serials = d.serials() == null ? List.of() : d.serials();
            if (Boolean.TRUE.equals(sp.getCoTheoDoiSerial())) {
                if (serials.size() != sl) {
                    throw BusinessException.badRequest("THIEU_SERIAL",
                            "Sản phẩm " + sp.getMaSKU() + " theo dõi Serial: cần đúng " + sl + " serial, nhận được " + serials.size());
                }
                BigDecimal giaVon = giaVonDonVi(ct);
                for (SerialInput si : serials) {
                    taoSerial(sp, ct, si, giaVon, maTrongYeuCau, u, p);
                }
            } else if (!serials.isEmpty()) {
                throw BusinessException.badRequest("KHONG_THEO_DOI_SERIAL", "Sản phẩm " + sp.getMaSKU() + " không theo dõi Serial");
            }
            ct.setSlDaNhan(ct.getSlDaNhan() + sl);
            tonKhoService.dieuChinh(sp.getMaSanPham(), sl, "nhap_hang", "phieu_nhap", p.getMaPhieuNhap(),
                    u.getMaNhanVien(), "Nhận hàng " + p.getMaSoPhieu());
        }

        boolean xong = p.getChiTiets().stream().allMatch(c -> c.getSlDaNhan().equals(c.getSlDatHang()));
        p.setNguoiNhanHang(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        if (xong) {
            p.setTrangThai("hoan_thanh");
            p.setNgayNhanThuc(LocalDateTime.now());
        } else {
            p.setTrangThai("mot_phan");
        }
        tinhTong(p, true);
        return toResponse(p, true);
    }

    /** Chốt phiếu nhận thiếu: không nhận thêm nữa. */
    @Transactional
    public PhieuNhapResponse ketThuc(Integer id) {
        PhieuNhapKho p = phieuRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu nhập"));
        if (!"mot_phan".equals(p.getTrangThai())) {
            throw BusinessException.conflict("PHIEU_KHONG_KET_THUC_DUOC", "Chỉ kết thúc được phiếu đang nhận một phần");
        }
        p.setTrangThai("hoan_thanh");
        p.setNgayNhanThuc(LocalDateTime.now());
        return toResponse(p, true);
    }

    @Transactional
    public PhieuNhapResponse huy(Integer id) {
        PhieuNhapKho p = phieuRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu nhập"));
        if (!"nhap".equals(p.getTrangThai())) {
            throw BusinessException.conflict("PHIEU_KHONG_HUY_DUOC", "Chỉ hủy được phiếu chưa nhận hàng");
        }
        p.setTrangThai("huy");
        return toResponse(p, true);
    }

    @Transactional(readOnly = true)
    public PhieuNhapResponse chiTiet(Integer id) {
        return toResponse(phieuRepository.findWithChiTiets(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu nhập")), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<PhieuNhapResponse> danhSach(String trangThai, Integer maNCC, Pageable pageable) {
        Page<PhieuNhapKho> page = trangThai != null && !trangThai.isBlank() ? phieuRepository.findByTrangThai(trangThai, pageable)
                : maNCC != null ? phieuRepository.findByNcc_MaNCC(maNCC, pageable)
                : phieuRepository.findAll(pageable);
        return PageResponse.of(page.map(p -> toResponse(p, false)));
    }

    // ---------------- helpers ----------------

    private void taoSerial(SanPham sp, ChiTietNhapKho ct, SerialInput si, BigDecimal giaVon,
                           Set<String> maTrongYeuCau, TaiKhoanUserDetails u, PhieuNhapKho p) {
        String sn = si.soSerial().trim().toUpperCase();
        String imei = blankToNull(si.imei());
        String imei2 = blankToNull(si.imei2());
        for (String im : new String[]{imei, imei2}) {
            if (im != null && !im.matches("\\d{15}")) {
                throw BusinessException.badRequest("IMEI_KHONG_HOP_LE", "IMEI phải gồm đúng 15 chữ số: " + im);
            }
        }
        if (imei2 != null && (imei == null || imei.equals(imei2))) {
            throw BusinessException.badRequest("IMEI2_KHONG_HOP_LE", "IMEI2 chỉ hợp lệ khi có IMEI và khác IMEI");
        }
        // các mã của CÙNG thiết bị được phép trùng nhau (có hãng dùng IMEI làm serial); khác thiết bị thì không
        Set<String> maCuaThietBi = new LinkedHashSet<>();
        maCuaThietBi.add(sn);
        if (imei != null) maCuaThietBi.add(imei);
        if (imei2 != null) maCuaThietBi.add(imei2);
        for (String ma : maCuaThietBi) {
            if (!maTrongYeuCau.add(ma)) {
                throw BusinessException.conflict("SERIAL_TRUNG_TRONG_PHIEU", "Mã bị lặp trong cùng yêu cầu: " + ma);
            }
            if (serialRepository.findByAnyCode(ma).isPresent()) {
                throw BusinessException.conflict("SERIAL_DA_TON_TAI", "Serial/IMEI đã tồn tại trong hệ thống: " + ma);
            }
        }
        SerialSanPham s = new SerialSanPham();
        s.setSanPham(sp);
        s.setSoSerial(sn);
        s.setImei(imei);
        s.setImei2(imei2);
        s.setChiTietNhap(ct);
        s.setGiaVon(giaVon);
        s.setTrangThai("trong_kho");
        s = serialRepository.save(s);
        tonKhoService.ghiLichSuSerial(s, null, "trong_kho", "phieu_nhap", p.getMaPhieuNhap(),
                u.getMaNhanVien(), "Nhập kho " + p.getMaSoPhieu());
    }

    /** Giá vốn 1 đơn vị = giá nhập sau chiết khấu, chưa VAT. */
    private static BigDecimal giaVonDonVi(ChiTietNhapKho ct) {
        return ct.getGiaNhap().multiply(BigDecimal.valueOf(100).subtract(ct.getChietKhauPhanTram()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static void tinhTong(PhieuNhapKho p, boolean theoThucNhan) {
        BigDecimal hang = BigDecimal.ZERO;
        BigDecimal thue = BigDecimal.ZERO;
        for (ChiTietNhapKho ct : p.getChiTiets()) {
            int sl = theoThucNhan ? ct.getSlDaNhan() : ct.getSlDatHang();
            BigDecimal net = giaVonDonVi(ct).multiply(BigDecimal.valueOf(sl));
            hang = hang.add(net);
            thue = thue.add(net.multiply(ct.getThueSuat()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        p.setTongTienHang(hang);
        p.setTongTienThue(thue);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private PhieuNhapResponse toResponse(PhieuNhapKho p, boolean kemChiTiet) {
        List<DongNhapResponse> dongs = !kemChiTiet ? null : p.getChiTiets().stream().map(c -> new DongNhapResponse(
                c.getMaChiTiet(), c.getSanPham().getMaSanPham(), c.getSanPham().getMaSKU(), c.getSanPham().getTenSanPham(),
                c.getSanPham().getCoTheoDoiSerial(), c.getSlDatHang(), c.getSlDaNhan(), c.getGiaNhap(),
                c.getChietKhauPhanTram(), c.getThueSuat())).toList();
        return new PhieuNhapResponse(p.getMaPhieuNhap(), p.getMaSoPhieu(), p.getNcc().getMaNCC(), p.getNcc().getTenNCC(),
                p.getSoChungTuNCC(), p.getTrangThai(), p.getNguoiLap().getHoTen(), p.getNgayDuKien(), p.getNgayNhanThuc(),
                p.getTongTienHang(), p.getTongTienThue(), p.getTongTienHang().add(p.getTongTienThue()),
                p.getGhiChu(), p.getTaoLuc(), dongs);
    }
}
