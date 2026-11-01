package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Danh mục (cây ngành hàng), Thương hiệu, Nhà cung cấp. Tất cả xóa mềm. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final DanhMucRepository danhMucRepository;
    private final ThuongHieuRepository thuongHieuRepository;
    private final NhaCungCapRepository nhaCungCapRepository;
    private final SanPhamRepository sanPhamRepository;

    // ================= Danh mục =================

    @Transactional(readOnly = true)
    public List<DanhMucResponse> danhSachDanhMuc() {
        return danhMucRepository.findAll().stream().filter(d -> d.getNgayXoa() == null)
                .sorted(Comparator.comparing(DanhMuc::getThuTuHienThi).thenComparing(DanhMuc::getTenDanhMuc))
                .map(CatalogService::toResponse).toList();
    }

    @Transactional
    public DanhMucResponse taoDanhMuc(DanhMucRequest r) {
        DanhMuc cha = r.maDanhMucCha() == null ? null : layDanhMuc(r.maDanhMucCha());
        kiemTraTrungTen(r.tenDanhMuc().trim(), r.maDanhMucCha(), null);
        DanhMuc d = new DanhMuc();
        d.setTenDanhMuc(r.tenDanhMuc().trim());
        d.setDanhMucCha(cha);
        d.setMoTa(blankToNull(r.moTa()));
        if (r.thuTuHienThi() != null) d.setThuTuHienThi(r.thuTuHienThi());
        return toResponse(danhMucRepository.save(d));
    }

    @Transactional
    public DanhMucResponse suaDanhMuc(Integer id, DanhMucRequest r) {
        DanhMuc d = layDanhMuc(id);
        DanhMuc cha = r.maDanhMucCha() == null ? null : layDanhMuc(r.maDanhMucCha());
        // chống vòng lặp: đi ngược từ cha mới lên gốc, không được gặp chính nó
        Integer cur = r.maDanhMucCha();
        while (cur != null) {
            if (cur.equals(id)) {
                throw BusinessException.badRequest("DANH_MUC_VONG_LAP", "Không thể chọn danh mục con làm danh mục cha");
            }
            cur = danhMucRepository.findById(cur)
                    .map(x -> x.getDanhMucCha() == null ? null : x.getDanhMucCha().getMaDanhMuc()).orElse(null);
        }
        kiemTraTrungTen(r.tenDanhMuc().trim(), r.maDanhMucCha(), id);
        d.setTenDanhMuc(r.tenDanhMuc().trim());
        d.setDanhMucCha(cha);
        d.setMoTa(blankToNull(r.moTa()));
        if (r.thuTuHienThi() != null) d.setThuTuHienThi(r.thuTuHienThi());
        return toResponse(d);
    }

    @Transactional
    public void xoaDanhMuc(Integer id) {
        DanhMuc d = layDanhMuc(id);
        if (!danhMucRepository.findByDanhMucCha_MaDanhMucAndNgayXoaIsNullOrderByThuTuHienThi(id).isEmpty()) {
            throw BusinessException.conflict("DANH_MUC_CON_CON", "Danh mục còn danh mục con");
        }
        if (sanPhamRepository.existsByDanhMuc_MaDanhMucAndNgayXoaIsNull(id)) {
            throw BusinessException.conflict("DANH_MUC_CON_SAN_PHAM", "Danh mục còn sản phẩm");
        }
        d.setNgayXoa(LocalDateTime.now());
    }

    private void kiemTraTrungTen(String ten, Integer maCha, Integer boQuaId) {
        List<DanhMuc> cungCap = maCha == null
                ? danhMucRepository.findByDanhMucChaIsNullAndNgayXoaIsNullOrderByThuTuHienThi()
                : danhMucRepository.findByDanhMucCha_MaDanhMucAndNgayXoaIsNullOrderByThuTuHienThi(maCha);
        if (cungCap.stream().anyMatch(x -> x.getTenDanhMuc().equalsIgnoreCase(ten) && !x.getMaDanhMuc().equals(boQuaId))) {
            throw BusinessException.conflict("DANH_MUC_TRUNG_TEN", "Tên danh mục đã tồn tại trong cùng cấp");
        }
    }

    private DanhMuc layDanhMuc(Integer id) {
        return danhMucRepository.findById(id).filter(d -> d.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
    }

    private static DanhMucResponse toResponse(DanhMuc d) {
        return new DanhMucResponse(d.getMaDanhMuc(), d.getTenDanhMuc(),
                d.getDanhMucCha() == null ? null : d.getDanhMucCha().getMaDanhMuc(), d.getMoTa(), d.getThuTuHienThi());
    }

    // ================= Thương hiệu =================

    @Transactional(readOnly = true)
    public List<ThuongHieuResponse> danhSachThuongHieu() {
        return thuongHieuRepository.findByNgayXoaIsNull().stream()
                .sorted(Comparator.comparing(ThuongHieu::getTenThuongHieu, String.CASE_INSENSITIVE_ORDER))
                .map(CatalogService::toResponse).toList();
    }

    /** Tên thương hiệu của bản ghi đã xóa mềm vẫn chiếm unique index => tạo lại sẽ khôi phục bản ghi cũ. */
    @Transactional
    public ThuongHieuResponse taoThuongHieu(ThuongHieuRequest r) {
        String ten = r.tenThuongHieu().trim();
        var cu = thuongHieuRepository.findByTenThuongHieu(ten);
        if (cu.isPresent()) {
            ThuongHieu t = cu.get();
            if (t.getNgayXoa() == null) {
                throw BusinessException.conflict("THUONG_HIEU_TRUNG", "Thương hiệu đã tồn tại");
            }
            t.setNgayXoa(null);
            t.setTrangThai(true);
            apply(t, r);
            return toResponse(t);
        }
        ThuongHieu t = new ThuongHieu();
        apply(t, r);
        return toResponse(thuongHieuRepository.save(t));
    }

    @Transactional
    public ThuongHieuResponse suaThuongHieu(Integer id, ThuongHieuRequest r) {
        ThuongHieu t = layThuongHieu(id);
        var trung = thuongHieuRepository.findByTenThuongHieu(r.tenThuongHieu().trim());
        if (trung.isPresent() && !trung.get().getMaThuongHieu().equals(id)) {
            throw BusinessException.conflict("THUONG_HIEU_TRUNG", "Tên thương hiệu đã tồn tại");
        }
        apply(t, r);
        return toResponse(t);
    }

    @Transactional
    public void xoaThuongHieu(Integer id) {
        ThuongHieu t = layThuongHieu(id);
        if (sanPhamRepository.existsByThuongHieu_MaThuongHieuAndNgayXoaIsNull(id)) {
            throw BusinessException.conflict("THUONG_HIEU_CON_SAN_PHAM", "Thương hiệu còn sản phẩm");
        }
        t.setNgayXoa(LocalDateTime.now());
        t.setTrangThai(false);                    // cohesion: NgayXoa != null => TrangThai = 0
    }

    private static void apply(ThuongHieu t, ThuongHieuRequest r) {
        t.setTenThuongHieu(r.tenThuongHieu().trim());
        t.setQuocGia(blankToNull(r.quocGia()));
        t.setMoTa(blankToNull(r.moTa()));
    }

    private ThuongHieu layThuongHieu(Integer id) {
        return thuongHieuRepository.findById(id).filter(t -> t.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu"));
    }

    private static ThuongHieuResponse toResponse(ThuongHieu t) {
        return new ThuongHieuResponse(t.getMaThuongHieu(), t.getTenThuongHieu(), t.getQuocGia(), t.getMoTa());
    }

    // ================= Nhà cung cấp =================

    @Transactional(readOnly = true)
    public List<NhaCungCapResponse> danhSachNcc() {
        return nhaCungCapRepository.findByNgayXoaIsNull().stream()
                .sorted(Comparator.comparing(NhaCungCap::getTenNCC, String.CASE_INSENSITIVE_ORDER))
                .map(CatalogService::toResponse).toList();
    }

    @Transactional
    public NhaCungCapResponse taoNcc(NhaCungCapRequest r) {
        String mst = blankToNull(r.maSoThue());
        if (mst != null && nhaCungCapRepository.findByMaSoThueAndNgayXoaIsNull(mst).isPresent()) {
            throw BusinessException.conflict("MST_TRUNG", "Mã số thuế đã tồn tại");
        }
        NhaCungCap n = new NhaCungCap();
        apply(n, r);
        return toResponse(nhaCungCapRepository.save(n));
    }

    @Transactional
    public NhaCungCapResponse suaNcc(Integer id, NhaCungCapRequest r) {
        NhaCungCap n = layNcc(id);
        String mst = blankToNull(r.maSoThue());
        if (mst != null) {
            var trung = nhaCungCapRepository.findByMaSoThueAndNgayXoaIsNull(mst);
            if (trung.isPresent() && !trung.get().getMaNCC().equals(id)) {
                throw BusinessException.conflict("MST_TRUNG", "Mã số thuế đã tồn tại");
            }
        }
        apply(n, r);
        return toResponse(n);
    }

    @Transactional
    public void xoaNcc(Integer id) {
        NhaCungCap n = layNcc(id);
        n.setNgayXoa(LocalDateTime.now());
        n.setTrangThai(false);
    }

    private static void apply(NhaCungCap n, NhaCungCapRequest r) {
        n.setTenNCC(r.tenNCC().trim());
        n.setEmail(blankToNull(r.email()));
        n.setSoDienThoai(blankToNull(r.soDienThoai()));
        n.setDiaChi(blankToNull(r.diaChi()));
        n.setNguoiLienHe(blankToNull(r.nguoiLienHe()));
        n.setMaSoThue(blankToNull(r.maSoThue()));
    }

    private NhaCungCap layNcc(Integer id) {
        return nhaCungCapRepository.findById(id).filter(n -> n.getNgayXoa() == null)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));
    }

    private static NhaCungCapResponse toResponse(NhaCungCap n) {
        return new NhaCungCapResponse(n.getMaNCC(), n.getTenNCC(), n.getEmail(), n.getSoDienThoai(),
                n.getDiaChi(), n.getNguoiLienHe(), n.getMaSoThue());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
