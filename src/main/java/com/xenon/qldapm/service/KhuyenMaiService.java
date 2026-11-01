package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhuyenMaiDtos.*;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Quản lý chương trình khuyến mãi (CRUD, trạng thái, hết hạn). Phần TÍNH khuyến mãi nằm ở KhuyenMaiEngine.
 *
 * Quy tắc:
 *  - Mỗi loại chỉ mang đúng các cột giá trị của nó (khớp CHECK chk_km_hinhdang) và đúng loại dòng sản phẩm:
 *      giam_phan_tram / giam_tien : chỉ dòng 'dieu_kien' (bắt buộc nếu không áp dụng toàn bộ; cấm nếu toàn bộ).
 *      mua_kem_gia_soc            : >=1 'dieu_kien' + >=1 'uu_dai' là sản phẩm, GiaUuDai < giá niêm yết.
 *      tang_qua                   : 'dieu_kien' (hoặc toàn bộ) + 'uu_dai' là sản phẩm không theo Serial, tặng miễn phí.
 *      tang_voucher               : chỉ 'dieu_kien' (hoặc toàn bộ); khách hàng phải gắn vào hóa đơn mới được tặng.
 *  - Mã code: viết hoa, 3-50 ký tự [A-Z0-9_-], duy nhất, không bắt đầu bằng "VC-" (dành cho voucher).
 *  - Chương trình nhập mã tay (TuDongApDung = false) bắt buộc có mã.
 *  - Đã có lượt dùng: chỉ sửa được tên, mô tả, độ ưu tiên, ngày kết thúc, số lượng tối đa (>= đã dùng).
 *  - Trạng thái: hoat_dong <-> tam_dung; huy (không hoàn tác); het_han do hệ thống đặt, muốn chạy lại thì gia hạn
 *    ngày kết thúc (tự về hoat_dong). Hủy chương trình KHÔNG thu hồi voucher đã phát cho khách.
 */
@Service
@RequiredArgsConstructor
public class KhuyenMaiService {

    private static final Set<String> LOAI = Set.of("giam_phan_tram", "giam_tien", "mua_kem_gia_soc", "tang_qua", "tang_voucher");
    private static final Set<String> VAI_TRO = Set.of("dieu_kien", "uu_dai");
    private static final Set<String> TRANG_THAI_DOI = Set.of("hoat_dong", "tam_dung", "huy");

    private final KhuyenMaiRepository kmRepository;
    private final SanPhamRepository sanPhamRepository;
    private final DanhMucRepository danhMucRepository;
    private final NhanVienRepository nhanVienRepository;
    private final VoucherRepository voucherRepository;
    private final NhatKyService nhatKyService;

    /** Dữ liệu đã kiểm tra + chuẩn hóa từ request. */
    private record Chuan(String ten, String code, String loai, BigDecimal pct, BigDecimal max, BigDecimal codinh,
                         BigDecimal voucher, BigDecimal donToiThieu, boolean tuDong, boolean congDon, int uuTien,
                         boolean toanBo, Integer soLuongToiDa, LocalDateTime bd, LocalDateTime kt, String moTa,
                         List<KhuyenMaiSanPham> items) {}

    // ===================== truy vấn =====================

    @Transactional(readOnly = true)
    public PageResponse<KhuyenMaiResponse> danhSach(String trangThai, Pageable pageable) {
        Page<KhuyenMai> page = trangThai != null && !trangThai.isBlank()
                ? kmRepository.findByTrangThai(trangThai, pageable) : kmRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    /** Các chương trình TỰ ĐỘNG đang có hiệu lực (thu ngân xem để tư vấn khách). */
    @Transactional(readOnly = true)
    public List<KhuyenMaiResponse> dangApDung() {
        return kmRepository.findDangHoatDongTuDong(LocalDateTime.now()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public KhuyenMaiResponse chiTiet(Integer id) { return toResponse(lay(id)); }

    // ===================== tạo / sửa =====================

    @Transactional
    public KhuyenMaiResponse tao(KhuyenMaiRequest r, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        Chuan c = kiemTra(r, null, 0);
        if (!c.kt().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("NGAY_KET_THUC_QUA_KHU", "Ngày kết thúc phải sau thời điểm hiện tại");
        }
        KhuyenMai k = new KhuyenMai();
        k.setNguoiTao(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        gan(k, c);
        c.items().forEach(k::addKhuyenMaiSanPham);
        k = kmRepository.saveAndFlush(k);
        nhatKyService.ghi(u.getMaNhanVien(), "tao_khuyen_mai", "KhuyenMai", String.valueOf(k.getMaKhuyenMai()),
                null, tom(k), ip);
        return toResponse(k);
    }

    @Transactional
    public KhuyenMaiResponse capNhat(Integer id, KhuyenMaiRequest r, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        KhuyenMai k = layDeSua(id);
        if ("huy".equals(k.getTrangThai())) {
            throw BusinessException.conflict("KM_DA_HUY", "Chương trình đã hủy, không thể chỉnh sửa");
        }
        Chuan c = kiemTra(r, id, k.getSoLuongDaDung());
        if (k.getSoLuongDaDung() > 0 && !chuKy(k).equals(chuKy(c))) {
            throw BusinessException.conflict("KM_DA_SU_DUNG",
                    "Chương trình đã có lượt dùng: chỉ được sửa tên, mô tả, độ ưu tiên, ngày kết thúc và số lượng tối đa");
        }
        String cu = tom(k);
        boolean doiItems = !chuKyItems(k.getSanPhams()).equals(chuKyItems(c.items()));
        gan(k, c);
        if (doiItems) {
            k.getSanPhams().clear();
            kmRepository.flush();                       // xóa dòng cũ trước khi thêm (unique index theo từng dòng)
            c.items().forEach(k::addKhuyenMaiSanPham);
        }
        LocalDateTime now = LocalDateTime.now();
        if ("het_han".equals(k.getTrangThai()) && now.isBefore(k.getNgayKetThuc()) && conLuot(k)) {
            k.setTrangThai("hoat_dong");                // gia hạn => chạy lại
        }
        kmRepository.flush();
        nhatKyService.ghi(u.getMaNhanVien(), "sua_khuyen_mai", "KhuyenMai", String.valueOf(id), cu, tom(k), ip);
        return toResponse(k);
    }

    @Transactional
    public KhuyenMaiResponse doiTrangThai(Integer id, String trangThai, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        if ("het_han".equals(trangThai)) {
            throw BusinessException.badRequest("TRANG_THAI_HE_THONG", "Trạng thái 'het_han' do hệ thống tự đặt; hãy gia hạn ngày kết thúc để chạy lại");
        }
        if (!TRANG_THAI_DOI.contains(trangThai)) {
            throw BusinessException.badRequest("TRANG_THAI_KHONG_HOP_LE", "Trạng thái không hợp lệ: " + trangThai);
        }
        KhuyenMai k = layDeSua(id);
        String cu = k.getTrangThai();
        if ("huy".equals(cu)) throw BusinessException.conflict("KM_DA_HUY", "Chương trình đã hủy, không thể đổi trạng thái");
        if (cu.equals(trangThai)) throw BusinessException.badRequest("TRANG_THAI_KHONG_DOI", "Chương trình đã ở trạng thái này");
        if ("hoat_dong".equals(trangThai) && "het_han".equals(cu)
                && (!LocalDateTime.now().isBefore(k.getNgayKetThuc()) || !conLuot(k))) {
            throw BusinessException.conflict("KM_KHONG_CHAY_LAI_DUOC", "Chương trình đã hết hạn hoặc hết lượt, hãy gia hạn trước khi bật lại");
        }
        k.setTrangThai(trangThai);
        nhatKyService.ghi(u.getMaNhanVien(), "doi_trang_thai_khuyen_mai", "KhuyenMai", String.valueOf(id), cu, trangThai, ip);
        return toResponse(k);
    }

    /** Job định kỳ: đánh dấu chương trình / voucher quá hạn. Trả về {số CTKM, số voucher}. */
    @Transactional
    public int[] danhDauHetHan() {
        LocalDateTime now = LocalDateTime.now();
        return new int[]{kmRepository.danhDauHetHan(now), voucherRepository.danhDauHetHan(now)};
    }

    // ===================== kiểm tra & chuẩn hóa =====================

    private Chuan kiemTra(KhuyenMaiRequest r, Integer boQuaId, int daDung) {
        String loai = r.loaiKhuyenMai();
        yeuCau(LOAI.contains(loai), "Loại khuyến mãi không hợp lệ: " + loai);
        boolean tuDong = r.tuDongApDung() == null || r.tuDongApDung();
        boolean congDon = Boolean.TRUE.equals(r.choPhepCongDon());
        boolean toanBo = Boolean.TRUE.equals(r.apDungToanBo());
        int uuTien = r.doUuTien() == null ? 0 : r.doUuTien();
        BigDecimal don = r.giaTriDonToiThieu() == null ? BigDecimal.ZERO : r.giaTriDonToiThieu();

        String code = chuanHoaCode(r.maCode());
        yeuCau(tuDong || code != null, "Chương trình nhập mã tay phải có mã code");
        if (code != null) {
            kmRepository.findByMaCode(code).filter(x -> !x.getMaKhuyenMai().equals(boQuaId)).ifPresent(x -> {
                throw BusinessException.conflict("MA_CODE_TRUNG", "Mã code đã được dùng cho chương trình khác");
            });
        }
        yeuCau(r.ngayKetThuc().isAfter(r.ngayBatDau()), "Ngày kết thúc phải sau ngày bắt đầu");
        yeuCau(r.soLuongToiDa() == null || r.soLuongToiDa() >= daDung,
                "Số lượng tối đa không được nhỏ hơn số lượt đã dùng (" + daDung + ")");

        BigDecimal pct = r.phanTramGiam(), max = r.soTienGiamToiDa(), codinh = r.giamCodinh(), vc = r.giaTriVoucher();
        switch (loai) {
            case "giam_phan_tram" -> yeuCau(pct != null && codinh == null && vc == null,
                    "Giảm % chỉ nhập phần trăm giảm (và trần giảm nếu có)");
            case "giam_tien" -> yeuCau(codinh != null && pct == null && max == null && vc == null,
                    "Giảm tiền chỉ nhập số tiền giảm cố định");
            case "mua_kem_gia_soc", "tang_qua" -> yeuCau(pct == null && max == null && codinh == null && vc == null,
                    "Loại này không có giá trị giảm/voucher, giá nằm ở các dòng ưu đãi");
            default -> yeuCau(vc != null && pct == null && max == null && codinh == null,
                    "Tặng voucher chỉ nhập giá trị voucher");
        }

        List<KhuyenMaiSanPham> items = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int dk = 0, uu = 0;
        for (KhuyenMaiItemRequest i : r.items() == null ? List.<KhuyenMaiItemRequest>of() : r.items()) {
            yeuCau(VAI_TRO.contains(i.vaiTro()), "Vai trò dòng không hợp lệ: " + i.vaiTro());
            yeuCau((i.maSanPham() != null) ^ (i.maDanhMuc() != null), "Mỗi dòng phải chọn đúng 1 trong sản phẩm hoặc danh mục");
            KhuyenMaiSanPham it = new KhuyenMaiSanPham();
            it.setVaiTro(i.vaiTro());
            it.setSoLuong(i.soLuong() == null ? 1 : i.soLuong());
            String key;
            if (i.maSanPham() != null) {
                it.setSanPham(sanPhamRepository.findById(i.maSanPham()).filter(s -> s.getNgayXoa() == null)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm #" + i.maSanPham())));
                key = i.vaiTro() + ":sp" + i.maSanPham();
            } else {
                it.setDanhMuc(danhMucRepository.findById(i.maDanhMuc()).filter(d -> d.getNgayXoa() == null)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục #" + i.maDanhMuc())));
                key = i.vaiTro() + ":dm" + i.maDanhMuc();
            }
            yeuCau(keys.add(key), "Dòng bị trùng: " + key);
            if ("uu_dai".equals(i.vaiTro())) {
                uu++;
                yeuCau(it.getSanPham() != null, "Dòng ưu đãi phải là sản phẩm cụ thể");
                it.setGiaUuDai(i.giaUuDai());
            } else {
                dk++;
                yeuCau(i.giaUuDai() == null, "Dòng điều kiện không có giá ưu đãi");
            }
            items.add(it);
        }

        switch (loai) {
            case "giam_phan_tram", "giam_tien" -> {
                yeuCau(uu == 0, "Giảm giá không có dòng ưu đãi");
                if (toanBo) yeuCau(dk == 0, "Áp dụng toàn bộ thì không chọn sản phẩm/danh mục điều kiện");
                else yeuCau(dk > 0, "Cần chọn ít nhất 1 sản phẩm/danh mục được giảm (hoặc áp dụng toàn bộ)");
            }
            case "mua_kem_gia_soc" -> {
                yeuCau(dk > 0 && uu > 0, "Mua kèm giá sốc cần ít nhất 1 dòng điều kiện và 1 dòng ưu đãi");
                for (KhuyenMaiSanPham it : items) {
                    if ("uu_dai".equals(it.getVaiTro())) {
                        yeuCau(it.getGiaUuDai() != null && it.getGiaUuDai().compareTo(it.getSanPham().getGiaNiemYet()) < 0,
                                "Giá sốc của " + it.getSanPham().getMaSKU() + " phải nhỏ hơn giá niêm yết");
                    }
                }
            }
            case "tang_qua" -> {
                yeuCau(uu > 0, "Tặng quà cần ít nhất 1 sản phẩm quà tặng");
                yeuCau(dk > 0 || toanBo, "Cần điều kiện nhận quà (hoặc áp dụng toàn bộ)");
                for (KhuyenMaiSanPham it : items) {
                    if ("uu_dai".equals(it.getVaiTro())) {
                        SanPham sp = it.getSanPham();
                        yeuCau("dang_kinh_doanh".equals(sp.getTrangThai()) && !Boolean.TRUE.equals(sp.getCoTheoDoiSerial()),
                                "Quà tặng " + sp.getMaSKU() + " phải đang kinh doanh và không theo dõi Serial");
                        yeuCau(it.getGiaUuDai() == null || it.getGiaUuDai().signum() == 0, "Quà tặng là miễn phí");
                    }
                }
            }
            default -> {
                yeuCau(uu == 0, "Tặng voucher không có dòng ưu đãi");
                yeuCau(dk > 0 || toanBo, "Cần điều kiện nhận voucher (hoặc áp dụng toàn bộ)");
            }
        }
        return new Chuan(r.tenChuongTrinh().trim(), code, loai, pct, max, codinh, vc, don, tuDong, congDon, uuTien,
                toanBo, r.soLuongToiDa(), r.ngayBatDau(), r.ngayKetThuc(), blankToNull(r.moTa()), items);
    }

    private static String chuanHoaCode(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        if (s.isEmpty()) return null;
        if (!s.matches("[A-Z0-9_-]{3,50}")) {
            throw BusinessException.badRequest("MA_CODE_KHONG_HOP_LE", "Mã code gồm 3-50 ký tự chữ/số/gạch (A-Z, 0-9, _, -)");
        }
        if (s.startsWith("VC-")) {
            throw BusinessException.badRequest("MA_CODE_KHONG_HOP_LE", "Mã bắt đầu bằng 'VC-' dành riêng cho voucher");
        }
        return s;
    }

    private static void yeuCau(boolean ok, String message) {
        if (!ok) throw BusinessException.badRequest("KHUYEN_MAI_KHONG_HOP_LE", message);
    }

    // ===================== helpers =====================

    private static void gan(KhuyenMai k, Chuan c) {
        k.setTenChuongTrinh(c.ten());
        k.setMaCode(c.code());
        k.setLoaiKhuyenMai(c.loai());
        k.setPhanTramGiam(c.pct());
        k.setSoTienGiamToiDa(c.max());
        k.setGiamCodinh(c.codinh());
        k.setGiaTriVoucher(c.voucher());
        k.setGiaTriDonToiThieu(c.donToiThieu());
        k.setTuDongApDung(c.tuDong());
        k.setChoPhepCongDon(c.congDon());
        k.setDoUuTien(c.uuTien());
        k.setApDungToanBo(c.toanBo());
        k.setSoLuongToiDa(c.soLuongToiDa());
        k.setNgayBatDau(c.bd());
        k.setNgayKetThuc(c.kt());
        k.setMoTa(c.moTa());
    }

    private static boolean conLuot(KhuyenMai k) {
        return k.getSoLuongToiDa() == null || k.getSoLuongDaDung() < k.getSoLuongToiDa();
    }

    private KhuyenMai lay(Integer id) {
        return kmRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy chương trình khuyến mãi"));
    }

    private KhuyenMai layDeSua(Integer id) {
        return kmRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy chương trình khuyến mãi"));
    }

    // chữ ký phần "lõi" (không gồm tên, mô tả, độ ưu tiên, ngày kết thúc, số lượng tối đa)
    private static String chuKy(KhuyenMai k) {
        return chuKy(k.getLoaiKhuyenMai(), k.getMaCode(), k.getPhanTramGiam(), k.getSoTienGiamToiDa(), k.getGiamCodinh(),
                k.getGiaTriVoucher(), k.getGiaTriDonToiThieu(), k.getTuDongApDung(), k.getChoPhepCongDon(),
                k.getApDungToanBo(), k.getNgayBatDau(), k.getSanPhams());
    }

    private static String chuKy(Chuan c) {
        return chuKy(c.loai(), c.code(), c.pct(), c.max(), c.codinh(), c.voucher(), c.donToiThieu(), c.tuDong(),
                c.congDon(), c.toanBo(), c.bd(), c.items());
    }

    private static String chuKy(String loai, String code, BigDecimal pct, BigDecimal max, BigDecimal codinh, BigDecimal vc,
                                BigDecimal don, boolean tuDong, boolean congDon, boolean toanBo, LocalDateTime bd,
                                List<KhuyenMaiSanPham> items) {
        return String.join("|", loai, code == null ? "" : code.toUpperCase(), s(pct), s(max), s(codinh), s(vc), s(don),
                String.valueOf(tuDong), String.valueOf(congDon), String.valueOf(toanBo), String.valueOf(bd), chuKyItems(items));
    }

    private static String chuKyItems(List<KhuyenMaiSanPham> items) {
        return items.stream().map(i -> i.getVaiTro() + ":"
                        + (i.getSanPham() != null ? "sp" + i.getSanPham().getMaSanPham() : "dm" + i.getDanhMuc().getMaDanhMuc())
                        + ":" + i.getSoLuong() + ":" + s(i.getGiaUuDai()))
                .sorted().collect(Collectors.joining(","));
    }

    private static String s(BigDecimal b) { return b == null ? "" : b.stripTrailingZeros().toPlainString(); }

    private static String tom(KhuyenMai k) {
        return k.getTenChuongTrinh() + "; " + k.getLoaiKhuyenMai() + "; %=" + s(k.getPhanTramGiam())
                + "; tien=" + s(k.getGiamCodinh()) + "; voucher=" + s(k.getGiaTriVoucher())
                + "; " + k.getNgayBatDau() + " -> " + k.getNgayKetThuc() + "; toiDa=" + k.getSoLuongToiDa();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private KhuyenMaiResponse toResponse(KhuyenMai k) {
        List<KhuyenMaiItemResponse> items = k.getSanPhams().stream().map(i -> new KhuyenMaiItemResponse(
                i.getVaiTro(),
                i.getSanPham() == null ? null : i.getSanPham().getMaSanPham(),
                i.getSanPham() == null ? null : i.getSanPham().getMaSKU(),
                i.getSanPham() == null ? null : i.getSanPham().getTenSanPham(),
                i.getDanhMuc() == null ? null : i.getDanhMuc().getMaDanhMuc(),
                i.getDanhMuc() == null ? null : i.getDanhMuc().getTenDanhMuc(),
                i.getSoLuong(), i.getGiaUuDai())).toList();
        return new KhuyenMaiResponse(k.getMaKhuyenMai(), k.getTenChuongTrinh(), k.getMaCode(), k.getLoaiKhuyenMai(),
                k.getPhanTramGiam(), k.getSoTienGiamToiDa(), k.getGiamCodinh(), k.getGiaTriVoucher(),
                k.getGiaTriDonToiThieu(), k.getTuDongApDung(), k.getChoPhepCongDon(), k.getDoUuTien(),
                k.getApDungToanBo(), k.getSoLuongToiDa(), k.getSoLuongDaDung(), k.getNgayBatDau(), k.getNgayKetThuc(),
                k.getTrangThai(), k.getMoTa(), items);
    }
}