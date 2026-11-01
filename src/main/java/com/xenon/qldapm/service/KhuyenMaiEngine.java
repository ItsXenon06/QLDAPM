package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhuyenMaiDtos.KmApDungResponse;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Động cơ khuyến mãi cho hóa đơn nháp. Quy ước lưu vết trong HoaDonKhuyenMai (HDKM):
 *  - dòng "neo" (MaChiTiet = NULL, SoTienGiam = 0): CTKM/voucher đang áp dụng trên hóa đơn;
 *  - dòng theo từng dòng hàng (MaChiTiet != NULL): số tiền giảm phân bổ cho dòng đó.
 * => GiamGia của dòng = giảm tay + tổng HDKM của dòng. Chỉ phần giảm tay bị chặn bởi giá sàn.
 *
 * Quy tắc: CTKM xét theo DoUuTien giảm dần; mỗi CTKM áp tối đa 1 lần/hóa đơn; một dòng chỉ nhận thêm CTKM
 * thứ hai khi cả hai cho phép cộng dồn; giảm tiền/voucher phân bổ theo tỷ trọng giá trị dòng;
 * quà tặng là dòng giá 0 (vẫn trừ tồn, chỉ hàng không theo Serial); voucher cần gắn khách hàng.
 */
@Service
@RequiredArgsConstructor
public class KhuyenMaiEngine {

    private static final BigDecimal TRAM = BigDecimal.valueOf(100);
    private static final int NGAY_HIEU_LUC_VOUCHER = 30;

    private final KhuyenMaiRepository kmRepo;
    private final KhuyenMaiSanPhamRepository kmspRepo;
    private final HoaDonKhuyenMaiRepository hdkmRepo;
    private final VoucherRepository voucherRepo;
    private final TonKhoRepository tonKhoRepo;
    private final EntityManager em;

    private record Snap(List<HoaDonKhuyenMai> rows, Map<ChiTietHoaDon, BigDecimal> tay,
                        Set<Integer> kmTay, Set<Integer> vouchers) {}

    public record Ket(Set<Integer> km, Set<Integer> vouchers) {}

    // ===================== API cho PosService =====================

    @Transactional(propagation = Propagation.MANDATORY)
    public Ket apDungLai(HoaDon h) {
        return tinh(h, chup(h, null, null));
    }

    /** Dùng khi vừa đổi số lượng/giảm tay của 1 dòng: giảm tay mới của dòng đó được truyền vào, không suy ra từ DB. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Ket apDungLai(HoaDon h, ChiTietHoaDon dong, BigDecimal tayMoi) {
        return tinh(h, chup(h, dong, tayMoi));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void themMa(HoaDon h, String ma) {
        String code = ma.trim();
        LocalDateTime now = LocalDateTime.now();
        Snap s = chup(h, null, null);
        Optional<Voucher> ov = voucherRepo.findByMaCode(code.toUpperCase());
        if (ov.isPresent()) {
            Voucher v = ov.get();
            if (!voucherHopLe(v, h, now)) {
                throw BusinessException.conflict("VOUCHER_KHONG_HOP_LE",
                        "Voucher không hợp lệ: đã dùng, hết hạn, thuộc khách hàng khác hoặc phát hành từ chính hóa đơn này");
            }
            if (!s.vouchers().add(v.getMaVoucher())) {
                throw BusinessException.conflict("MA_DA_AP_DUNG", "Voucher đã được áp dụng trên hóa đơn");
            }
            Ket kq = tinh(h, s);
            if (!kq.vouchers().contains(v.getMaVoucher())) {
                throw BusinessException.conflict("VOUCHER_KHONG_AP_DUNG", "Hóa đơn chưa có giá trị để áp dụng voucher");
            }
            return;
        }
        KhuyenMai k = kmRepo.findByMaCode(code).orElseThrow(() -> new NotFoundException("Mã giảm giá không tồn tại"));
        if (Boolean.TRUE.equals(k.getTuDongApDung())) {
            throw BusinessException.badRequest("KM_TU_DONG", "Chương trình này tự động áp dụng, không cần nhập mã");
        }
        if (!hieuLuc(k, now)) {
            throw BusinessException.conflict("KM_KHONG_HIEU_LUC", "Mã giảm giá đã hết hạn, hết lượt hoặc đang tạm dừng");
        }
        if (!s.kmTay().add(k.getMaKhuyenMai())) {
            throw BusinessException.conflict("MA_DA_AP_DUNG", "Mã giảm giá đã được áp dụng trên hóa đơn");
        }
        Ket kq = tinh(h, s);
        if (!kq.km().contains(k.getMaKhuyenMai())) {
            throw BusinessException.conflict("CHUA_DU_DIEU_KIEN", "Hóa đơn chưa đủ điều kiện áp dụng mã này");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void goMa(HoaDon h, String ma) {
        String code = ma.trim();
        Snap s = chup(h, null, null);
        boolean thay = false;
        Optional<Voucher> ov = voucherRepo.findByMaCode(code.toUpperCase());
        if (ov.isPresent() && s.vouchers().remove(ov.get().getMaVoucher())) {
            thay = true;
        } else {
            Optional<KhuyenMai> ok = kmRepo.findByMaCode(code);
            if (ok.isPresent() && s.kmTay().remove(ok.get().getMaKhuyenMai())) thay = true;
        }
        if (!thay) throw new NotFoundException("Mã này chưa được áp dụng trên hóa đơn");
        tinh(h, s);
    }

    /** Gọi TRƯỚC khi xóa 1 dòng khỏi hóa đơn (HDKM tham chiếu dòng đó). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void goDong(ChiTietHoaDon c) {
        if (c.getMaChiTiet() == null) return;
        hdkmRepo.deleteAll(hdkmRepo.findByChiTiet_MaChiTiet(c.getMaChiTiet()));
        hdkmRepo.flush();
    }

    /** Gọi TRƯỚC khi xóa hẳn hóa đơn nháp. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void xoaTatCa(HoaDon h) {
        hdkmRepo.deleteAll(hdkmRepo.findByHoaDon_MaHoaDon(h.getMaHoaDon()));
        hdkmRepo.flush();
    }

    public BigDecimal tongKhuyenMaiDong(ChiTietHoaDon c) {
        return c.getMaChiTiet() == null ? BigDecimal.ZERO : hdkmRepo.tongGiamCuaDong(c.getMaChiTiet());
    }

    /** Chốt khi thanh toán: khóa + tăng lượt dùng, phát hành voucher, đánh dấu voucher đã dùng. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void chot(HoaDon h) {
        LocalDateTime now = LocalDateTime.now();
        Set<Integer> kmIds = new TreeSet<>();
        Set<Integer> vcIds = new TreeSet<>();
        for (HoaDonKhuyenMai r : hdkmRepo.findByHoaDon_MaHoaDon(h.getMaHoaDon())) {
            if (r.getChiTiet() != null) continue;
            if (r.getVoucher() != null) vcIds.add(r.getVoucher().getMaVoucher());
            else kmIds.add(r.getKhuyenMai().getMaKhuyenMai());
        }
        for (Integer id : kmIds) {
            KhuyenMai k = kmRepo.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy khuyến mãi"));
            em.refresh(k, LockModeType.PESSIMISTIC_WRITE);       // đọc lại số lượt dùng mới nhất dưới khóa
            if (!hieuLuc(k, now)) {
                throw BusinessException.conflict("KM_HET_HIEU_LUC",
                        "Chương trình '" + k.getTenChuongTrinh() + "' không còn hiệu lực, vui lòng kiểm tra lại hóa đơn");
            }
            k.setSoLuongDaDung(k.getSoLuongDaDung() + 1);
            if ("tang_voucher".equals(k.getLoaiKhuyenMai())) phatHanhVoucher(h, k, now);
        }
        for (Integer id : vcIds) {
            Voucher v = voucherRepo.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy voucher"));
            em.refresh(v, LockModeType.PESSIMISTIC_WRITE);
            if (!voucherHopLe(v, h, now)) {
                throw BusinessException.conflict("VOUCHER_KHONG_HOP_LE", "Voucher " + v.getMaCode() + " không còn hợp lệ");
            }
            v.setTrangThai("da_dung");
            v.setHoaDonSuDung(h);
            v.setNgaySuDung(now);
        }
    }

    /** Hủy hóa đơn đã thanh toán: trả lượt dùng, mở lại voucher đã dùng, hủy voucher đã phát hành. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void hoanTac(HoaDon h) {
        LocalDateTime now = LocalDateTime.now();
        for (Voucher v : voucherRepo.findByHoaDonPhatHanh_MaHoaDon(h.getMaHoaDon())) {
            if ("da_dung".equals(v.getTrangThai())) {
                throw BusinessException.conflict("VOUCHER_DA_DUNG",
                        "Voucher " + v.getMaCode() + " phát hành từ hóa đơn này đã được sử dụng, không thể hủy hóa đơn");
            }
            if ("chua_dung".equals(v.getTrangThai())) v.setTrangThai("huy");
        }
        Set<Integer> kmIds = new TreeSet<>();
        Set<Integer> vcIds = new TreeSet<>();
        for (HoaDonKhuyenMai r : hdkmRepo.findByHoaDon_MaHoaDon(h.getMaHoaDon())) {
            if (r.getChiTiet() != null) continue;
            if (r.getVoucher() != null) vcIds.add(r.getVoucher().getMaVoucher());
            else kmIds.add(r.getKhuyenMai().getMaKhuyenMai());
        }
        for (Integer id : kmIds) {
            kmRepo.findById(id).ifPresent(k -> {
                em.refresh(k, LockModeType.PESSIMISTIC_WRITE);
                if (k.getSoLuongDaDung() > 0) k.setSoLuongDaDung(k.getSoLuongDaDung() - 1);
            });
        }
        for (Integer id : vcIds) {
            voucherRepo.findById(id).ifPresent(v -> {
                if ("da_dung".equals(v.getTrangThai()) && v.getHoaDonSuDung() != null
                        && v.getHoaDonSuDung().getMaHoaDon().equals(h.getMaHoaDon())) {
                    v.setHoaDonSuDung(null);
                    v.setNgaySuDung(null);
                    v.setTrangThai(v.getHanSuDung().isAfter(now) ? "chua_dung" : "het_han");
                }
            });
        }
    }

    public List<KmApDungResponse> tomTat(Integer maHoaDon) {
        List<HoaDonKhuyenMai> rows = hdkmRepo.findByHoaDon_MaHoaDon(maHoaDon);
        Map<String, BigDecimal> tong = new HashMap<>();
        for (HoaDonKhuyenMai r : rows) {
            if (r.getChiTiet() != null) tong.merge(khoa(r), r.getSoTienGiam(), BigDecimal::add);
        }
        List<KmApDungResponse> kq = new ArrayList<>();
        for (HoaDonKhuyenMai r : rows) {
            if (r.getChiTiet() != null) continue;
            KhuyenMai k = r.getKhuyenMai();
            Voucher v = r.getVoucher();
            kq.add(new KmApDungResponse(k.getTenChuongTrinh(), v != null ? v.getMaCode() : k.getMaCode(),
                    v != null ? "voucher" : k.getLoaiKhuyenMai(), tong.getOrDefault(khoa(r), BigDecimal.ZERO)));
        }
        return kq;
    }

    // ===================== lõi tính toán =====================

    private Snap chup(HoaDon h, ChiTietHoaDon ghiDe, BigDecimal tayMoi) {
        List<HoaDonKhuyenMai> rows = hdkmRepo.findByHoaDon_MaHoaDon(h.getMaHoaDon());
        Map<Integer, BigDecimal> kmTheoDong = new HashMap<>();
        Set<Integer> kmTay = new LinkedHashSet<>();
        Set<Integer> vouchers = new LinkedHashSet<>();
        for (HoaDonKhuyenMai r : rows) {
            if (r.getChiTiet() != null) {
                kmTheoDong.merge(r.getChiTiet().getMaChiTiet(), r.getSoTienGiam(), BigDecimal::add);
            } else if (r.getVoucher() != null) {
                vouchers.add(r.getVoucher().getMaVoucher());
            } else if (!Boolean.TRUE.equals(r.getKhuyenMai().getTuDongApDung())) {
                kmTay.add(r.getKhuyenMai().getMaKhuyenMai());
            }
        }
        Map<ChiTietHoaDon, BigDecimal> tay = new HashMap<>();
        for (ChiTietHoaDon c : h.getChiTiets()) {
            if (!Boolean.TRUE.equals(c.getHieuLuc()) || Boolean.TRUE.equals(c.getLaQuaTang())) continue;
            if (c == ghiDe) {
                tay.put(c, tayMoi);
            } else {
                BigDecimal km = c.getMaChiTiet() == null ? BigDecimal.ZERO
                        : kmTheoDong.getOrDefault(c.getMaChiTiet(), BigDecimal.ZERO);
                tay.put(c, c.getGiamGia().subtract(km).max(BigDecimal.ZERO));
            }
        }
        return new Snap(rows, tay, kmTay, vouchers);
    }

    private Ket tinh(HoaDon h, Snap s) {
        hdkmRepo.deleteAll(s.rows());
        hdkmRepo.flush();
        if (h.getChiTiets().removeIf(c -> Boolean.TRUE.equals(c.getLaQuaTang()))) hdkmRepo.flush();

        List<ChiTietHoaDon> dong = h.getChiTiets().stream()
                .filter(c -> Boolean.TRUE.equals(c.getHieuLuc()) && !Boolean.TRUE.equals(c.getLaQuaTang())).toList();
        Map<ChiTietHoaDon, BigDecimal> base = new HashMap<>();
        BigDecimal tongGross = BigDecimal.ZERO;
        for (ChiTietHoaDon c : dong) {
            BigDecimal gross = gross(c);
            BigDecimal tay = s.tay().getOrDefault(c, BigDecimal.ZERO).min(gross);
            c.setGiamGia(tay);
            base.put(c, gross.subtract(tay));
            tongGross = tongGross.add(gross);
        }
        Set<Integer> kmApDung = new LinkedHashSet<>();
        Set<Integer> vcApDung = new LinkedHashSet<>();
        if (dong.isEmpty()) return new Ket(kmApDung, vcApDung);

        LocalDateTime now = LocalDateTime.now();
        List<KhuyenMai> ds = new ArrayList<>(kmRepo.findDangHoatDongTuDong(now));
        for (Integer id : s.kmTay()) kmRepo.findById(id).filter(k -> hieuLuc(k, now)).ifPresent(ds::add);
        ds.sort(Comparator.comparing(KhuyenMai::getDoUuTien).reversed().thenComparing(KhuyenMai::getMaKhuyenMai));

        Set<ChiTietHoaDon> daCo = new HashSet<>();
        Set<ChiTietHoaDon> khongCongDon = new HashSet<>();
        Map<Integer, Integer> quaDuKien = new HashMap<>();

        for (KhuyenMai k : ds) {
            if (tongGross.compareTo(k.getGiaTriDonToiThieu()) < 0) continue;
            List<KhuyenMaiSanPham> items = kmspRepo.findByKhuyenMai_MaKhuyenMai(k.getMaKhuyenMai());
            Map<ChiTietHoaDon, BigDecimal> giam = new LinkedHashMap<>();
            List<ChiTietHoaDon> quaMoi = new ArrayList<>();
            boolean phatVoucher = false;

            switch (k.getLoaiKhuyenMai()) {
                case "giam_phan_tram", "giam_tien" -> {
                    List<ChiTietHoaDon> t = phamVi(k, items, dong).stream()
                            .filter(c -> choPhep(c, k, daCo, khongCongDon) && base.get(c).signum() > 0).toList();
                    if (!t.isEmpty()) {
                        if ("giam_tien".equals(k.getLoaiKhuyenMai())) {
                            giam.putAll(chia(t, base, k.getGiamCodinh()));
                        } else {
                            giam.putAll(theoPhanTram(t, base, k));
                        }
                    }
                }
                case "mua_kem_gia_soc" -> {
                    if (duDieuKien(k, items, dong)) giam.putAll(giaSoc(items, dong, base, k, daCo, khongCongDon));
                }
                case "tang_qua" -> {
                    if (duDieuKien(k, items, dong)) quaMoi.addAll(themQua(items, dong, quaDuKien));
                }
                case "tang_voucher" -> phatVoucher = duDieuKien(k, items, dong)
                        && h.getKhachHang() != null && k.getGiaTriVoucher() != null;
                default -> { }
            }

            if (giam.isEmpty() && quaMoi.isEmpty() && !phatVoucher) continue;
            for (Map.Entry<ChiTietHoaDon, BigDecimal> e : giam.entrySet()) {
                ChiTietHoaDon c = e.getKey();
                c.setGiamGia(c.getGiamGia().add(e.getValue()));
                base.merge(c, e.getValue().negate(), BigDecimal::add);
                daCo.add(c);
                if (!Boolean.TRUE.equals(k.getChoPhepCongDon())) khongCongDon.add(c);
                luu(h, c, k, null, e.getValue());
            }
            quaMoi.forEach(h::addChiTietHoaDon);
            luu(h, null, k, null, BigDecimal.ZERO);
            kmApDung.add(k.getMaKhuyenMai());
        }

        // voucher: trừ sau cùng, phân bổ theo giá trị còn lại của từng dòng
        for (Integer id : s.vouchers()) {
            Voucher v = voucherRepo.findById(id).filter(x -> voucherHopLe(x, h, now)).orElse(null);
            if (v == null) continue;
            List<ChiTietHoaDon> t = dong.stream().filter(c -> base.get(c).signum() > 0).toList();
            if (t.isEmpty()) continue;
            Map<ChiTietHoaDon, BigDecimal> chia = chia(t, base, v.getGiaTri());
            if (chia.isEmpty()) continue;
            for (Map.Entry<ChiTietHoaDon, BigDecimal> e : chia.entrySet()) {
                ChiTietHoaDon c = e.getKey();
                c.setGiamGia(c.getGiamGia().add(e.getValue()));
                base.merge(c, e.getValue().negate(), BigDecimal::add);
                luu(h, c, v.getKhuyenMai(), v, e.getValue());
            }
            luu(h, null, v.getKhuyenMai(), v, BigDecimal.ZERO);
            vcApDung.add(id);
        }
        hdkmRepo.flush();
        return new Ket(kmApDung, vcApDung);
    }

    // ===================== các loại khuyến mãi =====================

    private static List<ChiTietHoaDon> phamVi(KhuyenMai k, List<KhuyenMaiSanPham> items, List<ChiTietHoaDon> dong) {
        if (Boolean.TRUE.equals(k.getApDungToanBo())) return dong;
        List<KhuyenMaiSanPham> dk = items.stream().filter(i -> "dieu_kien".equals(i.getVaiTro())).toList();
        return dong.stream().filter(c -> dk.stream().anyMatch(i -> khop(c, i))).toList();
    }

    /** Khuyến mãi mua kèm/tặng quà/voucher: mọi dòng điều kiện phải đủ số lượng (AND). */
    private static boolean duDieuKien(KhuyenMai k, List<KhuyenMaiSanPham> items, List<ChiTietHoaDon> dong) {
        List<KhuyenMaiSanPham> dk = items.stream().filter(i -> "dieu_kien".equals(i.getVaiTro())).toList();
        if (dk.isEmpty()) return Boolean.TRUE.equals(k.getApDungToanBo());
        for (KhuyenMaiSanPham r : dk) {
            int q = dong.stream().filter(c -> khop(c, r)).mapToInt(ChiTietHoaDon::getSoLuong).sum();
            if (q < r.getSoLuong()) return false;
        }
        return true;
    }

    private static Map<ChiTietHoaDon, BigDecimal> theoPhanTram(List<ChiTietHoaDon> t,
                                                               Map<ChiTietHoaDon, BigDecimal> base, KhuyenMai k) {
        Map<ChiTietHoaDon, BigDecimal> kq = new LinkedHashMap<>();
        BigDecimal tong = BigDecimal.ZERO;
        for (ChiTietHoaDon c : t) {
            BigDecimal d = base.get(c).multiply(k.getPhanTramGiam()).divide(TRAM, 2, RoundingMode.HALF_UP);
            kq.put(c, d);
            tong = tong.add(d);
        }
        if (k.getSoTienGiamToiDa() != null && tong.compareTo(k.getSoTienGiamToiDa()) > 0) {
            return chia(t, base, k.getSoTienGiamToiDa());       // vượt trần: chia lại tổng = trần
        }
        return kq;
    }

    private static Map<ChiTietHoaDon, BigDecimal> giaSoc(List<KhuyenMaiSanPham> items, List<ChiTietHoaDon> dong,
                                                         Map<ChiTietHoaDon, BigDecimal> base, KhuyenMai k, Set<ChiTietHoaDon> daCo, Set<ChiTietHoaDon> khongCongDon) {
        Map<ChiTietHoaDon, BigDecimal> kq = new LinkedHashMap<>();
        for (KhuyenMaiSanPham i : items) {
            if (!"uu_dai".equals(i.getVaiTro()) || i.getSanPham() == null || i.getGiaUuDai() == null) continue;
            for (ChiTietHoaDon c : dong) {
                if (!c.getSanPham().getMaSanPham().equals(i.getSanPham().getMaSanPham())) continue;
                if (!choPhep(c, k, daCo, khongCongDon) || kq.containsKey(c)) continue;
                BigDecimal moiDon = c.getDonGia().subtract(i.getGiaUuDai());
                if (moiDon.signum() <= 0) continue;
                int q = Math.min(i.getSoLuong(), c.getSoLuong());
                BigDecimal d = moiDon.multiply(BigDecimal.valueOf(q)).min(base.get(c));
                if (d.signum() > 0) kq.put(c, d);
                break;
            }
        }
        return kq;
    }

    private List<ChiTietHoaDon> themQua(List<KhuyenMaiSanPham> items, List<ChiTietHoaDon> dong,
                                        Map<Integer, Integer> quaDuKien) {
        List<ChiTietHoaDon> kq = new ArrayList<>();
        for (KhuyenMaiSanPham i : items) {
            if (!"uu_dai".equals(i.getVaiTro()) || i.getSanPham() == null) continue;
            SanPham sp = i.getSanPham();
            if (sp.getNgayXoa() != null || !"dang_kinh_doanh".equals(sp.getTrangThai())
                    || Boolean.TRUE.equals(sp.getCoTheoDoiSerial())) continue;
            int trongGio = dong.stream().filter(c -> c.getSanPham().getMaSanPham().equals(sp.getMaSanPham()))
                    .mapToInt(ChiTietHoaDon::getSoLuong).sum() + quaDuKien.getOrDefault(sp.getMaSanPham(), 0);
            int ton = tonKhoRepo.findBySanPham_MaSanPham(sp.getMaSanPham()).map(TonKho::getSlTon).orElse(0);
            if (ton < trongGio + i.getSoLuong()) continue;       // hết quà thì bỏ qua, không chặn bán
            quaDuKien.merge(sp.getMaSanPham(), i.getSoLuong(), Integer::sum);
            ChiTietHoaDon g = new ChiTietHoaDon();
            g.setSanPham(sp);
            g.setTenHang(sp.getTenSanPham());
            g.setMaSKU(sp.getMaSKU());
            g.setSoLuong(i.getSoLuong());
            g.setDonGia(BigDecimal.ZERO);
            g.setThueSuat(sp.getThueSuatVAT());
            g.setLaQuaTang(true);
            kq.add(g);
        }
        return kq;
    }

    // ===================== helpers =====================

    /** Chia tổng tiền theo tỷ trọng giá trị còn lại của từng dòng; dòng cuối nhận phần dư. */
    private static Map<ChiTietHoaDon, BigDecimal> chia(List<ChiTietHoaDon> t, Map<ChiTietHoaDon, BigDecimal> base,
                                                       BigDecimal tong) {
        Map<ChiTietHoaDon, BigDecimal> kq = new LinkedHashMap<>();
        BigDecimal sum = t.stream().map(base::get).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() <= 0 || tong == null || tong.signum() <= 0) return kq;
        BigDecimal can = tong.min(sum);
        BigDecimal con = can;
        for (int i = 0; i < t.size(); i++) {
            ChiTietHoaDon c = t.get(i);
            BigDecimal b = base.get(c);
            BigDecimal d = (i == t.size() - 1) ? con
                    : can.multiply(b).divide(sum, 2, RoundingMode.HALF_UP);
            d = d.min(b).min(con);
            if (d.signum() > 0) {
                kq.put(c, d);
                con = con.subtract(d);
            }
        }
        return kq;
    }

    private static boolean choPhep(ChiTietHoaDon c, KhuyenMai k, Set<ChiTietHoaDon> daCo, Set<ChiTietHoaDon> khongCongDon) {
        return !daCo.contains(c) || (Boolean.TRUE.equals(k.getChoPhepCongDon()) && !khongCongDon.contains(c));
    }

    private static boolean khop(ChiTietHoaDon c, KhuyenMaiSanPham r) {
        SanPham sp = c.getSanPham();
        if (r.getSanPham() != null) return r.getSanPham().getMaSanPham().equals(sp.getMaSanPham());
        if (r.getDanhMuc() == null) return false;
        Integer dm = r.getDanhMuc().getMaDanhMuc();
        for (DanhMuc d = sp.getDanhMuc(); d != null; d = d.getDanhMucCha()) {    // gồm cả ngành con
            if (d.getMaDanhMuc().equals(dm)) return true;
        }
        return false;
    }

    private static BigDecimal gross(ChiTietHoaDon c) {
        return c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong()));
    }

    private static boolean hieuLuc(KhuyenMai k, LocalDateTime now) {
        return "hoat_dong".equals(k.getTrangThai()) && !now.isBefore(k.getNgayBatDau()) && now.isBefore(k.getNgayKetThuc())
                && (k.getSoLuongToiDa() == null || k.getSoLuongDaDung() < k.getSoLuongToiDa());
    }

    private static boolean voucherHopLe(Voucher v, HoaDon h, LocalDateTime now) {
        if (!"chua_dung".equals(v.getTrangThai()) || !v.getHanSuDung().isAfter(now)) return false;
        if (v.getHoaDonPhatHanh() != null && v.getHoaDonPhatHanh().getMaHoaDon().equals(h.getMaHoaDon())) return false;
        if (v.getKhachHang() == null) return true;
        return h.getKhachHang() != null && v.getKhachHang().getMaKhachHang().equals(h.getKhachHang().getMaKhachHang());
    }

    private void luu(HoaDon h, ChiTietHoaDon c, KhuyenMai k, Voucher v, BigDecimal soTien) {
        HoaDonKhuyenMai r = new HoaDonKhuyenMai();
        r.setHoaDon(h);
        r.setChiTiet(c);
        r.setKhuyenMai(k);
        r.setVoucher(v);
        r.setSoTienGiam(soTien);
        hdkmRepo.save(r);
    }

    private void phatHanhVoucher(HoaDon h, KhuyenMai k, LocalDateTime now) {
        Voucher v = new Voucher();
        v.setMaCode("VC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        v.setKhuyenMai(k);
        v.setGiaTri(k.getGiaTriVoucher());
        v.setKhachHang(h.getKhachHang());
        v.setHoaDonPhatHanh(h);
        v.setNgayPhatHanh(now);
        v.setHanSuDung(now.plusDays(NGAY_HIEU_LUC_VOUCHER));
        voucherRepo.save(v);
    }

    private static String khoa(HoaDonKhuyenMai r) {
        return r.getKhuyenMai().getMaKhuyenMai() + ":" + (r.getVoucher() == null ? 0 : r.getVoucher().getMaVoucher());
    }
}