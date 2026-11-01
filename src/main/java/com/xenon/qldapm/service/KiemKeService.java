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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kiểm kê: dang_kiem -> hoan_thanh | huy.
 * Quy tắc: chỉ 1 phiếu đang kiểm tại một thời điểm; chụp tồn hệ thống lúc tạo; phải đếm đủ mọi dòng mới hoàn thành;
 * dòng lệch bắt buộc có lý do; điều chỉnh theo CHÊNH LỆCH (không ghi đè) để không xóa các giao dịch phát sinh
 * sau lúc chụp. Hàng Serial: serial không tìm thấy => 'mat' (nếu còn 'trong_kho'), tồn giảm đúng số serial mất.
 * Mọi điều chỉnh ghi sổ cái tồn kho + nhật ký hệ thống.
 */
@Service
@RequiredArgsConstructor
public class KiemKeService {

    private final PhieuKiemKeRepository phieuRepository;
    private final ChiTietKiemKeSerialRepository ctSerialRepository;
    private final SanPhamRepository sanPhamRepository;
    private final TonKhoRepository tonKhoRepository;
    private final SerialSanPhamRepository serialRepository;
    private final NhanVienRepository nhanVienRepository;
    private final TonKhoService tonKhoService;
    private final NhatKyService nhatKyService;

    @Transactional
    public KiemKeResponse tao(KiemKeRequest r) {
        TaiKhoanUserDetails u = CurrentUser.require();
        if (phieuRepository.findByTrangThai("dang_kiem", PageRequest.of(0, 1)).hasContent()) {
            throw BusinessException.conflict("DANG_CO_PHIEU_KIEM", "Đang có phiếu kiểm kê chưa hoàn thành");
        }
        List<SanPham> sanPhams;
        if (r.maSanPhams() == null || r.maSanPhams().isEmpty()) {
            sanPhams = sanPhamRepository.findAll().stream().filter(s -> s.getNgayXoa() == null).toList();
        } else {
            sanPhams = r.maSanPhams().stream().distinct().map(id -> sanPhamRepository.findById(id)
                    .filter(s -> s.getNgayXoa() == null)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm #" + id))).toList();
        }
        if (sanPhams.isEmpty()) throw BusinessException.badRequest("KHONG_CO_SAN_PHAM", "Không có sản phẩm để kiểm kê");

        Map<Integer, Integer> tonMap = tonKhoRepository.findAll().stream()
                .collect(Collectors.toMap(t -> t.getSanPham().getMaSanPham(), TonKho::getSlTon));

        PhieuKiemKe p = new PhieuKiemKe();
        p.setMaSoPhieu("TMP-" + UUID.randomUUID().toString().substring(0, 12));
        p.setNguoiLap(nhanVienRepository.getReferenceById(u.getMaNhanVien()));
        p.setGhiChu(blankToNull(r.ghiChu()));
        for (SanPham sp : sanPhams) {
            ChiTietKiemKe ck = new ChiTietKiemKe();
            ck.setSanPham(sp);
            ck.setSlHeThong(tonMap.getOrDefault(sp.getMaSanPham(), 0));
            p.addChiTietKiemKe(ck);
        }
        p = phieuRepository.saveAndFlush(p);
        p.setMaSoPhieu(String.format("KK-%s-%06d", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), p.getMaPhieuKiemKe()));

        // hàng theo dõi Serial: chụp danh sách serial đang trong kho
        List<ChiTietKiemKeSerial> dongSerial = new ArrayList<>();
        for (ChiTietKiemKe ck : p.getChiTiets()) {
            if (Boolean.TRUE.equals(ck.getSanPham().getCoTheoDoiSerial())) {
                for (SerialSanPham s : serialRepository.findBySanPham_MaSanPhamAndTrangThai(
                        ck.getSanPham().getMaSanPham(), "trong_kho", Pageable.unpaged()).getContent()) {
                    ChiTietKiemKeSerial row = new ChiTietKiemKeSerial();
                    row.setChiTietKiemKe(ck);
                    row.setSerial(s);
                    row.setTrangThaiHeThong(s.getTrangThai());
                    dongSerial.add(row);
                }
            }
        }
        ctSerialRepository.saveAll(dongSerial);
        return toResponse(p, true);
    }

    @Transactional
    public KiemKeResponse nhapKetQua(Integer id, KiemKeKetQuaRequest r) {
        PhieuKiemKe p = layDangKiem(id);
        Map<Integer, ChiTietKiemKe> theoId = p.getChiTiets().stream()
                .collect(Collectors.toMap(ChiTietKiemKe::getMaChiTiet, c -> c));
        for (DongKiemKeRequest d : r.dongs()) {
            ChiTietKiemKe ck = theoId.get(d.maChiTiet());
            if (ck == null) throw BusinessException.badRequest("DONG_KHONG_THUOC_PHIEU", "Dòng #" + d.maChiTiet() + " không thuộc phiếu");
            if (Boolean.TRUE.equals(ck.getSanPham().getCoTheoDoiSerial())) {
                List<ChiTietKiemKeSerial> rows = ctSerialRepository.findByChiTietKiemKe_MaChiTiet(ck.getMaChiTiet());
                Map<Integer, ChiTietKiemKeSerial> theoSerial = rows.stream()
                        .collect(Collectors.toMap(x -> x.getSerial().getMaSerial(), x -> x));
                if (d.serials() != null) {
                    for (SerialKiemRequest s : d.serials()) {
                        ChiTietKiemKeSerial row = theoSerial.get(s.maSerial());
                        if (row == null) throw BusinessException.badRequest("SERIAL_NGOAI_PHIEU", "Serial #" + s.maSerial() + " không có trong phiếu kiểm");
                        row.setTimThay(s.timThay());
                        row.setGhiChu(blankToNull(s.ghiChu()));
                    }
                }
                boolean demDu = rows.stream().allMatch(x -> x.getTimThay() != null);
                ck.setSlThucTe(demDu ? (int) rows.stream().filter(x -> Boolean.TRUE.equals(x.getTimThay())).count() : null);
            } else {
                if (d.slThucTe() == null) throw BusinessException.badRequest("THIEU_SO_LUONG", "Cần nhập số lượng thực tế cho dòng #" + d.maChiTiet());
                ck.setSlThucTe(d.slThucTe());
            }
            if (d.lyDoChenhLech() != null) ck.setLyDoChenhLech(blankToNull(d.lyDoChenhLech()));
        }
        return toResponse(p, true);
    }

    @Transactional
    public KiemKeResponse hoanThanh(Integer id, String ip) {
        TaiKhoanUserDetails u = CurrentUser.require();
        PhieuKiemKe p = layDangKiem(id);

        // kiểm tra toàn bộ trước khi điều chỉnh
        for (ChiTietKiemKe ck : p.getChiTiets()) {
            if (ck.getSlThucTe() == null) {
                throw BusinessException.badRequest("CHUA_DEM_XONG", "Còn dòng chưa kiểm xong: " + ck.getSanPham().getMaSKU());
            }
            if (ck.getSlThucTe() != ck.getSlHeThong().intValue()
                    && (ck.getLyDoChenhLech() == null || ck.getLyDoChenhLech().isBlank())) {
                throw BusinessException.badRequest("THIEU_LY_DO", "Dòng lệch phải có lý do: " + ck.getSanPham().getMaSKU());
            }
        }
        for (ChiTietKiemKe ck : p.getChiTiets()) {
            SanPham sp = ck.getSanPham();
            int delta;
            if (Boolean.TRUE.equals(sp.getCoTheoDoiSerial())) {
                int mat = 0;
                for (ChiTietKiemKeSerial row : ctSerialRepository.findByChiTietKiemKe_MaChiTiet(ck.getMaChiTiet())) {
                    if (Boolean.FALSE.equals(row.getTimThay())) {
                        SerialSanPham s = serialRepository.findByIdForUpdate(row.getSerial().getMaSerial())
                                .orElseThrow(() -> new NotFoundException("Không tìm thấy serial"));
                        if ("trong_kho".equals(s.getTrangThai())) {     // nếu đã bán trong lúc kiểm thì bỏ qua
                            s.setTrangThai("mat");
                            tonKhoService.ghiLichSuSerial(s, "trong_kho", "mat", "phieu_kiem_ke", p.getMaPhieuKiemKe(),
                                    u.getMaNhanVien(), "Không tìm thấy khi kiểm kê");
                            mat++;
                        }
                    }
                }
                delta = -mat;
            } else {
                delta = ck.getSlThucTe() - ck.getSlHeThong();
            }
            if (delta != 0) {
                TonKho tk = tonKhoService.dieuChinh(sp.getMaSanPham(), delta, "dieu_chinh", "phieu_kiem_ke",
                        p.getMaPhieuKiemKe(), u.getMaNhanVien(), ck.getLyDoChenhLech());
                nhatKyService.ghi(u.getMaNhanVien(), "dieu_chinh_kho", "TonKho", String.valueOf(sp.getMaSanPham()),
                        "SLTon=" + (tk.getSlTon() - delta), "SLTon=" + tk.getSlTon() + " (" + p.getMaSoPhieu() + ")", ip);
            }
            ck.setDaDieuChinh(true);
        }
        p.setTrangThai("hoan_thanh");
        p.setHoanThanhLuc(LocalDateTime.now());
        return toResponse(p, true);
    }

    @Transactional
    public KiemKeResponse huy(Integer id) {
        PhieuKiemKe p = layDangKiem(id);
        p.setTrangThai("huy");
        return toResponse(p, true);
    }

    @Transactional(readOnly = true)
    public KiemKeResponse chiTiet(Integer id) {
        return toResponse(phieuRepository.findWithChiTiets(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kiểm kê")), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<KiemKeResponse> danhSach(String trangThai, Pageable pageable) {
        Page<PhieuKiemKe> page = trangThai != null && !trangThai.isBlank()
                ? phieuRepository.findByTrangThai(trangThai, pageable) : phieuRepository.findAll(pageable);
        return PageResponse.of(page.map(p -> toResponse(p, false)));
    }

    // ---------------- helpers ----------------

    private PhieuKiemKe layDangKiem(Integer id) {
        PhieuKiemKe p = phieuRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kiểm kê"));
        if (!"dang_kiem".equals(p.getTrangThai())) {
            throw BusinessException.conflict("PHIEU_DA_DONG", "Phiếu kiểm kê đã " + ("hoan_thanh".equals(p.getTrangThai()) ? "hoàn thành" : "hủy"));
        }
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private KiemKeResponse toResponse(PhieuKiemKe p, boolean kemChiTiet) {
        List<DongKiemKeResponse> dongs = !kemChiTiet ? null : p.getChiTiets().stream().map(ck -> {
            boolean coSerial = Boolean.TRUE.equals(ck.getSanPham().getCoTheoDoiSerial());
            List<SerialKiemResponse> serials = !coSerial ? List.of()
                    : ctSerialRepository.findByChiTietKiemKe_MaChiTiet(ck.getMaChiTiet()).stream()
                    .map(x -> new SerialKiemResponse(x.getSerial().getMaSerial(), x.getSerial().getSoSerial(),
                            x.getSerial().getImei(), x.getTimThay(), x.getGhiChu())).toList();
            Integer chenh = ck.getSlThucTe() == null ? null : ck.getSlThucTe() - ck.getSlHeThong();
            return new DongKiemKeResponse(ck.getMaChiTiet(), ck.getSanPham().getMaSanPham(), ck.getSanPham().getMaSKU(),
                    ck.getSanPham().getTenSanPham(), coSerial, ck.getSlHeThong(), ck.getSlThucTe(), chenh,
                    ck.getLyDoChenhLech(), ck.getDaDieuChinh(), serials);
        }).toList();
        return new KiemKeResponse(p.getMaPhieuKiemKe(), p.getMaSoPhieu(), p.getTrangThai(), p.getNguoiLap().getHoTen(),
                p.getNgayKiemKe(), p.getHoanThanhLuc(), p.getGhiChu(), dongs);
    }
}
