package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.PosDtos.*;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bán hàng tại quầy. Vòng đời: dang_lap <-> tam_giu -> da_thanh_toan -> da_huy (cùng ngày).
 *
 * Quy tắc chính:
 *  - Server tự định giá từ SanPham (không tin client); giảm giá tay chỉ khi sản phẩm có giá sàn và không thấp hơn sàn.
 *  - Hàng Serial: thêm vào hóa đơn là "giữ chỗ" (unique index HieuLuc=1 + khóa dòng Serial); chỉ chuyển da_ban khi thanh toán.
 *  - Hàng thường: trừ tồn (có khóa) lúc thanh toán; khóa theo thứ tự mã để tránh deadlock.
 *  - Tổng các khoản thanh toán phải đúng bằng số phải trả; tiền mặt tính tiền thối; thẻ/QR/trả góp bắt buộc mã tham chiếu.
 *  - Số hóa đơn HD-{QUAY}-{yyyyMMdd}-{stt} chỉ cấp khi thanh toán (khóa quầy) nên không có "lỗ" số do hủy nháp.
 *  - Thanh toán xong tự tạo BaoHanh (thời hạn theo SanPham.BaoHanhThang, bắt đầu từ lúc thanh toán).
 *  - Mỗi thu ngân chỉ có 1 hóa đơn đang lập; nháp quá hạn bị dọn tự động.
 */
@Service
@RequiredArgsConstructor
public class PosService {

    private static final List<String> NHAP = List.of("dang_lap", "tam_giu");
    private static final Set<String> PHUONG_THUC = Set.of("tien_mat", "the_ngan_hang", "qr_chuyen_khoan", "tra_gop");

    private final HoaDonRepository hoaDonRepository;
    private final ChiTietHoaDonRepository chiTietRepository;
    private final QuayPOSRepository quayRepository;
    private final SanPhamRepository sanPhamRepository;
    private final SerialSanPhamRepository serialRepository;
    private final KhachHangRepository khachHangRepository;
    private final TonKhoRepository tonKhoRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final BaoHanhRepository baoHanhRepository;
    private final PhieuTraHangRepository phieuTraRepository;
    private final NhanVienRepository nhanVienRepository;
    private final TonKhoService tonKhoService;
    private final NhatKyService nhatKyService;

    @Value("${app.pos.draft-expire-minutes:180}")
    private int draftExpireMinutes;

    // ===================== tạo / xem =====================

    @Transactional
    public HoaDonResponse tao(TaoHoaDonRequest r, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        QuayPOS quay = r.maQuay() != null
                ? quayRepository.findById(r.maQuay()).orElseThrow(() -> new NotFoundException("Không tìm thấy quầy"))
                : quayRepository.findByDiaChiIP(ip).orElseThrow(() -> BusinessException.badRequest(
                "KHONG_XAC_DINH_QUAY", "Không xác định được quầy từ địa chỉ IP, vui lòng chọn quầy"));
        if (!"hoat_dong".equals(quay.getTrangThai())) {
            throw BusinessException.conflict("QUAY_KHONG_HOAT_DONG", "Quầy đang ở trạng thái '" + quay.getTrangThai() + "'");
        }
        if (!hoaDonRepository.findByThuNgan_MaNhanVienAndTrangThai(u.getMaNhanVien(), "dang_lap").isEmpty()) {
            throw BusinessException.conflict("DANG_CO_HOA_DON_DANG_LAP",
                    "Bạn đang có hóa đơn đang lập. Hãy thanh toán, tạm giữ hoặc hủy trước khi tạo hóa đơn mới");
        }
        HoaDon h = new HoaDon();
        h.setSoHoaDon("TMP-" + UUID.randomUUID().toString().substring(0, 12));   // số thật cấp khi thanh toán
        h.setQuay(quay);
        h.setThuNgan(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        if (r.maKhachHang() != null) h.setKhachHang(layKhach(r.maKhachHang()));
        return toResponse(hoaDonRepository.saveAndFlush(h), true);
    }

    @Transactional(readOnly = true)
    public HoaDonResponse chiTiet(Integer id) {
        return toResponse(hoaDonRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn")), true);
    }

    @Transactional(readOnly = true)
    public List<HoaDonResponse> nhapCuaToi() {
        Integer me = CurrentUser.require().getMaNhanVien();
        List<HoaDonResponse> kq = new ArrayList<>();
        for (String tt : NHAP) {
            hoaDonRepository.findByThuNgan_MaNhanVienAndTrangThai(me, tt).forEach(h -> kq.add(toResponse(h, true)));
        }
        return kq;
    }

    @Transactional(readOnly = true)
    public PageResponse<HoaDonResponse> danhSach(String soHoaDon, String trangThai, LocalDate tu, LocalDate den, Pageable pageable) {
        if (soHoaDon != null && !soHoaDon.isBlank()) {
            var h = hoaDonRepository.findBySoHoaDon(soHoaDon.trim());
            List<HoaDonResponse> items = h.map(x -> List.of(toResponse(x, false))).orElse(List.of());
            return new PageResponse<>(items, 0, 1, items.size(), items.isEmpty() ? 0 : 1);
        }
        LocalDateTime t = (tu == null ? LocalDate.now() : tu).atStartOfDay();
        LocalDateTime d = (den == null ? LocalDate.now() : den).plusDays(1).atStartOfDay();
        Page<HoaDon> page = hoaDonRepository.findByTrangThaiAndThoiGianThanhToanBetween(
                trangThai == null || trangThai.isBlank() ? "da_thanh_toan" : trangThai, t, d, pageable);
        return PageResponse.of(page.map(h -> toResponse(h, false)));
    }

    // ===================== chỉnh sửa hóa đơn nháp =====================

    @Transactional
    public HoaDonResponse themDong(Integer id, ThemDongRequest r) {
        HoaDon h = layCuaToi(id, "dang_lap");
        String ma = r.ma().trim();
        int sl = r.soLuong() == null ? 1 : r.soLuong();

        Optional<SerialSanPham> serialOpt = serialRepository.findByAnyCode(ma);
        if (serialOpt.isPresent()) {
            if (sl != 1) throw BusinessException.badRequest("SERIAL_SO_LUONG", "Mỗi Serial/IMEI chỉ bán 1 thiết bị");
            SerialSanPham s = serialRepository.findByIdForUpdate(serialOpt.get().getMaSerial())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy thiết bị"));
            if (!"trong_kho".equals(s.getTrangThai())) {
                throw BusinessException.conflict("SERIAL_KHONG_CON_HANG",
                        "Thiết bị " + s.getSoSerial() + " không còn trong kho (trạng thái: " + s.getTrangThai() + ")");
            }
            if (chiTietRepository.existsBySerial_MaSerialAndHieuLucTrue(s.getMaSerial())) {
                throw BusinessException.conflict("SERIAL_DANG_TRONG_HOA_DON",
                        "Thiết bị " + s.getSoSerial() + " đang nằm trong một hóa đơn khác");
            }
            checkBanDuoc(s.getSanPham());
            themDongMoi(h, s.getSanPham(), s, 1);
        } else {
            SanPham sp = sanPhamRepository.findByMaSKUAndNgayXoaIsNull(ma)
                    .or(() -> sanPhamRepository.findByMaVachAndNgayXoaIsNull(ma))
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm/thiết bị với mã: " + ma));
            checkBanDuoc(sp);
            if (Boolean.TRUE.equals(sp.getCoTheoDoiSerial())) {
                throw BusinessException.badRequest("CAN_QUET_SERIAL",
                        "Sản phẩm " + sp.getMaSKU() + " theo dõi Serial/IMEI, vui lòng quét Serial hoặc IMEI của thiết bị");
            }
            ChiTietHoaDon cu = h.getChiTiets().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getHieuLuc()) && c.getSerial() == null
                            && !Boolean.TRUE.equals(c.getLaQuaTang())
                            && c.getSanPham().getMaSanPham().equals(sp.getMaSanPham()))
                    .findFirst().orElse(null);
            if (cu != null) {
                int moi = cu.getSoLuong() + sl;
                kiemTraTon(sp, moi);
                cu.setSoLuong(moi);
            } else {
                kiemTraTon(sp, sl);
                themDongMoi(h, sp, null, sl);
            }
        }
        tinhLai(h);
        hoaDonRepository.flush();
        return toResponse(h, true);
    }

    @Transactional
    public HoaDonResponse capNhatDong(Integer id, Integer maChiTiet, CapNhatDongRequest r) {
        HoaDon h = layCuaToi(id, "dang_lap");
        ChiTietHoaDon c = timDong(h, maChiTiet);
        if (r.soLuong() != null) {
            if (c.getSerial() != null && r.soLuong() != 1) {
                throw BusinessException.badRequest("SERIAL_SO_LUONG", "Dòng có Serial luôn có số lượng 1");
            }
            if (c.getSerial() == null) kiemTraTon(c.getSanPham(), r.soLuong());
            c.setSoLuong(r.soLuong());
        }
        if (r.giamGia() != null) c.setGiamGia(r.giamGia());
        validateGiamGia(c);
        tinhLai(h);
        hoaDonRepository.flush();
        return toResponse(h, true);
    }

    @Transactional
    public HoaDonResponse xoaDong(Integer id, Integer maChiTiet) {
        HoaDon h = layCuaToi(id, "dang_lap");
        ChiTietHoaDon c = timDong(h, maChiTiet);
        h.getChiTiets().remove(c);          // orphanRemoval => xóa dòng, nhả Serial
        tinhLai(h);
        hoaDonRepository.flush();
        return toResponse(h, true);
    }

    @Transactional
    public HoaDonResponse ganKhach(Integer id, Integer maKhachHang) {
        HoaDon h = layCuaToi(id, "dang_lap");
        h.setKhachHang(maKhachHang == null ? null : layKhach(maKhachHang));
        return toResponse(h, true);
    }

    @Transactional
    public HoaDonResponse tamGiu(Integer id) {
        HoaDon h = layCuaToi(id, "dang_lap");
        if (h.getChiTiets().isEmpty()) throw BusinessException.badRequest("HOA_DON_TRONG", "Hóa đơn chưa có hàng để tạm giữ");
        h.setTrangThai("tam_giu");
        return toResponse(h, true);
    }

    @Transactional
    public HoaDonResponse tiepTuc(Integer id) {
        HoaDon h = layCuaToi(id, "tam_giu");
        if (!hoaDonRepository.findByThuNgan_MaNhanVienAndTrangThai(h.getThuNgan().getMaNhanVien(), "dang_lap").isEmpty()) {
            throw BusinessException.conflict("DANG_CO_HOA_DON_DANG_LAP", "Hãy xử lý hóa đơn đang lập trước khi mở lại hóa đơn tạm giữ");
        }
        h.setTrangThai("dang_lap");
        return toResponse(h, true);
    }

    /** Hủy hóa đơn nháp: xóa hẳn (chưa có giá trị nghiệp vụ, không tạo lỗ số hóa đơn). */
    @Transactional
    public void huyNhap(Integer id) {
        HoaDon h = layCuaToi(id, "dang_lap", "tam_giu");
        hoaDonRepository.delete(h);
    }

    // ===================== thanh toán =====================

    @Transactional
    public HoaDonResponse thanhToan(Integer id, ThanhToanRequest r) {
        TaiKhoanUserDetails u = CurrentUser.require();
        HoaDon h = layCuaToi(id, "dang_lap");
        List<ChiTietHoaDon> dongs = dongHieuLuc(h);
        if (dongs.isEmpty()) throw BusinessException.badRequest("HOA_DON_TRONG", "Hóa đơn chưa có hàng");

        QuayPOS quay = quayRepository.findByIdForUpdate(h.getQuay().getMaQuay())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quầy"));
        if (!"hoat_dong".equals(quay.getTrangThai())) {
            throw BusinessException.conflict("QUAY_KHONG_HOAT_DONG", "Quầy không còn hoạt động");
        }

        // 1. định giá lại theo giá hiện hành, kiểm tra lại giá sàn
        for (ChiTietHoaDon c : dongs) {
            SanPham sp = c.getSanPham();
            checkBanDuoc(sp);
            c.setDonGia(sp.getGiaNiemYet());
            c.setThueSuat(sp.getThueSuatVAT());
            validateGiamGia(c);
        }
        tinhLai(h);
        BigDecimal canTra = h.getTongTienHang().subtract(h.getTongGiamGia());
        if (canTra.signum() <= 0) throw BusinessException.badRequest("TONG_TIEN_KHONG_HOP_LE", "Số tiền phải trả phải lớn hơn 0");

        // 2. kiểm tra các khoản thanh toán
        BigDecimal tong = BigDecimal.ZERO;
        for (DongThanhToanRequest p : r.thanhToans()) {
            kiemTraThanhToan(p);
            tong = tong.add(p.soTien());
        }
        if (tong.compareTo(canTra) != 0) {
            throw BusinessException.badRequest("THANH_TOAN_KHONG_KHOP",
                    "Tổng thanh toán " + tong.toPlainString() + " khác số phải trả " + canTra.toPlainString());
        }

        // 3. kho: Serial -> da_ban (khóa theo thứ tự id), tồn -> trừ (khóa theo thứ tự mã sản phẩm)
        dongs.stream().filter(c -> c.getSerial() != null)
                .sorted(Comparator.comparing((ChiTietHoaDon c) -> c.getSerial().getMaSerial()))
                .forEach(c -> {
                    SerialSanPham s = serialRepository.findByIdForUpdate(c.getSerial().getMaSerial())
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy thiết bị"));
                    if (!"trong_kho".equals(s.getTrangThai())) {
                        throw BusinessException.conflict("SERIAL_KHONG_CON_HANG",
                                "Thiết bị " + s.getSoSerial() + " không còn trong kho");
                    }
                    s.setTrangThai("da_ban");
                    tonKhoService.ghiLichSuSerial(s, "trong_kho", "da_ban", "hoa_don", h.getMaHoaDon(),
                            u.getMaNhanVien(), "Bán tại " + quay.getMaQuayCode());
                });
        for (Map.Entry<Integer, Integer> e : tongTheoSanPham(dongs).entrySet()) {
            tonKhoService.dieuChinh(e.getKey(), -e.getValue(), "xuat_ban", "hoa_don", h.getMaHoaDon(),
                    u.getMaNhanVien(), "Bán tại " + quay.getMaQuayCode());
        }

        // 4. thanh toán
        LocalDateTime now = LocalDateTime.now();
        for (DongThanhToanRequest p : r.thanhToans()) {
            ThanhToan t = new ThanhToan();
            t.setHoaDon(h);
            t.setPhuongThuc(p.phuongThuc());
            t.setTrangThai("thanh_cong");
            t.setSoTien(p.soTien());
            t.setTienKhachDua(p.tienKhachDua());
            t.setMaThamChieu(blankToNull(p.maThamChieu()));
            t.setDonViTraGop(blankToNull(p.donViTraGop()));
            t.setSoKyHan(p.soKyHan());
            t.setThanhToanLuc(now);
            thanhToanRepository.save(t);
        }

        // 5. bảo hành
        for (ChiTietHoaDon c : dongs) {
            int thang = c.getSanPham().getBaoHanhThang();
            if (thang > 0) {
                BaoHanh bh = new BaoHanh();
                bh.setChiTietHoaDon(c);
                bh.setSerial(c.getSerial());
                bh.setNgayBatDau(now);
                bh.setNgayKetThuc(now.plusMonths(thang));
                baoHanhRepository.save(bh);
            }
        }

        // 6. cấp số hóa đơn, chốt
        String prefix = "HD-" + quay.getMaQuayCode().replaceAll("[^A-Za-z0-9]", "").toUpperCase()
                + "-" + now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        long stt = hoaDonRepository.countBySoHoaDonStartingWith(prefix) + 1;
        String so = prefix + String.format("%04d", stt);
        if (so.length() > 30) so = "HD-" + quay.getMaQuay() + "-" + now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", stt);
        h.setSoHoaDon(so);
        h.setTrangThai("da_thanh_toan");
        h.setThoiGianThanhToan(now);
        hoaDonRepository.flush();
        return toResponse(h, true);
    }

    @Transactional
    public HoaDonResponse ghiNhanIn(Integer id) {
        HoaDon h = hoaDonRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!"da_thanh_toan".equals(h.getTrangThai())) {
            throw BusinessException.conflict("HOA_DON_CHUA_THANH_TOAN", "Chỉ in hóa đơn đã thanh toán");
        }
        h.setSoLanIn(h.getSoLanIn() + 1);
        return toResponse(h, true);
    }

    // ===================== hủy hóa đơn đã thanh toán =====================

    /** Chỉ hủy trong cùng ngày thanh toán và khi chưa có phiếu trả; hóa đơn cũ hơn dùng chức năng trả hàng. */
    @Transactional
    public HoaDonResponse huyHoaDon(Integer id, String lyDo, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        HoaDon h = hoaDonRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!"da_thanh_toan".equals(h.getTrangThai())) {
            throw BusinessException.conflict("HOA_DON_KHONG_HUY_DUOC", "Chỉ hủy được hóa đơn đã thanh toán");
        }
        if (!h.getThoiGianThanhToan().toLocalDate().equals(LocalDate.now())) {
            throw BusinessException.conflict("QUA_NGAY_HUY", "Chỉ hủy hóa đơn trong ngày; hóa đơn cũ hơn hãy dùng chức năng trả hàng");
        }
        if (phieuTraRepository.existsByHoaDon_MaHoaDonAndTrangThaiNot(id, "tu_choi")) {
            throw BusinessException.conflict("DA_CO_PHIEU_TRA", "Hóa đơn đã có phiếu trả hàng, không thể hủy");
        }
        List<ChiTietHoaDon> dongs = dongHieuLuc(h);
        dongs.stream().filter(c -> c.getSerial() != null)
                .sorted(Comparator.comparing((ChiTietHoaDon c) -> c.getSerial().getMaSerial()))
                .forEach(c -> {
                    SerialSanPham s = serialRepository.findByIdForUpdate(c.getSerial().getMaSerial())
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy thiết bị"));
                    if (!"da_ban".equals(s.getTrangThai())) {
                        throw BusinessException.conflict("SERIAL_SAI_TRANG_THAI",
                                "Thiết bị " + s.getSoSerial() + " đang ở trạng thái '" + s.getTrangThai() + "', không thể hoàn kho");
                    }
                    s.setTrangThai("trong_kho");
                    tonKhoService.ghiLichSuSerial(s, "da_ban", "trong_kho", "hoa_don", h.getMaHoaDon(),
                            u.getMaNhanVien(), "Hủy hóa đơn: " + lyDo);
                });
        for (Map.Entry<Integer, Integer> e : tongTheoSanPham(dongs).entrySet()) {
            tonKhoService.dieuChinh(e.getKey(), e.getValue(), "huy_hoa_don", "hoa_don", h.getMaHoaDon(),
                    u.getMaNhanVien(), "Hủy hóa đơn: " + lyDo);
        }
        for (ChiTietHoaDon c : dongs) {
            c.setHieuLuc(false);
            baoHanhRepository.findByChiTietHoaDon_MaChiTiet(c.getMaChiTiet()).forEach(b -> b.setTrangThai("da_huy"));
        }
        h.setTrangThai("da_huy");
        h.setHuyBoi(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        h.setLyDoHuy(lyDo.trim());
        nhatKyService.ghi(u.getMaNhanVien(), "huy_hoa_don", "HoaDon", String.valueOf(id),
                "da_thanh_toan " + h.getSoHoaDon(), "da_huy: " + lyDo.trim(), ip);
        return toResponse(h, true);
    }

    // ===================== dọn nháp quá hạn =====================

    /** Dựa trên thời điểm tạo (HoaDon không có cột "cập nhật lần cuối"), nên ngưỡng mặc định rộng: 180 phút. */
    @Transactional
    public int donNhapQuaHan() {
        LocalDateTime moc = LocalDateTime.now().minusMinutes(draftExpireMinutes);
        int n = 0;
        for (HoaDon x : hoaDonRepository.findByTrangThaiInAndThoiGianTaoBefore(NHAP, moc)) {
            Optional<HoaDon> locked = hoaDonRepository.findByIdForUpdate(x.getMaHoaDon())
                    .filter(h -> NHAP.contains(h.getTrangThai()));
            if (locked.isPresent()) {
                hoaDonRepository.delete(locked.get());
                n++;
            }
        }
        return n;
    }

    // ===================== helpers =====================

    private HoaDon layCuaToi(Integer id, String... trangThais) {
        TaiKhoanUserDetails u = CurrentUser.require();
        HoaDon h = hoaDonRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!h.getThuNgan().getMaNhanVien().equals(u.getMaNhanVien())) {
            throw new AccessDeniedException("Hóa đơn thuộc thu ngân khác");
        }
        if (!Arrays.asList(trangThais).contains(h.getTrangThai())) {
            throw BusinessException.conflict("HOA_DON_SAI_TRANG_THAI", "Hóa đơn đang ở trạng thái '" + h.getTrangThai() + "'");
        }
        return h;
    }

    private KhachHang layKhach(Integer id) {
        return khachHangRepository.findById(id).filter(k -> k.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));
    }

    private void checkBanDuoc(SanPham sp) {
        if (sp.getNgayXoa() != null || !"dang_kinh_doanh".equals(sp.getTrangThai())) {
            throw BusinessException.conflict("SAN_PHAM_NGUNG_KINH_DOANH", "Sản phẩm " + sp.getMaSKU() + " đã ngừng kinh doanh");
        }
    }

    private void kiemTraTon(SanPham sp, int canBan) {
        int ton = tonKhoRepository.findBySanPham_MaSanPham(sp.getMaSanPham()).map(TonKho::getSlTon).orElse(0);
        if (ton < canBan) {
            throw BusinessException.conflict("TON_KHO_KHONG_DU",
                    "Sản phẩm " + sp.getMaSKU() + " chỉ còn " + ton + " trong kho");
        }
    }

    private void themDongMoi(HoaDon h, SanPham sp, SerialSanPham serial, int sl) {
        ChiTietHoaDon c = new ChiTietHoaDon();
        c.setSanPham(sp);
        c.setSerial(serial);
        c.setTenHang(sp.getTenSanPham());
        c.setMaSKU(sp.getMaSKU());
        c.setSoLuong(sl);
        c.setDonGia(sp.getGiaNiemYet());
        c.setThueSuat(sp.getThueSuatVAT());
        c.setGiaVon(serial != null ? serial.getGiaVon() : null);
        h.addChiTietHoaDon(c);
    }

    private ChiTietHoaDon timDong(HoaDon h, Integer maChiTiet) {
        return h.getChiTiets().stream()
                .filter(c -> c.getMaChiTiet() != null && c.getMaChiTiet().equals(maChiTiet) && Boolean.TRUE.equals(c.getHieuLuc()))
                .findFirst().orElseThrow(() -> new NotFoundException("Không tìm thấy dòng hàng #" + maChiTiet));
    }

    private static List<ChiTietHoaDon> dongHieuLuc(HoaDon h) {
        return h.getChiTiets().stream().filter(c -> Boolean.TRUE.equals(c.getHieuLuc())).toList();
    }

    private static Map<Integer, Integer> tongTheoSanPham(List<ChiTietHoaDon> dongs) {
        Map<Integer, Integer> m = new TreeMap<>();      // TreeMap: khóa tồn kho theo thứ tự mã, tránh deadlock
        dongs.forEach(c -> m.merge(c.getSanPham().getMaSanPham(), c.getSoLuong(), Integer::sum));
        return m;
    }

    /** Giảm giá tay: chỉ khi SP có giá sàn; đơn giá sau giảm không thấp hơn giá sàn. */
    private static void validateGiamGia(ChiTietHoaDon c) {
        BigDecimal gross = c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong()));
        BigDecimal giam = c.getGiamGia();
        if (giam.signum() < 0 || giam.compareTo(gross) > 0) {
            throw BusinessException.badRequest("GIAM_GIA_KHONG_HOP_LE", "Số tiền giảm không hợp lệ cho " + c.getMaSKU());
        }
        if (giam.signum() == 0) return;
        BigDecimal san = c.getSanPham().getGiaToiThieu();
        if (san == null) {
            throw BusinessException.badRequest("KHONG_DUOC_GIAM_GIA", "Sản phẩm " + c.getMaSKU() + " chưa có giá tối thiểu nên không được giảm giá tay");
        }
        if (gross.subtract(giam).compareTo(san.multiply(BigDecimal.valueOf(c.getSoLuong()))) < 0) {
            throw BusinessException.badRequest("DUOI_GIA_SAN", "Giá sau giảm của " + c.getMaSKU() + " thấp hơn giá tối thiểu");
        }
    }

    private static void tinhLai(HoaDon h) {
        BigDecimal hang = BigDecimal.ZERO, giam = BigDecimal.ZERO, thue = BigDecimal.ZERO;
        for (ChiTietHoaDon c : dongHieuLuc(h)) {
            BigDecimal gross = c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong()));
            hang = hang.add(gross);
            giam = giam.add(c.getGiamGia());
            thue = thue.add(tienThue(c, gross));
        }
        h.setTongTienHang(hang);
        h.setTongGiamGia(giam);
        h.setTongTienThue(thue);
    }

    private static BigDecimal tienThue(ChiTietHoaDon c, BigDecimal gross) {
        return gross.subtract(c.getGiamGia()).multiply(c.getThueSuat())
                .divide(BigDecimal.valueOf(100).add(c.getThueSuat()), 2, RoundingMode.HALF_UP);
    }

    private static void kiemTraThanhToan(DongThanhToanRequest p) {
        if (!PHUONG_THUC.contains(p.phuongThuc())) {
            throw BusinessException.badRequest("PHUONG_THUC_KHONG_HOP_LE", "Phương thức thanh toán không hợp lệ: " + p.phuongThuc());
        }
        boolean tienMat = "tien_mat".equals(p.phuongThuc());
        boolean traGop = "tra_gop".equals(p.phuongThuc());
        if (tienMat) {
            if (p.tienKhachDua() != null && p.tienKhachDua().compareTo(p.soTien()) < 0) {
                throw BusinessException.badRequest("TIEN_KHACH_DUA_THIEU", "Tiền khách đưa nhỏ hơn số tiền thanh toán");
            }
        } else {
            if (p.tienKhachDua() != null) {
                throw BusinessException.badRequest("TIEN_KHACH_DUA_CHI_TIEN_MAT", "Chỉ nhập tiền khách đưa với tiền mặt");
            }
            if (blankToNull(p.maThamChieu()) == null) {
                throw BusinessException.badRequest("THIEU_MA_THAM_CHIEU", "Thanh toán không dùng tiền mặt cần mã giao dịch/tham chiếu");
            }
        }
        if (traGop) {
            if (blankToNull(p.donViTraGop()) == null || p.soKyHan() == null) {
                throw BusinessException.badRequest("THIEU_THONG_TIN_TRA_GOP", "Trả góp cần đơn vị trả góp và số kỳ hạn");
            }
        } else if (blankToNull(p.donViTraGop()) != null || p.soKyHan() != null) {
            throw BusinessException.badRequest("THONG_TIN_TRA_GOP_THUA", "Chỉ nhập đơn vị/kỳ hạn với thanh toán trả góp");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private HoaDonResponse toResponse(HoaDon h, boolean kemChiTiet) {
        KhachHang k = h.getKhachHang();
        List<DongHoaDonResponse> dongs = null;
        List<ThanhToanResponse> tts = null;
        if (kemChiTiet) {
            dongs = h.getChiTiets().stream().map(c -> {
                BigDecimal gross = c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong()));
                return new DongHoaDonResponse(c.getMaChiTiet(), c.getSanPham().getMaSanPham(), c.getMaSKU(), c.getTenHang(),
                        c.getSerial() == null ? null : c.getSerial().getMaSerial(),
                        c.getSerial() == null ? null : c.getSerial().getSoSerial(),
                        c.getSoLuong(), c.getDonGia(), c.getGiamGia(), gross.subtract(c.getGiamGia()), c.getThueSuat(),
                        tienThue(c, gross), c.getSanPham().getBaoHanhThang(), c.getHieuLuc());
            }).toList();
            tts = h.getMaHoaDon() == null ? List.of() : thanhToanRepository.findByHoaDon_MaHoaDon(h.getMaHoaDon()).stream()
                    .map(t -> new ThanhToanResponse(t.getPhuongThuc(), t.getTrangThai(), t.getSoTien(), t.getTienKhachDua(),
                            t.getTienKhachDua() == null ? null : t.getTienKhachDua().subtract(t.getSoTien()),
                            t.getMaThamChieu(), t.getDonViTraGop(), t.getSoKyHan(), t.getThanhToanLuc())).toList();
        }
        return new HoaDonResponse(h.getMaHoaDon(), h.getSoHoaDon().startsWith("TMP-") ? null : h.getSoHoaDon(),
                h.getQuay().getMaQuay(), h.getQuay().getTenQuay(), h.getThuNgan().getHoTen(),
                k == null ? null : k.getMaKhachHang(), k == null ? null : k.getHoTen(), k == null ? null : k.getSoDienThoai(),
                h.getTrangThai(), h.getTongTienHang(), h.getTongGiamGia(), h.getTongTienHang().subtract(h.getTongGiamGia()),
                h.getTongTienThue(), h.getSoLanIn(), h.getThoiGianTao(), h.getThoiGianThanhToan(), h.getLyDoHuy(), dongs, tts);
    }
}