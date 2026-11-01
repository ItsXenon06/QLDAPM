package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.BaoCaoDtos.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Báo cáo - thống kê (SQL thuần qua JdbcTemplate, chỉ đọc, dựa trên các index báo cáo của schema).
 *
 * Quy ước doanh thu:
 *  - Doanh thu gộp = TongThanhToan của hóa đơn 'da_thanh_toan' (hóa đơn đã hủy không tính), đã gồm VAT.
 *  - Tiền hoàn = TongTienHoan của phiếu trả 'hoan_tat'.
 *  - Doanh thu thuần = gộp - hoàn. Báo cáo theo thời gian/quầy/thu ngân tính tiền hoàn vào NGÀY HOÀN TẤT TRẢ
 *    (theo dòng tiền); báo cáo theo ngành hàng/sản phẩm trừ tiền hoàn vào chính dòng đã bán (theo ngày bán).
 *  - Ngành hàng gộp lên ngành gốc (Tivi, Tủ lạnh, Điện thoại...). Quà tặng không tính vào số lượng bán.
 */
@Service
@RequiredArgsConstructor
public class BaoCaoService {

    private static final DateTimeFormatter NGAY_GIO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private record Chieu(String tieuDe, String cotId, String join, String cotMa, String cotTen, String nhanMa, String nhanTen) {}

    private static final Chieu QUAY = new Chieu("Doanh thu theo quầy POS", "h.MaQuay",
            "JOIN QuayPOS d ON d.MaQuay = h.MaQuay", "d.MaQuayCode", "d.TenQuay", "Mã quầy", "Tên quầy");
    private static final Chieu THU_NGAN = new Chieu("Doanh thu theo thu ngân", "h.MaThuNgan",
            "JOIN NhanVien d ON d.MaNhanVien = h.MaThuNgan", "d.MaSoNhanVien", "d.HoTen", "Mã nhân viên", "Thu ngân");

    private static final class Acc {
        String ma = "", ten = "";
        long soHd;
        BigDecimal tienHang = BigDecimal.ZERO, giam = BigDecimal.ZERO, gop = BigDecimal.ZERO,
                thue = BigDecimal.ZERO, hoan = BigDecimal.ZERO;
        BigDecimal thuan() { return gop.subtract(hoan); }
    }

    private record Khoang(LocalDate tu, LocalDate den) {
        Timestamp tuTs() { return Timestamp.valueOf(tu.atStartOfDay()); }
        Timestamp denTs() { return Timestamp.valueOf(den.plusDays(1).atStartOfDay()); }
        String moTa() { return tu.equals(den) ? tu.format(NGAY) : tu.format(NGAY) + " – " + den.format(NGAY); }
    }

    private final JdbcTemplate jdbc;

    @Value("${app.report.slow-stock-days:90}")
    private int tonLauMacDinh;

    // ===================== điều phối =====================

    public BaoCaoBang tao(String loai, BaoCaoThamSo p) {
        return switch (loai) {
            case "doanh-thu" -> doanhThu(p.tu(), p.den(), p.nhom(), p.maQuay(), p.maThuNgan());
            case "doanh-thu-quay" -> doanhThuTheoChieu(QUAY, p.tu(), p.den());
            case "doanh-thu-thu-ngan" -> doanhThuTheoChieu(THU_NGAN, p.tu(), p.den());
            case "doanh-thu-nganh-hang" -> doanhThuNganhHang(p.tu(), p.den());
            case "doanh-thu-san-pham" -> doanhThuSanPham(p.tu(), p.den(), p.top());
            case "ton-kho" -> tonKho(Boolean.TRUE.equals(p.chiCanhBao()));
            case "ton-kho-serial" -> serialTonKho(p.maSanPham(), null);
            case "ton-lau" -> serialTonKho(p.maSanPham(), p.soNgay() == null ? tonLauMacDinh : p.soNgay());
            case "chenh-lech-ton" -> chenhLechTon();
            default -> throw new NotFoundException("Không có báo cáo: " + loai);
        };
    }

    // ===================== tổng quan (bảng điều khiển thời gian thực) =====================

    public TongQuanResponse tongQuan() {
        LocalDate hn = LocalDate.now();
        Timestamp t0 = Timestamp.valueOf(hn.atStartOfDay());
        Timestamp t1 = Timestamp.valueOf(hn.plusDays(1).atStartOfDay());

        Map<String, Object> ban = jdbc.queryForMap("SELECT COUNT(*) AS SoHD, ISNULL(SUM(TongThanhToan), 0) AS Gop FROM HoaDon "
                + "WHERE TrangThai = N'da_thanh_toan' AND ThoiGianThanhToan >= ? AND ThoiGianThanhToan < ?", t0, t1);
        BigDecimal gop = d(ban.get("Gop"));
        BigDecimal hoan = d(jdbc.queryForObject("SELECT ISNULL(SUM(TongTienHoan), 0) FROM PhieuTraHang "
                + "WHERE TrangThai = N'hoan_tat' AND HoanTatLuc >= ? AND HoanTatLuc < ?", BigDecimal.class, t0, t1));
        long nhap = l(jdbc.queryForObject("SELECT COUNT(*) FROM HoaDon WHERE TrangThai IN (N'dang_lap', N'tam_giu')", Long.class));
        long canhBao = l(jdbc.queryForObject("SELECT COUNT(*) FROM vw_CanhBaoTonKho", Long.class));
        long bh = l(jdbc.queryForObject("SELECT COUNT(*) FROM YeuCauBaoHanh WHERE TrangThai IN (N'tiep_nhan', N'dang_xu_ly', N'hoan_tat')", Long.class));

        BigDecimal[] gio = new BigDecimal[24];
        Arrays.fill(gio, BigDecimal.ZERO);
        for (Map<String, Object> r : jdbc.queryForList("SELECT DATEPART(HOUR, ThoiGianThanhToan) AS Gio, SUM(TongThanhToan) AS DT FROM HoaDon "
                + "WHERE TrangThai = N'da_thanh_toan' AND ThoiGianThanhToan >= ? AND ThoiGianThanhToan < ? "
                + "GROUP BY DATEPART(HOUR, ThoiGianThanhToan)", t0, t1)) {
            gio[(int) l(r.get("Gio"))] = d(r.get("DT"));
        }
        List<DiemDoanhThu> theoGio = new ArrayList<>();
        for (int i = 0; i < 24; i++) theoGio.add(new DiemDoanhThu(String.format("%02d", i), gio[i]));
        return new TongQuanResponse(hn, l(ban.get("SoHD")), gop, hoan, gop.subtract(hoan), nhap, canhBao, bh, theoGio);
    }

    // ===================== doanh thu =====================

    private BaoCaoBang doanhThu(LocalDate tu, LocalDate den, String nhom, Integer maQuay, Integer maThuNgan) {
        String g = nhom == null || nhom.isBlank() ? "ngay" : nhom;
        int len, macDinh, toiDa;
        String nhan;
        switch (g) {
            case "gio" -> { len = 13; macDinh = 1; toiDa = 31; nhan = "Giờ"; }
            case "ngay" -> { len = 10; macDinh = 30; toiDa = 366; nhan = "Ngày"; }
            case "thang" -> { len = 7; macDinh = 365; toiDa = 3660; nhan = "Tháng"; }
            default -> throw BusinessException.badRequest("NHOM_KHONG_HOP_LE", "nhom phải là gio, ngay hoặc thang");
        }
        Khoang k = khoang(tu, den, macDinh, toiDa);
        List<Object> a = new ArrayList<>(List.of(k.tuTs(), k.denTs()));
        StringBuilder loc = new StringBuilder();
        if (maQuay != null) { loc.append(" AND h.MaQuay = ?"); a.add(maQuay); }
        if (maThuNgan != null) { loc.append(" AND h.MaThuNgan = ?"); a.add(maThuNgan); }

        String bt1 = "CONVERT(varchar(" + len + "), h.ThoiGianThanhToan, 120)";
        String bt2 = "CONVERT(varchar(" + len + "), p.HoanTatLuc, 120)";
        String sqlBan = "SELECT " + bt1 + " AS Khoa, COUNT(*) AS SoHD, SUM(h.TongTienHang) AS TienHang, SUM(h.TongGiamGia) AS GiamGia, "
                + "SUM(h.TongThanhToan) AS DoanhThu, SUM(h.TongTienThue) AS Thue FROM HoaDon h "
                + "WHERE h.TrangThai = N'da_thanh_toan' AND h.ThoiGianThanhToan >= ? AND h.ThoiGianThanhToan < ?" + loc
                + " GROUP BY " + bt1;
        String sqlHoan = "SELECT " + bt2 + " AS Khoa, SUM(p.TongTienHoan) AS Hoan FROM PhieuTraHang p JOIN HoaDon h ON h.MaHoaDon = p.MaHoaDon "
                + "WHERE p.TrangThai = N'hoan_tat' AND p.HoanTatLuc >= ? AND p.HoanTatLuc < ?" + loc + " GROUP BY " + bt2;

        Map<String, Acc> m = new TreeMap<>();
        napBan(m, jdbc.queryForList(sqlBan, a.toArray()));
        napHoan(m, jdbc.queryForList(sqlHoan, a.toArray()));

        List<List<Object>> dong = new ArrayList<>();
        for (Map.Entry<String, Acc> e : m.entrySet()) {
            Acc x = e.getValue();
            String nhanDong = "gio".equals(g) ? e.getKey() + ":00" : e.getKey();
            dong.add(row(nhanDong, x.soHd, x.tienHang, x.giam, x.gop, x.hoan, x.thuan(), x.thue));
        }
        return bang("Doanh thu theo " + nhan.toLowerCase(), k.moTa(),
                List.of(nhan, "Số hóa đơn", "Tiền hàng", "Giảm giá", "Doanh thu gộp", "Tiền hoàn trả",
                        "Doanh thu thuần", "VAT (trong doanh thu gộp)"),
                dong, 1, 2, 3, 4, 5, 6, 7);
    }

    private BaoCaoBang doanhThuTheoChieu(Chieu c, LocalDate tu, LocalDate den) {
        Khoang k = khoang(tu, den, 30, 366);
        String nhom = c.cotId() + ", " + c.cotMa() + ", " + c.cotTen();
        String sqlBan = "SELECT " + c.cotId() + " AS Khoa, " + c.cotMa() + " AS Ma, " + c.cotTen() + " AS Ten, COUNT(*) AS SoHD, "
                + "SUM(h.TongTienHang) AS TienHang, SUM(h.TongGiamGia) AS GiamGia, SUM(h.TongThanhToan) AS DoanhThu, SUM(h.TongTienThue) AS Thue "
                + "FROM HoaDon h " + c.join() + " WHERE h.TrangThai = N'da_thanh_toan' AND h.ThoiGianThanhToan >= ? AND h.ThoiGianThanhToan < ? "
                + "GROUP BY " + nhom;
        String sqlHoan = "SELECT " + c.cotId() + " AS Khoa, " + c.cotMa() + " AS Ma, " + c.cotTen() + " AS Ten, SUM(p.TongTienHoan) AS Hoan "
                + "FROM PhieuTraHang p JOIN HoaDon h ON h.MaHoaDon = p.MaHoaDon " + c.join()
                + " WHERE p.TrangThai = N'hoan_tat' AND p.HoanTatLuc >= ? AND p.HoanTatLuc < ? GROUP BY " + nhom;
        Map<String, Acc> m = new LinkedHashMap<>();
        napBan(m, jdbc.queryForList(sqlBan, k.tuTs(), k.denTs()));
        napHoan(m, jdbc.queryForList(sqlHoan, k.tuTs(), k.denTs()));

        List<List<Object>> dong = new ArrayList<>();
        m.values().stream().sorted(Comparator.comparing(Acc::thuan).reversed())
                .forEach(x -> dong.add(row(x.ma, x.ten, x.soHd, x.giam, x.gop, x.hoan, x.thuan())));
        return bang(c.tieuDe(), k.moTa(),
                List.of(c.nhanMa(), c.nhanTen(), "Số hóa đơn", "Giảm giá", "Doanh thu gộp", "Tiền hoàn trả", "Doanh thu thuần"),
                dong, 2, 3, 4, 5, 6);
    }

    private static final String HOAN_DONG = " OUTER APPLY (SELECT SUM(t.SoTienHoan) AS Hoan, SUM(t.SoLuong) AS SL FROM ChiTietTraHang t "
            + "JOIN PhieuTraHang p ON p.MaPhieuTra = t.MaPhieuTra WHERE t.MaChiTietHD = ct.MaChiTiet AND p.TrangThai = N'hoan_tat') r ";

    private BaoCaoBang doanhThuNganhHang(LocalDate tu, LocalDate den) {
        Khoang k = khoang(tu, den, 30, 366);
        String sql = "WITH cay AS (SELECT MaDanhMuc, TenDanhMuc AS TenGoc, MaDanhMuc AS MaGoc FROM DanhMuc WHERE MaDanhMucCha IS NULL "
                + "UNION ALL SELECT c.MaDanhMuc, p.TenGoc, p.MaGoc FROM DanhMuc c JOIN cay p ON c.MaDanhMucCha = p.MaDanhMuc) "
                + "SELECT cay.TenGoc AS Ten, SUM(ct.SoLuong) AS SLBan, SUM(ISNULL(r.SL, 0)) AS SLTra, SUM(ct.ThanhTien) AS Gop, SUM(ISNULL(r.Hoan, 0)) AS Hoan "
                + "FROM ChiTietHoaDon ct JOIN HoaDon h ON h.MaHoaDon = ct.MaHoaDon AND h.TrangThai = N'da_thanh_toan' "
                + "JOIN SanPham sp ON sp.MaSanPham = ct.MaSanPham JOIN cay ON cay.MaDanhMuc = sp.MaDanhMuc" + HOAN_DONG
                + "WHERE ct.LaQuaTang = 0 AND h.ThoiGianThanhToan >= ? AND h.ThoiGianThanhToan < ? "
                + "GROUP BY cay.MaGoc, cay.TenGoc ORDER BY SUM(ct.ThanhTien) - SUM(ISNULL(r.Hoan, 0)) DESC";
        List<List<Object>> dong = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, k.tuTs(), k.denTs())) {
            BigDecimal gop = d(r.get("Gop")), hoan = d(r.get("Hoan"));
            long ban = l(r.get("SLBan")), tra = l(r.get("SLTra"));
            dong.add(row(r.get("Ten"), ban, tra, ban - tra, gop, hoan, gop.subtract(hoan)));
        }
        return bang("Doanh thu theo nhóm ngành hàng", k.moTa() + " (theo ngày bán)",
                List.of("Ngành hàng", "SL bán", "SL trả", "SL thuần", "Doanh thu gộp", "Tiền hoàn trả", "Doanh thu thuần"),
                dong, 1, 2, 3, 4, 5, 6);
    }

    private BaoCaoBang doanhThuSanPham(LocalDate tu, LocalDate den, Integer top) {
        Khoang k = khoang(tu, den, 30, 366);
        int n = top == null ? 10 : Math.max(1, Math.min(top, 100));
        String sql = "SELECT TOP (?) sp.MaSKU, sp.TenSanPham, SUM(ct.SoLuong) AS SLBan, SUM(ISNULL(r.SL, 0)) AS SLTra, "
                + "SUM(ct.ThanhTien) AS Gop, SUM(ISNULL(r.Hoan, 0)) AS Hoan "
                + "FROM ChiTietHoaDon ct JOIN HoaDon h ON h.MaHoaDon = ct.MaHoaDon AND h.TrangThai = N'da_thanh_toan' "
                + "JOIN SanPham sp ON sp.MaSanPham = ct.MaSanPham" + HOAN_DONG
                + "WHERE ct.LaQuaTang = 0 AND h.ThoiGianThanhToan >= ? AND h.ThoiGianThanhToan < ? "
                + "GROUP BY sp.MaSanPham, sp.MaSKU, sp.TenSanPham ORDER BY SUM(ct.ThanhTien) - SUM(ISNULL(r.Hoan, 0)) DESC";
        List<List<Object>> dong = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, n, k.tuTs(), k.denTs())) {
            BigDecimal gop = d(r.get("Gop")), hoan = d(r.get("Hoan"));
            long ban = l(r.get("SLBan")), tra = l(r.get("SLTra"));
            dong.add(row(r.get("MaSKU"), r.get("TenSanPham"), ban - tra, gop, hoan, gop.subtract(hoan)));
        }
        return bang("Top " + n + " sản phẩm bán chạy", k.moTa() + " (xếp theo doanh thu thuần)",
                List.of("SKU", "Sản phẩm", "SL thuần", "Doanh thu gộp", "Tiền hoàn trả", "Doanh thu thuần"),
                dong, 2, 3, 4, 5);
    }

    // ===================== tồn kho =====================

    private BaoCaoBang tonKho(boolean chiCanhBao) {
        String sql = "SELECT sp.MaSKU, sp.TenSanPham, dm.TenDanhMuc, tk.SLTon, tk.TonToiThieu, tk.ViTriKho, "
                + "CASE WHEN tk.SLTon = 0 THEN N'Hết hàng' WHEN tk.SLTon <= tk.TonToiThieu THEN N'Sắp hết' ELSE N'Bình thường' END AS MucDo, "
                + "v.GiaTri AS GiaTriTon FROM TonKho tk JOIN SanPham sp ON sp.MaSanPham = tk.MaSanPham "
                + "JOIN DanhMuc dm ON dm.MaDanhMuc = sp.MaDanhMuc "
                + "OUTER APPLY (SELECT SUM(s.GiaVon) AS GiaTri FROM SerialSanPham s WHERE s.MaSanPham = sp.MaSanPham AND s.TrangThai = N'trong_kho') v "
                + "WHERE sp.NgayXoa IS NULL" + (chiCanhBao ? " AND tk.SLTon <= tk.TonToiThieu" : "")
                + " ORDER BY tk.SLTon, sp.MaSKU";
        List<List<Object>> dong = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql)) {
            dong.add(row(r.get("MaSKU"), r.get("TenSanPham"), r.get("TenDanhMuc"), r.get("SLTon"), r.get("TonToiThieu"),
                    r.get("MucDo"), r.get("ViTriKho"), r.get("GiaTriTon")));
        }
        return bang(chiCanhBao ? "Hàng chạm ngưỡng tồn tối thiểu" : "Báo cáo tồn kho",
                "Giá trị tồn chỉ tính hàng theo dõi Serial (giá vốn từng thiết bị)",
                List.of("SKU", "Sản phẩm", "Ngành hàng", "SL tồn", "Tồn tối thiểu", "Mức độ", "Vị trí kho", "Giá trị tồn (Serial)"),
                dong, 3, 7);
    }

    private BaoCaoBang serialTonKho(Integer maSanPham, Integer tonTrenNgay) {
        StringBuilder sql = new StringBuilder("SELECT TOP 5000 sp.MaSKU, sp.TenSanPham, s.SoSerial, s.IMEI, s.IMEI2, s.NgayNhap, "
                + "DATEDIFF(DAY, s.NgayNhap, SYSDATETIME()) AS SoNgayTon, s.GiaVon FROM SerialSanPham s "
                + "JOIN SanPham sp ON sp.MaSanPham = s.MaSanPham WHERE s.TrangThai = N'trong_kho'");
        List<Object> a = new ArrayList<>();
        if (maSanPham != null) { sql.append(" AND s.MaSanPham = ?"); a.add(maSanPham); }
        if (tonTrenNgay != null) {
            if (tonTrenNgay < 1) throw BusinessException.badRequest("SO_NGAY_KHONG_HOP_LE", "Số ngày phải lớn hơn 0");
            sql.append(" AND s.NgayNhap < ?");
            a.add(Timestamp.valueOf(LocalDateTime.now().minusDays(tonTrenNgay)));
        }
        sql.append(" ORDER BY s.NgayNhap, sp.MaSKU");
        List<List<Object>> dong = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql.toString(), a.toArray())) {
            dong.add(row(r.get("MaSKU"), r.get("TenSanPham"), r.get("SoSerial"), r.get("IMEI"), r.get("IMEI2"),
                    hienThi(r.get("NgayNhap")), r.get("SoNgayTon"), r.get("GiaVon")));
        }
        String tieuDe = tonTrenNgay == null ? "Serial/IMEI đang trong kho" : "Hàng tồn lâu ngày (trên " + tonTrenNgay + " ngày)";
        return bang(tieuDe, dong.size() + " thiết bị" + (dong.size() >= 5000 ? " (hiển thị tối đa 5000)" : ""),
                List.of("SKU", "Sản phẩm", "Serial", "IMEI", "IMEI 2", "Ngày nhập", "Số ngày tồn", "Giá vốn"),
                dong);
    }

    private BaoCaoBang chenhLechTon() {
        List<List<Object>> dong = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT MaSKU, TenSanPham, TonHeThong, SoSerialTrongKho, ChenhLech "
                + "FROM vw_ChenhLechTonKho ORDER BY ABS(ChenhLech) DESC")) {
            dong.add(row(r.get("MaSKU"), r.get("TenSanPham"), r.get("TonHeThong"), r.get("SoSerialTrongKho"), r.get("ChenhLech")));
        }
        return bang("Lệch giữa tồn hệ thống và số Serial trong kho", dong.isEmpty() ? "Không có chênh lệch" : null,
                List.of("SKU", "Sản phẩm", "Tồn hệ thống", "Serial trong kho", "Chênh lệch"), dong);
    }

    // ===================== helpers =====================

    private static Khoang khoang(LocalDate tu, LocalDate den, int macDinh, int toiDa) {
        LocalDate d = den != null ? den : LocalDate.now();
        LocalDate t = tu != null ? tu : d.minusDays(macDinh - 1L);
        if (t.isAfter(d)) {
            throw BusinessException.badRequest("KHOANG_THOI_GIAN_SAI", "Từ ngày phải trước hoặc bằng đến ngày");
        }
        if (ChronoUnit.DAYS.between(t, d) + 1 > toiDa) {
            throw BusinessException.badRequest("KHOANG_THOI_GIAN_QUA_DAI", "Khoảng thời gian tối đa " + toiDa + " ngày cho cách nhóm này");
        }
        return new Khoang(t, d);
    }

    private static void napBan(Map<String, Acc> m, List<Map<String, Object>> rows) {
        for (Map<String, Object> r : rows) {
            String key = String.valueOf(r.get("Khoa"));
            Acc x = m.computeIfAbsent(key, z -> new Acc());
            x.ma = r.containsKey("Ma") ? String.valueOf(r.get("Ma")) : key;
            x.ten = r.containsKey("Ten") ? String.valueOf(r.get("Ten")) : "";
            x.soHd = l(r.get("SoHD"));
            x.tienHang = d(r.get("TienHang"));
            x.giam = d(r.get("GiamGia"));
            x.gop = d(r.get("DoanhThu"));
            x.thue = d(r.get("Thue"));
        }
    }

    private static void napHoan(Map<String, Acc> m, List<Map<String, Object>> rows) {
        for (Map<String, Object> r : rows) {
            String key = String.valueOf(r.get("Khoa"));
            Acc x = m.computeIfAbsent(key, z -> new Acc());
            if (x.ma.isEmpty()) {
                x.ma = r.containsKey("Ma") ? String.valueOf(r.get("Ma")) : key;
                x.ten = r.containsKey("Ten") ? String.valueOf(r.get("Ten")) : "";
            }
            x.hoan = d(r.get("Hoan"));
        }
    }

    private static Object hienThi(Object o) {
        return o instanceof Timestamp t ? t.toLocalDateTime().format(NGAY_GIO) : o;
    }

    private static List<Object> row(Object... v) { return new ArrayList<>(Arrays.asList(v)); }

    private static BigDecimal d(Object o) {
        return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static long l(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

    /** Dựng bảng; cotTong = chỉ số các cột số cần cộng dòng tổng (rỗng = không có dòng tổng). */
    private static BaoCaoBang bang(String tieuDe, String moTa, List<String> cot, List<List<Object>> dong, int... cotTong) {
        List<Object> tong = null;
        if (cotTong.length > 0 && !dong.isEmpty()) {
            tong = new ArrayList<>(Collections.<Object>nCopies(cot.size(), null));
            tong.set(0, "Tổng");
            for (int c : cotTong) {
                BigDecimal s = BigDecimal.ZERO;
                for (List<Object> r : dong) if (r.get(c) instanceof Number n) s = s.add(new BigDecimal(n.toString()));
                tong.set(c, s);
            }
        }
        return new BaoCaoBang(tieuDe, moTa, cot, dong, tong);
    }
}