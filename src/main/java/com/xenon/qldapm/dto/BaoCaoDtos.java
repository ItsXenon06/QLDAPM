package com.xenon.qldapm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/** DTO phân hệ Báo cáo - Thống kê. Mọi báo cáo dạng bảng dùng chung BaoCaoBang (xem JSON, xuất Excel/PDF). */
public final class BaoCaoDtos {

    private BaoCaoDtos() {}

    /** Tham số lọc chung; mỗi báo cáo chỉ dùng các tham số liên quan. */
    public record BaoCaoThamSo(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den,
                               String nhom, Integer maQuay, Integer maThuNgan, Integer maSanPham,
                               Integer soNgay, Integer top, Boolean chiCanhBao) {}

    /** tong = dòng tổng (null nếu báo cáo không cộng dồn). Số là Number, còn lại là chuỗi đã định dạng. */
    public record BaoCaoBang(String tieuDe, String moTa, List<String> cot, List<List<Object>> dong, List<Object> tong) {}

    public record DiemDoanhThu(String nhan, BigDecimal doanhThu) {}

    public record TongQuanResponse(LocalDate ngay, long soHoaDon, BigDecimal doanhThuGop, BigDecimal tienHoan,
                                   BigDecimal doanhThuThuan, long hoaDonDangLap, long matHangCanhBao,
                                   long yeuCauBaoHanhDangMo, List<DiemDoanhThu> theoGio) {}
}