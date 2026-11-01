package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.BaoHanhDtos.ChinhSachRequest;
import com.xenon.qldapm.dto.BaoHanhDtos.ChinhSachResponse;
import com.xenon.qldapm.entity.ChinhSachBaoHanh;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.ChinhSachBaoHanhRepository;
import com.xenon.qldapm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Chính sách bảo hành bổ sung (cửa hàng / mở rộng). Tên duy nhất; không xóa cứng (đã có bảo hành tham chiếu)
 * mà chỉ ngưng áp dụng. Đổi thời hạn chỉ ảnh hưởng các lần gia hạn sau, không đổi bảo hành đã cấp.
 */
@Service
@RequiredArgsConstructor
public class ChinhSachBaoHanhService {

    private final ChinhSachBaoHanhRepository repository;
    private final NhatKyService nhatKyService;

    @Transactional(readOnly = true)
    public List<ChinhSachResponse> danhSach(boolean tatCa) {
        return (tatCa ? repository.findAll() : repository.findByTrangThaiTrue()).stream()
                .sorted(Comparator.comparing(ChinhSachBaoHanh::getTenChinhSach, String.CASE_INSENSITIVE_ORDER))
                .map(ChinhSachBaoHanhService::toResponse).toList();
    }

    @Transactional
    public ChinhSachResponse tao(ChinhSachRequest r, String ip) {
        String ten = r.tenChinhSach().trim();
        if (repository.findByTenChinhSach(ten).isPresent()) {
            throw BusinessException.conflict("CHINH_SACH_TRUNG", "Tên chính sách đã tồn tại");
        }
        ChinhSachBaoHanh c = new ChinhSachBaoHanh();
        apply(c, r, ten);
        c = repository.saveAndFlush(c);
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "tao_chinh_sach_bao_hanh", "ChinhSachBaoHanh",
                String.valueOf(c.getMaChinhSach()), null, ten + " / " + c.getThoiHanThang() + " tháng", ip);
        return toResponse(c);
    }

    @Transactional
    public ChinhSachResponse sua(Integer id, ChinhSachRequest r, String ip) {
        ChinhSachBaoHanh c = lay(id);
        String ten = r.tenChinhSach().trim();
        var trung = repository.findByTenChinhSach(ten);
        if (trung.isPresent() && !trung.get().getMaChinhSach().equals(id)) {
            throw BusinessException.conflict("CHINH_SACH_TRUNG", "Tên chính sách đã tồn tại");
        }
        String cu = c.getTenChinhSach() + " / " + c.getThoiHanThang() + " tháng / " + c.getTrangThai();
        apply(c, r, ten);
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "sua_chinh_sach_bao_hanh", "ChinhSachBaoHanh",
                String.valueOf(id), cu, ten + " / " + c.getThoiHanThang() + " tháng / " + c.getTrangThai(), ip);
        return toResponse(c);
    }

    @Transactional
    public void ngungApDung(Integer id, String ip) {
        ChinhSachBaoHanh c = lay(id);
        c.setTrangThai(false);
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "ngung_chinh_sach_bao_hanh", "ChinhSachBaoHanh",
                String.valueOf(id), c.getTenChinhSach(), "ngung ap dung", ip);
    }

    private static void apply(ChinhSachBaoHanh c, ChinhSachRequest r, String ten) {
        c.setTenChinhSach(ten);
        c.setThoiHanThang(r.thoiHanThang());
        c.setNoiDung(r.noiDung() == null || r.noiDung().isBlank() ? null : r.noiDung().trim());
        if (r.hoatDong() != null) c.setTrangThai(r.hoatDong());
    }

    private ChinhSachBaoHanh lay(Integer id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy chính sách bảo hành"));
    }

    private static ChinhSachResponse toResponse(ChinhSachBaoHanh c) {
        return new ChinhSachResponse(c.getMaChinhSach(), c.getTenChinhSach(), c.getThoiHanThang(), c.getNoiDung(), c.getTrangThai());
    }
}