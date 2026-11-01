package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.BaoHanhDtos.*;
import com.xenon.qldapm.dto.PageResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Bảo hành: tra cứu, tiếp nhận yêu cầu, theo dõi trạng thái, gia hạn. (Không quản lý sửa chữa/linh kiện - ngoài phạm vi.)
 *
 * Quy tắc:
 *  - Bảo hành hãng được tạo khi bán (PosService). Gia hạn (mo_rong) nối tiếp ngay sau lúc hết hạn cuối cùng của dòng hàng,
 *    chỉ khi dòng còn trong thời hạn; thời hạn theo chính sách đang áp dụng.
 *  - Tiếp nhận: hóa đơn đã thanh toán, dòng hàng còn hiệu lực (chưa trả), có bảo hành đang trong thời hạn
 *    (nếu mã chọn đã hết nhưng bản gia hạn còn hạn thì dùng bản đó); mỗi dòng chỉ có 1 yêu cầu đang mở.
 *  - Serial: tiếp nhận -> 'bao_hanh'; khi trả khách (da_tra_khach) -> về 'da_ban'; ghi vòng đời Serial.
 *  - Luồng: tiep_nhan -> dang_xu_ly -> hoan_tat -> da_tra_khach; tu_choi (từ tiep_nhan/dang_xu_ly) -> da_tra_khach.
 *    hoan_tat/tu_choi bắt buộc có kết quả xử lý. Hẹn trả mặc định app.warranty.default-days (14) ngày.
 */
@Service
@RequiredArgsConstructor
public class BaoHanhService {

    private static final Set<String> DANG_MO = Set.of("tiep_nhan", "dang_xu_ly", "hoan_tat");
    private static final Map<String, Set<String>> CHUYEN = Map.of(
            "tiep_nhan", Set.of("dang_xu_ly", "tu_choi"),
            "dang_xu_ly", Set.of("hoan_tat", "tu_choi"),
            "hoan_tat", Set.of("da_tra_khach"),
            "tu_choi", Set.of("da_tra_khach"),
            "da_tra_khach", Set.of());

    private final BaoHanhRepository baoHanhRepository;
    private final YeuCauBaoHanhRepository yeuCauRepository;
    private final ChinhSachBaoHanhRepository chinhSachRepository;
    private final ChiTietHoaDonRepository chiTietHoaDonRepository;
    private final HoaDonRepository hoaDonRepository;
    private final SerialSanPhamRepository serialRepository;
    private final KhachHangRepository khachHangRepository;
    private final NhanVienRepository nhanVienRepository;
    private final TonKhoService tonKhoService;
    private final NhatKyService nhatKyService;

    @Value("${app.warranty.default-days:14}")
    private int hanTraMacDinh;

    // ===================== tra cứu =====================

    /** Một ô tra cứu: Serial/IMEI -> số hóa đơn -> số điện thoại khách. */
    @Transactional(readOnly = true)
    public TraCuuBaoHanhResponse traCuu(String q) {
        String ma = q.trim();
        LocalDateTime now = LocalDateTime.now();

        Optional<SerialSanPham> so = serialRepository.findByAnyCode(ma);
        if (so.isPresent()) {
            SerialSanPham s = so.get();
            List<BaoHanh> bhs = baoHanhRepository.findBySerial_MaSerial(s.getMaSerial());
            SerialTomTat tt = new SerialTomTat(s.getMaSerial(), s.getSoSerial(), s.getImei(), s.getImei2(), s.getTrangThai(),
                    s.getSanPham().getMaSKU(), s.getSanPham().getTenSanPham());
            return new TraCuuBaoHanhResponse("serial", tt, mapBh(bhs, now), mapYc(layYeuCau(bhs, null)));
        }
        if (hoaDonRepository.findBySoHoaDon(ma).isPresent()) {
            List<BaoHanh> bhs = baoHanhRepository.findByChiTietHoaDon_HoaDon_SoHoaDon(ma);
            return new TraCuuBaoHanhResponse("hoa_don", null, mapBh(bhs, now), mapYc(layYeuCau(bhs, null)));
        }
        String sdt = null;
        try { sdt = KhachHangService.chuanHoaSdt(ma); } catch (BusinessException ignored) { }
        if (sdt != null) {
            Optional<KhachHang> kh = khachHangRepository.findBySoDienThoaiAndNgayXoaIsNull(sdt);
            List<BaoHanh> bhs = kh.map(k -> baoHanhRepository.timTheoKhachHang(k.getMaKhachHang())).orElse(List.of());
            List<YeuCauBaoHanh> ycs = layYeuCau(bhs, sdt);
            if (kh.isPresent() || !ycs.isEmpty()) {
                return new TraCuuBaoHanhResponse("khach_hang", null, mapBh(bhs, now), mapYc(ycs));
            }
        }
        throw new NotFoundException("Không tìm thấy Serial/IMEI, hóa đơn hoặc khách hàng với: " + ma);
    }

    // ===================== gia hạn =====================

    @Transactional
    public BaoHanhResponse giaHan(GiaHanRequest r, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        ChiTietHoaDon c = chiTietHoaDonRepository.findById(r.maChiTietHD())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dòng hóa đơn"));
        HoaDon h = hoaDonRepository.findByIdForUpdate(c.getHoaDon().getMaHoaDon())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!"da_thanh_toan".equals(h.getTrangThai()) || !Boolean.TRUE.equals(c.getHieuLuc())) {
            throw BusinessException.conflict("HANG_KHONG_GIA_HAN_DUOC", "Chỉ gia hạn cho hàng thuộc hóa đơn đã thanh toán và chưa trả");
        }
        ChinhSachBaoHanh cs = chinhSachRepository.findById(r.maChinhSach()).filter(x -> Boolean.TRUE.equals(x.getTrangThai()))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chính sách bảo hành đang áp dụng"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cuoi = baoHanhRepository.findByChiTietHoaDon_MaChiTiet(c.getMaChiTiet()).stream()
                .filter(b -> !"da_huy".equals(b.getTrangThai())).map(BaoHanh::getNgayKetThuc)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> BusinessException.conflict("KHONG_CO_BAO_HANH", "Dòng hàng này không có bảo hành để gia hạn"));
        if (!cuoi.isAfter(now)) {
            throw BusinessException.conflict("BAO_HANH_DA_HET_HAN", "Bảo hành đã hết hạn ngày " + cuoi.toLocalDate() + ", không thể gia hạn");
        }
        BaoHanh m = new BaoHanh();
        m.setChiTietHoaDon(c);
        m.setSerial(c.getSerial());
        m.setChinhSach(cs);
        m.setLoaiBaoHanh("mo_rong");
        m.setNgayBatDau(cuoi);
        m.setNgayKetThuc(cuoi.plusMonths(cs.getThoiHanThang()));
        m = baoHanhRepository.saveAndFlush(m);
        nhatKyService.ghi(u.getMaNhanVien(), "gia_han_bao_hanh", "BaoHanh", String.valueOf(m.getMaBaoHanh()),
                "het han " + cuoi, "+" + cs.getThoiHanThang() + " thang (" + cs.getTenChinhSach() + ") den " + m.getNgayKetThuc(), ip);
        return toBh(m, now);
    }

    // ===================== yêu cầu bảo hành =====================

    @Transactional
    public YeuCauResponse tiepNhan(TiepNhanRequest r, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        BaoHanh chon = baoHanhRepository.findById(r.maBaoHanh())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin bảo hành"));
        ChiTietHoaDon c = chon.getChiTietHoaDon();
        HoaDon h = hoaDonRepository.findByIdForUpdate(c.getHoaDon().getMaHoaDon())     // khóa hóa đơn: chặn tiếp nhận trùng
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hóa đơn"));
        if (!"da_thanh_toan".equals(h.getTrangThai()) || !Boolean.TRUE.equals(c.getHieuLuc())) {
            throw BusinessException.conflict("HANG_DA_TRA_HOAC_HUY", "Hàng thuộc hóa đơn đã hủy hoặc đã trả, không còn bảo hành");
        }
        LocalDateTime now = LocalDateTime.now();
        List<BaoHanh> cungDong = baoHanhRepository.findByChiTietHoaDon_MaChiTiet(c.getMaChiTiet());
        BaoHanh bh = cungDong.stream().filter(b -> dangCoHieuLuc(b, now)).findFirst()
                .orElseThrow(() -> BusinessException.conflict("BAO_HANH_HET_HAN", "Hàng đã hết hạn bảo hành"
                        + cungDong.stream().map(BaoHanh::getNgayKetThuc).max(Comparator.naturalOrder())
                        .map(d -> " (ngày " + d.toLocalDate() + ")").orElse("")));
        for (BaoHanh b : cungDong) {
            if (yeuCauRepository.existsByBaoHanh_MaBaoHanhAndTrangThaiIn(b.getMaBaoHanh(), DANG_MO)) {
                throw BusinessException.conflict("DA_CO_YEU_CAU_DANG_MO", "Thiết bị này đang có yêu cầu bảo hành chưa trả khách");
            }
        }
        SerialSanPham serial = null;
        if (bh.getSerial() != null) {
            serial = serialRepository.findByIdForUpdate(bh.getSerial().getMaSerial())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy thiết bị"));
            if (!"da_ban".equals(serial.getTrangThai())) {
                throw BusinessException.conflict("SERIAL_SAI_TRANG_THAI",
                        "Thiết bị " + serial.getSoSerial() + " đang ở trạng thái '" + serial.getTrangThai() + "'");
            }
        }
        YeuCauBaoHanh y = new YeuCauBaoHanh();
        y.setMaSoTiepNhan("TMP-" + UUID.randomUUID().toString().substring(0, 12));
        y.setBaoHanh(bh);
        y.setNhanVienTiepNhan(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        y.setTenNguoiGui(r.tenNguoiGui().trim());
        y.setSoDienThoaiNguoiGui(KhachHangService.chuanHoaSdt(r.soDienThoaiNguoiGui()));
        y.setMoTaSuCo(r.moTaSuCo().trim());
        y.setTinhTrangNhanMay(blankToNull(r.tinhTrangNhanMay()));
        y.setPhuKienKemTheo(blankToNull(r.phuKienKemTheo()));
        LocalDateTime hen = r.ngayHenTra() != null ? r.ngayHenTra() : y.getTiepNhanLuc().plusDays(hanTraMacDinh);
        kiemTraNgayHen(hen, y);
        y.setNgayHenTra(hen);
        y = yeuCauRepository.saveAndFlush(y);
        y.setMaSoTiepNhan(String.format("BH-%s-%06d", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), y.getMaYeuCau()));

        if (serial != null) {
            serial.setTrangThai("bao_hanh");
            tonKhoService.ghiLichSuSerial(serial, "da_ban", "bao_hanh", "yeu_cau_bao_hanh", y.getMaYeuCau(),
                    u.getMaNhanVien(), "Tiếp nhận bảo hành " + y.getMaSoTiepNhan());
        }
        nhatKyService.ghi(u.getMaNhanVien(), "tiep_nhan_bao_hanh", "YeuCauBaoHanh", String.valueOf(y.getMaYeuCau()),
                null, y.getMaSoTiepNhan() + " / " + c.getMaSKU(), ip);
        return toYc(y);
    }

    @Transactional
    public YeuCauResponse capNhat(Integer id, CapNhatYeuCauRequest r, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        YeuCauBaoHanh y = yeuCauRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu bảo hành"));
        String cu = y.getTrangThai();
        if ("da_tra_khach".equals(cu)) {
            throw BusinessException.conflict("YEU_CAU_DA_DONG", "Yêu cầu đã trả khách, không thể chỉnh sửa");
        }
        if (r.ketQuaXuLy() != null) y.setKetQuaXuLy(blankToNull(r.ketQuaXuLy()));
        if (r.ngayHenTra() != null) {
            kiemTraNgayHen(r.ngayHenTra(), y);
            y.setNgayHenTra(r.ngayHenTra());
        }
        String moi = r.trangThai();
        if (moi != null && !moi.equals(cu)) {
            if (!CHUYEN.getOrDefault(cu, Set.of()).contains(moi)) {
                throw BusinessException.conflict("CHUYEN_TRANG_THAI_KHONG_HOP_LE", "Không thể chuyển từ '" + cu + "' sang '" + moi + "'");
            }
            if (("hoan_tat".equals(moi) || "tu_choi".equals(moi)) && y.getKetQuaXuLy() == null) {
                throw BusinessException.badRequest("THIEU_KET_QUA_XU_LY", "Cần nhập kết quả xử lý / lý do từ chối");
            }
            LocalDateTime now = LocalDateTime.now();
            switch (moi) {
                case "hoan_tat", "tu_choi" -> y.setHoanTatLuc(now);
                case "da_tra_khach" -> {
                    y.setTraKhachLuc(now);
                    traSerialVeDaBan(y, u.getMaNhanVien());
                }
                default -> { }
            }
            y.setTrangThai(moi);
            nhatKyService.ghi(u.getMaNhanVien(), "cap_nhat_bao_hanh", "YeuCauBaoHanh", String.valueOf(id), cu, moi, ip);
        } else if (moi != null) {
            throw BusinessException.badRequest("TRANG_THAI_KHONG_DOI", "Yêu cầu đã ở trạng thái '" + cu + "'");
        }
        return toYc(y);
    }

    @Transactional(readOnly = true)
    public YeuCauResponse chiTiet(Integer id) {
        return toYc(yeuCauRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu bảo hành")));
    }

    @Transactional(readOnly = true)
    public YeuCauResponse theoMa(String maSoTiepNhan) {
        return toYc(yeuCauRepository.findByMaSoTiepNhan(maSoTiepNhan.trim())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu tiếp nhận " + maSoTiepNhan)));
    }

    @Transactional(readOnly = true)
    public List<YeuCauResponse> theoSdt(String sdt) {
        return mapYc(yeuCauRepository.findBySoDienThoaiNguoiGui(KhachHangService.chuanHoaSdt(sdt)).stream()
                .sorted(Comparator.comparing(YeuCauBaoHanh::getTiepNhanLuc).reversed()).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<YeuCauResponse> danhSach(String trangThai, Pageable pageable) {
        Page<YeuCauBaoHanh> page = trangThai != null && !trangThai.isBlank()
                ? yeuCauRepository.findByTrangThai(trangThai, pageable) : yeuCauRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toYc));
    }

    /** Job định kỳ: đánh dấu bảo hành quá hạn. */
    @Transactional
    public int danhDauHetHan() {
        return baoHanhRepository.danhDauHetHan(LocalDateTime.now());
    }

    // ===================== helpers =====================

    private void traSerialVeDaBan(YeuCauBaoHanh y, Integer maNhanVien) {
        SerialSanPham s0 = y.getBaoHanh().getSerial();
        if (s0 == null) return;
        SerialSanPham s = serialRepository.findByIdForUpdate(s0.getMaSerial()).orElse(null);
        if (s != null && "bao_hanh".equals(s.getTrangThai())) {
            s.setTrangThai("da_ban");
            tonKhoService.ghiLichSuSerial(s, "bao_hanh", "da_ban", "yeu_cau_bao_hanh", y.getMaYeuCau(),
                    maNhanVien, "Trả khách " + y.getMaSoTiepNhan());
        }
    }

    private static void kiemTraNgayHen(LocalDateTime hen, YeuCauBaoHanh y) {
        if (hen.isBefore(y.getTiepNhanLuc())) {
            throw BusinessException.badRequest("NGAY_HEN_KHONG_HOP_LE", "Ngày hẹn trả không được trước thời điểm tiếp nhận");
        }
    }

    private static boolean dangCoHieuLuc(BaoHanh b, LocalDateTime now) {
        return "con_han".equals(b.getTrangThai()) && !b.getNgayBatDau().isAfter(now) && b.getNgayKetThuc().isAfter(now);
    }

    private List<YeuCauBaoHanh> layYeuCau(List<BaoHanh> bhs, String sdt) {
        Map<Integer, YeuCauBaoHanh> kq = new LinkedHashMap<>();
        for (BaoHanh b : bhs) {
            yeuCauRepository.findByBaoHanh_MaBaoHanh(b.getMaBaoHanh()).forEach(y -> kq.put(y.getMaYeuCau(), y));
        }
        if (sdt != null) yeuCauRepository.findBySoDienThoaiNguoiGui(sdt).forEach(y -> kq.put(y.getMaYeuCau(), y));
        return kq.values().stream().sorted(Comparator.comparing(YeuCauBaoHanh::getTiepNhanLuc).reversed()).toList();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private List<BaoHanhResponse> mapBh(List<BaoHanh> bhs, LocalDateTime now) {
        return bhs.stream().sorted(Comparator.comparing(BaoHanh::getNgayBatDau)).map(b -> toBh(b, now)).toList();
    }

    private List<YeuCauResponse> mapYc(List<YeuCauBaoHanh> ycs) { return ycs.stream().map(this::toYc).toList(); }

    private BaoHanhResponse toBh(BaoHanh b, LocalDateTime now) {
        ChiTietHoaDon c = b.getChiTietHoaDon();
        HoaDon h = c.getHoaDon();
        KhachHang k = h.getKhachHang();
        SerialSanPham s = b.getSerial();
        boolean conHan = "con_han".equals(b.getTrangThai());
        String tt = conHan && !b.getNgayKetThuc().isAfter(now) ? "het_han" : b.getTrangThai();
        long ngayCon = conHan ? Math.max(ChronoUnit.DAYS.between(now, b.getNgayKetThuc()), 0) : 0;
        return new BaoHanhResponse(b.getMaBaoHanh(), c.getMaChiTiet(), h.getSoHoaDon(), h.getThoiGianThanhToan(),
                c.getMaSKU(), c.getTenHang(), s == null ? null : s.getMaSerial(), s == null ? null : s.getSoSerial(),
                s == null ? null : s.getImei(), b.getLoaiBaoHanh(),
                b.getChinhSach() == null ? null : b.getChinhSach().getTenChinhSach(), b.getNgayBatDau(),
                b.getNgayKetThuc(), tt, dangCoHieuLuc(b, now), ngayCon,
                k == null ? null : k.getHoTen(), k == null ? null : k.getSoDienThoai());
    }

    private YeuCauResponse toYc(YeuCauBaoHanh y) {
        ChiTietHoaDon c = y.getBaoHanh().getChiTietHoaDon();
        SerialSanPham s = y.getBaoHanh().getSerial();
        return new YeuCauResponse(y.getMaYeuCau(), y.getMaSoTiepNhan(), y.getBaoHanh().getMaBaoHanh(),
                c.getHoaDon().getSoHoaDon(), c.getMaSKU(), c.getTenHang(), s == null ? null : s.getSoSerial(),
                y.getTenNguoiGui(), y.getSoDienThoaiNguoiGui(), y.getMoTaSuCo(), y.getTinhTrangNhanMay(),
                y.getPhuKienKemTheo(), y.getTrangThai(), y.getKetQuaXuLy(), y.getNgayHenTra(), y.getTiepNhanLuc(),
                y.getHoanTatLuc(), y.getTraKhachLuc(), y.getNhanVienTiepNhan().getHoTen());
    }
}