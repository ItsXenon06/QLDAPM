package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.PosDtos.*;
import com.xenon.qldapm.dto.TraHangDtos.DongCoTheTraResponse;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.*;
import com.xenon.qldapm.security.CurrentUser;
import com.xenon.qldapm.security.TaiKhoanUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Trả hàng tại quầy: cho_duyet -> da_duyet -> hoan_tat | tu_choi (từ cho_duyet hoặc da_duyet).
 *
 * Quy tắc:
 *  - Chỉ trả hóa đơn đã thanh toán, trong vòng app.pos.return-days ngày kể từ lúc thanh toán (mặc định 7).
 *  - Dòng quà tặng không trả riêng. Mỗi dòng không trả quá (SL mua - SL đã/đang trả); dòng Serial luôn SL 1.
 *  - Tiền hoàn = giá trị dòng SAU giảm giá/khuyến mãi theo tỷ lệ số lượng; trừ phí theo tình trạng hàng:
 *    nguyen_hop 0%, loi 0% (lỗi nhà sản xuất), da_mo 10%, thieu_phu_kien 10%.
 *  - Nhập lại kho: nguyen_hop/da_mo -> trong_kho (+tồn); loi/thieu_phu_kien -> Serial 'hong', không cộng tồn.
 *  - Người duyệt phải khác người lập phiếu (tắt bằng app.pos.return-allow-self-approve=true).
 *  - Kho/Serial/bảo hành chỉ đổi khi hoàn tất. Dòng trả hết -> HieuLuc = 0 (nhả Serial), bảo hành 'da_huy'.
 *    Trả hết toàn bộ hóa đơn -> voucher phát hành từ hóa đơn (chưa dùng) bị hủy.
 *  - Hoàn tiền qua tiền mặt / thẻ / QR (không hoàn trả góp). Báo cáo doanh thu cần trừ PhieuTraHang hoan_tat.
 */
@Service
@RequiredArgsConstructor
public class TraHangService {

    private static final Set<String> PHUONG_THUC_HOAN = Set.of("tien_mat", "the_ngan_hang", "qr_chuyen_khoan");
    private static final Map<String, BigDecimal> PHI_PHAN_TRAM = Map.of(
            "nguyen_hop", BigDecimal.ZERO, "loi", BigDecimal.ZERO,
            "da_mo", BigDecimal.TEN, "thieu_phu_kien", BigDecimal.TEN);
    private static final BigDecimal TRAM = BigDecimal.valueOf(100);

    private final InventoryCountGuard inventoryCountGuard;

    private final PhieuTraHangRepository phieuRepository;
    private final ChiTietTraHangRepository chiTietTraRepository;
    private final HoaDonRepository hoaDonRepository;
    private final SerialSanPhamRepository serialRepository;
    private final BaoHanhRepository baoHanhRepository;
    private final VoucherRepository voucherRepository;
    private final NhanVienRepository nhanVienRepository;
    private final TonKhoService tonKhoService;
    private final NhatKyService nhatKyService;

    @Value("${app.pos.return-days:7}")
    private int returnDays;

    @Value("${app.pos.return-allow-self-approve:false}")
    private boolean allowSelfApprove;

    // ===================== lập phiếu =====================

    @Transactional
    public PhieuTraResponse tao(TaoPhieuTraRequest r) {
        TaiKhoanUserDetails u = CurrentUser.require();
        if (!PHUONG_THUC_HOAN.contains(r.phuongThucHoan())) {
            throw BusinessException.badRequest("PHUONG_THUC_HOAN_KHONG_HOP_LE",
                    "Phương thức hoàn tiền không hợp lệ: " + r.phuongThucHoan());
        }
        HoaDon h = hoaDonRepository.findByIdForUpdate(r.maHoaDon())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!"da_thanh_toan".equals(h.getTrangThai())) {
            throw BusinessException.conflict("HOA_DON_KHONG_TRA_DUOC", "Chỉ trả hàng cho hóa đơn đã thanh toán");
        }
        if (LocalDateTime.now().isAfter(h.getThoiGianThanhToan().plusDays(returnDays))) {
            throw BusinessException.conflict("QUA_HAN_TRA_HANG",
                    "Hóa đơn đã quá " + returnDays + " ngày kể từ lúc thanh toán, không thể trả hàng");
        }
        Map<Integer, ChiTietHoaDon> dongs = new HashMap<>();
        h.getChiTiets().forEach(c -> dongs.put(c.getMaChiTiet(), c));

        PhieuTraHang p = new PhieuTraHang();
        p.setMaSoPhieu("TMP-" + UUID.randomUUID().toString().substring(0, 12));
        p.setHoaDon(h);
        p.setNguoiXuLy(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        p.setLyDo(r.lyDo().trim());
        p.setPhuongThucHoan(r.phuongThucHoan());

        Set<Integer> daCo = new HashSet<>();
        BigDecimal tong = BigDecimal.ZERO;
        for (DongTraRequest d : r.chiTiets()) {
            ChiTietHoaDon c = dongs.get(d.maChiTietHD());
            if (c == null || !Boolean.TRUE.equals(c.getHieuLuc())) {
                throw BusinessException.badRequest("DONG_KHONG_TRA_DUOC",
                        "Dòng #" + d.maChiTietHD() + " không thuộc hóa đơn hoặc đã được trả hết");
            }
            if (!daCo.add(d.maChiTietHD())) {
                throw BusinessException.badRequest("DONG_TRUNG", "Dòng #" + d.maChiTietHD() + " xuất hiện nhiều lần");
            }
            if (Boolean.TRUE.equals(c.getLaQuaTang())) {
                throw BusinessException.badRequest("KHONG_TRA_QUA_TANG", "Quà tặng không thể trả riêng: " + c.getMaSKU());
            }
            if (!PHI_PHAN_TRAM.containsKey(d.tinhTrangHang())) {
                throw BusinessException.badRequest("TINH_TRANG_KHONG_HOP_LE", "Tình trạng hàng không hợp lệ: " + d.tinhTrangHang());
            }
            if (c.getSerial() != null) {
                if (d.soLuong() != 1) {
                    throw BusinessException.badRequest("SERIAL_SO_LUONG", "Dòng Serial chỉ trả 1 thiết bị");
                }
                if (!"da_ban".equals(c.getSerial().getTrangThai())) {
                    throw BusinessException.conflict("SERIAL_SAI_TRANG_THAI", "Thiết bị " + c.getSerial().getSoSerial()
                            + " đang ở trạng thái '" + c.getSerial().getTrangThai() + "', không thể trả");
                }
            }
            long conTra = c.getSoLuong() - chiTietTraRepository.daTraHoacDangTra(c.getMaChiTiet());
            if (d.soLuong() > conTra) {
                throw BusinessException.conflict("TRA_QUA_SO_LUONG",
                        "Dòng " + c.getMaSKU() + " chỉ còn trả được " + conTra + " (mua " + c.getSoLuong() + ")");
            }
            BigDecimal hoan = tinhHoan(c, d.soLuong(), d.tinhTrangHang());

            ChiTietTraHang ct = new ChiTietTraHang();
            ct.setChiTietHoaDon(c);
            ct.setSerial(c.getSerial());
            ct.setSoLuong(d.soLuong());
            ct.setTinhTrangHang(d.tinhTrangHang());
            ct.setSoTienHoan(hoan);
            ct.setNhapLaiKho(nhapLaiKho(d.tinhTrangHang()));
            ct.setGhiChu(blankToNull(d.ghiChu()));
            p.addChiTietTraHang(ct);
            tong = tong.add(hoan);
        }
        p.setTongTienHoan(tong);
        p = phieuRepository.saveAndFlush(p);
        p.setMaSoPhieu(String.format("PT-%s-%06d", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), p.getMaPhieuTra()));
        return toResponse(p);
    }

    // ===================== duyệt / từ chối / hoàn tất =====================

    @Transactional
    public PhieuTraResponse duyet(Integer id, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        PhieuTraHang p = layPhieu(id, "cho_duyet");
        if (!allowSelfApprove && p.getNguoiXuLy().getMaNhanVien().equals(u.getMaNhanVien())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "KHONG_TU_DUYET", "Người duyệt phải khác người lập phiếu");
        }
        p.setTrangThai("da_duyet");
        p.setNguoiDuyet(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        p.setDuyetLuc(LocalDateTime.now());
        nhatKyService.ghi(u.getMaNhanVien(), "duyet_tra_hang", "PhieuTraHang", String.valueOf(id),
                "cho_duyet", "da_duyet; hoan " + p.getTongTienHoan().toPlainString(), ip);
        return toResponse(p);
    }

    @Transactional
    public PhieuTraResponse tuChoi(Integer id, String lyDo, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        PhieuTraHang p = layPhieu(id, "cho_duyet", "da_duyet");
        String cu = p.getTrangThai();
        p.setTrangThai("tu_choi");
        p.setNguoiDuyet(nhanVienRepository.getReferenceById(u.getMaNhanVien()));   // ràng buộc CSDL: có người xử lý + thời điểm
        p.setDuyetLuc(LocalDateTime.now());
        String ghep = p.getLyDo() + " | Từ chối: " + lyDo.trim();
        p.setLyDo(ghep.length() > 500 ? ghep.substring(0, 500) : ghep);
        nhatKyService.ghi(u.getMaNhanVien(), "tu_choi_tra_hang", "PhieuTraHang", String.valueOf(id),
                cu, "tu_choi: " + lyDo.trim(), ip);
        return toResponse(p);
    }

    /** Khách đã trả hàng và nhận tiền: cập nhật Serial, tồn kho, bảo hành. */
    @Transactional
    public PhieuTraResponse hoanTat(Integer id, String ip) {
        inventoryCountGuard.stockMovement();
        TaiKhoanUserDetails u = CurrentUser.require();
        PhieuTraHang p = layPhieu(id, "da_duyet");
        HoaDon h = p.getHoaDon();
        String ghiChu = "Trả hàng " + p.getMaSoPhieu();
        List<ChiTietTraHang> cts = new ArrayList<>(p.getChiTiets());

        // 1. Serial (khóa theo thứ tự id để tránh deadlock)
        cts.stream().filter(ct -> ct.getSerial() != null)
                .sorted(Comparator.comparing((ChiTietTraHang ct) -> ct.getSerial().getMaSerial()))
                .forEach(ct -> {
                    SerialSanPham s = serialRepository.findByIdForUpdate(ct.getSerial().getMaSerial())
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy thiết bị"));
                    if (!"da_ban".equals(s.getTrangThai())) {
                        throw BusinessException.conflict("SERIAL_SAI_TRANG_THAI", "Thiết bị " + s.getSoSerial()
                                + " đang ở trạng thái '" + s.getTrangThai() + "', không thể nhận trả");
                    }
                    String moi = Boolean.TRUE.equals(ct.getNhapLaiKho()) ? "trong_kho" : "hong";
                    s.setTrangThai(moi);
                    tonKhoService.ghiLichSuSerial(s, "da_ban", moi, "phieu_tra", p.getMaPhieuTra(),
                            u.getMaNhanVien(), ghiChu + " (" + ct.getTinhTrangHang() + ")");
                });

        // 2. Tồn kho: chỉ cộng hàng nhập lại kho (khóa theo thứ tự mã sản phẩm)
        Map<Integer, Integer> cong = new TreeMap<>();
        for (ChiTietTraHang ct : cts) {
            if (Boolean.TRUE.equals(ct.getNhapLaiKho())) {
                cong.merge(ct.getChiTietHoaDon().getSanPham().getMaSanPham(), ct.getSoLuong(), Integer::sum);
            }
        }
        for (Map.Entry<Integer, Integer> e : cong.entrySet()) {
            tonKhoService.dieuChinh(e.getKey(), e.getValue(), "nhap_tra_hang", "phieu_tra",
                    p.getMaPhieuTra(), u.getMaNhanVien(), ghiChu);
        }

        // 3. Dòng hóa đơn trả hết -> vô hiệu hóa (nhả Serial) + hủy bảo hành (đếm TRƯỚC khi đổi trạng thái phiếu)
        for (ChiTietTraHang ct : cts) {
            ChiTietHoaDon c = ct.getChiTietHoaDon();
            long daTraXong = chiTietTraRepository.daTraXong(c.getMaChiTiet());
            if (daTraXong + ct.getSoLuong() >= c.getSoLuong()) {
                c.setHieuLuc(false);
                baoHanhRepository.findByChiTietHoaDon_MaChiTiet(c.getMaChiTiet()).forEach(b -> b.setTrangThai("da_huy"));
            }
        }

        // 4. trả hết toàn bộ hóa đơn -> hủy voucher phát hành chưa dùng
        boolean traHet = h.getChiTiets().stream().filter(c -> !Boolean.TRUE.equals(c.getLaQuaTang()))
                .noneMatch(c -> Boolean.TRUE.equals(c.getHieuLuc()));
        if (traHet) {
            for (Voucher v : voucherRepository.findByHoaDonPhatHanh_MaHoaDon(h.getMaHoaDon())) {
                if ("chua_dung".equals(v.getTrangThai())) v.setTrangThai("huy");
            }
        }

        p.setTrangThai("hoan_tat");
        p.setHoanTatLuc(LocalDateTime.now());
        nhatKyService.ghi(u.getMaNhanVien(), "hoan_tat_tra_hang", "PhieuTraHang", String.valueOf(id),
                "da_duyet", "hoan_tat; hoan " + p.getTongTienHoan().toPlainString() + " qua " + p.getPhuongThucHoan(), ip);
        return toResponse(p);
    }

    // ===================== truy vấn =====================

    @Transactional(readOnly = true)
    public PhieuTraResponse chiTiet(Integer id) {
        return toResponse(phieuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu trả hàng")));
    }

    @Transactional(readOnly = true)
    public PageResponse<PhieuTraResponse> danhSach(String trangThai, Pageable pageable) {
        Page<PhieuTraHang> page = trangThai != null && !trangThai.isBlank()
                ? phieuRepository.findByTrangThai(trangThai, pageable) : phieuRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<PhieuTraResponse> theoHoaDon(Integer maHoaDon) {
        return phieuRepository.findByHoaDon_MaHoaDon(maHoaDon).stream().map(this::toResponse).toList();
    }

    /** Các dòng của hóa đơn còn trả được và số lượng tối đa (cho màn hình trả hàng). */
    @Transactional(readOnly = true)
    public List<DongCoTheTraResponse> coTheTra(Integer maHoaDon) {
        HoaDon h = hoaDonRepository.findById(maHoaDon).orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!"da_thanh_toan".equals(h.getTrangThai())) {
            throw BusinessException.conflict("HOA_DON_KHONG_TRA_DUOC", "Chỉ trả hàng cho hóa đơn đã thanh toán");
        }
        List<DongCoTheTraResponse> kq = new ArrayList<>();
        for (ChiTietHoaDon c : h.getChiTiets()) {
            if (!Boolean.TRUE.equals(c.getHieuLuc()) || Boolean.TRUE.equals(c.getLaQuaTang())) continue;
            long da = chiTietTraRepository.daTraHoacDangTra(c.getMaChiTiet());
            kq.add(new DongCoTheTraResponse(c.getMaChiTiet(), c.getMaSKU(), c.getTenHang(),
                    c.getSerial() == null ? null : c.getSerial().getSoSerial(), c.getSoLuong(), da,
                    Math.max(c.getSoLuong() - da, 0), thanhTien(c)));
        }
        return kq;
    }

    // ===================== helpers =====================

    private PhieuTraHang layPhieu(Integer id, String... trangThais) {
        PhieuTraHang p = phieuRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu trả hàng"));
        if (!Arrays.asList(trangThais).contains(p.getTrangThai())) {
            throw BusinessException.conflict("PHIEU_SAI_TRANG_THAI", "Phiếu đang ở trạng thái '" + p.getTrangThai() + "'");
        }
        return p;
    }

    private static BigDecimal thanhTien(ChiTietHoaDon c) {
        return c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong())).subtract(c.getGiamGia());
    }

    private BigDecimal tinhHoan(ChiTietHoaDon c, int sl, String tinhTrang) {
        BigDecimal net = thanhTien(c);
        BigDecimal phanChia = net.multiply(BigDecimal.valueOf(sl))
                .divide(BigDecimal.valueOf(c.getSoLuong()), 2, RoundingMode.HALF_UP);
        BigDecimal conLai = net.subtract(chiTietTraRepository.tongHoan(c.getMaChiTiet())).max(BigDecimal.ZERO);
        BigDecimal truocPhi = phanChia.min(conLai);
        return truocPhi.multiply(TRAM.subtract(PHI_PHAN_TRAM.get(tinhTrang)))
                .divide(TRAM, 2, RoundingMode.HALF_UP);
    }

    private static boolean nhapLaiKho(String tinhTrang) {
        return "nguyen_hop".equals(tinhTrang) || "da_mo".equals(tinhTrang);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private PhieuTraResponse toResponse(PhieuTraHang p) {
        List<DongTraResponse> dongs = p.getChiTiets().stream().map(ct -> new DongTraResponse(
                ct.getMaChiTiet(), ct.getChiTietHoaDon().getMaChiTiet(), ct.getChiTietHoaDon().getTenHang(),
                ct.getChiTietHoaDon().getMaSKU(), ct.getSerial() == null ? null : ct.getSerial().getSoSerial(),
                ct.getSoLuong(), ct.getTinhTrangHang(), ct.getSoTienHoan(), ct.getNhapLaiKho(), ct.getGhiChu())).toList();
        return new PhieuTraResponse(p.getMaPhieuTra(), p.getMaSoPhieu(), p.getHoaDon().getMaHoaDon(),
                p.getHoaDon().getSoHoaDon(), p.getNguoiXuLy().getHoTen(),
                p.getNguoiDuyet() == null ? null : p.getNguoiDuyet().getHoTen(), p.getLyDo(), p.getPhuongThucHoan(),
                p.getTongTienHoan(), p.getTrangThai(), p.getTaoLuc(), p.getDuyetLuc(), p.getHoanTatLuc(), dongs);
    }
}