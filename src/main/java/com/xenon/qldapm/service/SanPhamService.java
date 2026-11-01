package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.*;
import com.xenon.qldapm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Quy tắc: SKU/mã vạch duy nhất (trong hàng chưa xóa); giá sàn <= giá niêm yết; đổi giá chỉ qua doiGia()
 * (cần GIA_QUAN_LY) và luôn ghi LichSuGiaBan + nhật ký; không xóa sản phẩm còn tồn; không đổi
 * "theo dõi serial" khi còn tồn.
 */
@Service
@RequiredArgsConstructor
public class SanPhamService {

    private static final Set<String> TRANG_THAI = Set.of("dang_kinh_doanh", "ngung_kinh_doanh");

    private final SanPhamRepository sanPhamRepository;
    private final DanhMucRepository danhMucRepository;
    private final ThuongHieuRepository thuongHieuRepository;
    private final TonKhoRepository tonKhoRepository;
    private final LichSuGiaBanRepository lichSuGiaRepository;
    private final NhanVienRepository nhanVienRepository;
    private final NhatKyService nhatKyService;

    @Transactional(readOnly = true)
    public PageResponse<SanPhamResponse> tim(String keyword, Integer maDanhMuc, Integer maThuongHieu, Pageable pageable) {
        Page<SanPham> page;
        if (keyword != null && !keyword.isBlank()) {
            page = sanPhamRepository.search(keyword.trim(), pageable);
        } else if (maDanhMuc != null) {
            page = sanPhamRepository.findByDanhMuc_MaDanhMucInAndNgayXoaIsNull(layDanhMucVaCon(maDanhMuc), pageable);
        } else if (maThuongHieu != null) {
            page = sanPhamRepository.findByThuongHieu_MaThuongHieuAndNgayXoaIsNull(maThuongHieu, pageable);
        } else {
            page = sanPhamRepository.findByNgayXoaIsNull(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SanPhamResponse chiTiet(Integer id) {
        return toResponse(layChuaXoa(id));
    }

    /** POS: quét/nhập SKU hoặc mã vạch. */
    @Transactional(readOnly = true)
    public SanPhamResponse traCuuMa(String ma) {
        String code = ma.trim();
        SanPham sp = sanPhamRepository.findByMaSKUAndNgayXoaIsNull(code)
                .or(() -> sanPhamRepository.findByMaVachAndNgayXoaIsNull(code))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm với mã: " + ma));
        return toResponse(sp);
    }

    @Transactional
    public SanPhamResponse tao(SanPhamRequest r) {
        validateGia(r.giaNiemYet(), r.giaToiThieu());
        String sku = r.maSKU().trim();
        String vach = blankToNull(r.maVach());
        if (sanPhamRepository.existsByMaSKUAndNgayXoaIsNull(sku)) {
            throw BusinessException.conflict("SKU_TRUNG", "Mã SKU đã tồn tại");
        }
        if (vach != null && sanPhamRepository.existsByMaVachAndNgayXoaIsNull(vach)) {
            throw BusinessException.conflict("MA_VACH_TRUNG", "Mã vạch đã tồn tại");
        }
        SanPham sp = new SanPham();
        sp.setMaSKU(sku);
        sp.setMaVach(vach);
        sp.setTenSanPham(r.tenSanPham().trim());
        sp.setDanhMuc(layDanhMuc(r.maDanhMuc()));
        sp.setThuongHieu(layThuongHieu(r.maThuongHieu()));
        sp.setDonViTinh(r.donViTinh() == null || r.donViTinh().isBlank() ? "cái" : r.donViTinh().trim());
        sp.setMoTa(blankToNull(r.moTa()));
        sp.setThongSoKyThuat(blankToNull(r.thongSoKyThuat()));
        sp.setGiaNiemYet(r.giaNiemYet());
        sp.setGiaToiThieu(r.giaToiThieu());
        if (r.thueSuatVAT() != null) sp.setThueSuatVAT(r.thueSuatVAT());
        if (r.baoHanhThang() != null) sp.setBaoHanhThang(r.baoHanhThang());
        if (r.coTheoDoiSerial() != null) sp.setCoTheoDoiSerial(r.coTheoDoiSerial());
        sp = sanPhamRepository.save(sp);

        TonKho tk = new TonKho();
        tk.setSanPham(sp);
        if (r.tonToiThieu() != null) tk.setTonToiThieu(r.tonToiThieu());
        tonKhoRepository.save(tk);

        ghiLichSuGia(sp, "Giá khởi tạo");
        return toResponse(sp);
    }

    @Transactional
    public SanPhamResponse capNhat(Integer id, SanPhamCapNhatRequest r) {
        SanPham sp = layChuaXoa(id);
        String sku = r.maSKU().trim();
        String vach = blankToNull(r.maVach());
        if (!sku.equalsIgnoreCase(sp.getMaSKU()) && sanPhamRepository.existsByMaSKUAndNgayXoaIsNull(sku)) {
            throw BusinessException.conflict("SKU_TRUNG", "Mã SKU đã tồn tại");
        }
        if (vach != null && !vach.equalsIgnoreCase(sp.getMaVach())
                && sanPhamRepository.existsByMaVachAndNgayXoaIsNull(vach)) {
            throw BusinessException.conflict("MA_VACH_TRUNG", "Mã vạch đã tồn tại");
        }
        if (!TRANG_THAI.contains(r.trangThai())) {
            throw BusinessException.badRequest("TRANG_THAI_KHONG_HOP_LE", "Trạng thái không hợp lệ");
        }
        if (!r.coTheoDoiSerial().equals(sp.getCoTheoDoiSerial()) && slTon(id) > 0) {
            throw BusinessException.conflict("DOI_THEO_DOI_SERIAL",
                    "Không thể đổi chế độ theo dõi Serial khi sản phẩm còn tồn kho");
        }
        sp.setMaSKU(sku);
        sp.setMaVach(vach);
        sp.setTenSanPham(r.tenSanPham().trim());
        sp.setDanhMuc(layDanhMuc(r.maDanhMuc()));
        sp.setThuongHieu(layThuongHieu(r.maThuongHieu()));
        sp.setDonViTinh(r.donViTinh() == null || r.donViTinh().isBlank() ? "cái" : r.donViTinh().trim());
        sp.setMoTa(blankToNull(r.moTa()));
        sp.setThongSoKyThuat(blankToNull(r.thongSoKyThuat()));
        sp.setThueSuatVAT(r.thueSuatVAT());
        sp.setBaoHanhThang(r.baoHanhThang());
        sp.setCoTheoDoiSerial(r.coTheoDoiSerial());
        sp.setTrangThai(r.trangThai());
        return toResponse(sp);
    }

    @Transactional
    public SanPhamResponse doiGia(Integer id, GiaRequest r, String ip) {
        SanPham sp = layChuaXoa(id);
        validateGia(r.giaNiemYet(), r.giaToiThieu());
        boolean giongGia = sp.getGiaNiemYet().compareTo(r.giaNiemYet()) == 0 && bangNhau(sp.getGiaToiThieu(), r.giaToiThieu());
        if (giongGia) {
            throw BusinessException.badRequest("GIA_KHONG_DOI", "Giá mới trùng với giá hiện tại");
        }
        String cu = moTaGia(sp);
        sp.setGiaNiemYet(r.giaNiemYet());
        sp.setGiaToiThieu(r.giaToiThieu());
        ghiLichSuGia(sp, blankToNull(r.ghiChu()));
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "doi_gia", "SanPham",
                String.valueOf(id), cu, moTaGia(sp), ip);
        return toResponse(sp);
    }

    @Transactional(readOnly = true)
    public List<LichSuGiaResponse> lichSuGia(Integer id) {
        layChuaXoa(id);
        return lichSuGiaRepository.findBySanPham_MaSanPhamOrderByNgayHieuLucDesc(id).stream()
                .map(l -> new LichSuGiaResponse(l.getGiaNiemYet(), l.getGiaToiThieu(), l.getNgayHieuLuc(),
                        l.getNguoiThayDoi() == null ? null : l.getNguoiThayDoi().getHoTen(), l.getGhiChu()))
                .toList();
    }

    @Transactional
    public void xoa(Integer id, String ip) {
        SanPham sp = layChuaXoa(id);
        if (slTon(id) > 0) {
            throw BusinessException.conflict("SAN_PHAM_CON_TON", "Không thể xóa sản phẩm còn tồn kho");
        }
        sp.setNgayXoa(java.time.LocalDateTime.now());
        sp.setTrangThai("ngung_kinh_doanh");     // ràng buộc cohesion: xóa mềm => ngưng kinh doanh
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "xoa_san_pham", "SanPham",
                String.valueOf(id), sp.getMaSKU(), null, ip);
    }

    // ---------------- helpers ----------------

    private Set<Integer> layDanhMucVaCon(Integer goc) {
        Set<Integer> ids = new LinkedHashSet<>();
        Deque<Integer> hangDoi = new ArrayDeque<>();
        hangDoi.add(goc);
        while (!hangDoi.isEmpty()) {
            Integer cur = hangDoi.poll();
            if (ids.add(cur)) {
                danhMucRepository.findByDanhMucCha_MaDanhMucAndNgayXoaIsNullOrderByThuTuHienThi(cur)
                        .forEach(c -> hangDoi.add(c.getMaDanhMuc()));
            }
        }
        return ids;
    }

    private void ghiLichSuGia(SanPham sp, String ghiChu) {
        LichSuGiaBan l = new LichSuGiaBan();
        l.setSanPham(sp);
        l.setGiaNiemYet(sp.getGiaNiemYet());
        l.setGiaToiThieu(sp.getGiaToiThieu());
        l.setNguoiThayDoi(nhanVienRepository.getReferenceById(CurrentUser.require().getMaNhanVien()));
        l.setGhiChu(ghiChu);
        lichSuGiaRepository.save(l);
    }

    private static String moTaGia(SanPham sp) {
        return "GiaNiemYet=" + sp.getGiaNiemYet() + "; GiaToiThieu=" + sp.getGiaToiThieu();
    }

    private static void validateGia(BigDecimal niemYet, BigDecimal toiThieu) {
        if (toiThieu != null && toiThieu.compareTo(niemYet) > 0) {
            throw BusinessException.badRequest("GIA_SAN_VUOT_GIA_NIEM_YET", "Giá tối thiểu không được lớn hơn giá niêm yết");
        }
    }

    private static boolean bangNhau(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private int slTon(Integer maSanPham) {
        return tonKhoRepository.findBySanPham_MaSanPham(maSanPham).map(TonKho::getSlTon).orElse(0);
    }

    private SanPham layChuaXoa(Integer id) {
        return sanPhamRepository.findById(id).filter(s -> s.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm"));
    }

    private DanhMuc layDanhMuc(Integer id) {
        return danhMucRepository.findById(id).filter(d -> d.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
    }

    private ThuongHieu layThuongHieu(Integer id) {
        return thuongHieuRepository.findById(id).filter(t -> t.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private SanPhamResponse toResponse(SanPham sp) {
        TonKho tk = tonKhoRepository.findBySanPham_MaSanPham(sp.getMaSanPham()).orElse(null);
        return new SanPhamResponse(sp.getMaSanPham(), sp.getMaSKU(), sp.getMaVach(), sp.getTenSanPham(),
                sp.getDanhMuc().getMaDanhMuc(), sp.getDanhMuc().getTenDanhMuc(),
                sp.getThuongHieu().getMaThuongHieu(), sp.getThuongHieu().getTenThuongHieu(),
                sp.getDonViTinh(), sp.getMoTa(), sp.getThongSoKyThuat(), sp.getGiaNiemYet(), sp.getGiaToiThieu(),
                sp.getThueSuatVAT(), sp.getBaoHanhThang(), sp.getCoTheoDoiSerial(), sp.getTrangThai(),
                tk == null ? 0 : tk.getSlTon(), tk == null ? 0 : tk.getTonToiThieu());
    }
}
