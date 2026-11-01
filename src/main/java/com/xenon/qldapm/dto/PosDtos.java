package com.xenon.qldapm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO của phân hệ Bán hàng POS, Trả hàng, Khách hàng. */
public final class PosDtos {

    private PosDtos() {}

    // ===== Hóa đơn =====
    public record TaoHoaDonRequest(Integer maQuay, Integer maKhachHang) {}
    /** ma = SKU, mã vạch, Serial hoặc IMEI (một ô duy nhất). */
    public record ThemDongRequest(@NotBlank @Size(max = 100) String ma, @Min(1) @Max(999) Integer soLuong) {}
    public record CapNhatDongRequest(@Min(1) @Max(999) Integer soLuong, @DecimalMin("0") BigDecimal giamGia) {}
    public record GanKhachRequest(Integer maKhachHang) {}
    public record HuyHoaDonRequest(@NotBlank @Size(max = 255) String lyDo) {}

    public record ThanhToanRequest(@NotEmpty @Size(max = 4) @Valid List<DongThanhToanRequest> thanhToans) {}
    public record DongThanhToanRequest(@NotBlank String phuongThuc,
                                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal soTien,
                                       @DecimalMin("0") BigDecimal tienKhachDua,
                                       @Size(max = 100) String maThamChieu,
                                       @Size(max = 100) String donViTraGop, @Min(1) Integer soKyHan) {}

    public record HoaDonResponse(Integer maHoaDon, String soHoaDon, Integer maQuay, String tenQuay, String thuNgan,
                                 Integer maKhachHang, String tenKhachHang, String soDienThoai, String trangThai,
                                 BigDecimal tongTienHang, BigDecimal tongGiamGia, BigDecimal tongThanhToan,
                                 BigDecimal tongTienThue, Integer soLanIn, LocalDateTime thoiGianTao,
                                 LocalDateTime thoiGianThanhToan, String lyDoHuy,
                                 List<DongHoaDonResponse> dongs, List<ThanhToanResponse> thanhToans) {}
    public record DongHoaDonResponse(Integer maChiTiet, Integer maSanPham, String maSKU, String tenHang,
                                     Integer maSerial, String soSerial, Integer soLuong, BigDecimal donGia,
                                     BigDecimal giamGia, BigDecimal thanhTien, BigDecimal thueSuat, BigDecimal tienThue,
                                     Integer baoHanhThang, Boolean hieuLuc) {}
    public record ThanhToanResponse(String phuongThuc, String trangThai, BigDecimal soTien, BigDecimal tienKhachDua,
                                    BigDecimal tienThoiLai, String maThamChieu, String donViTraGop, Integer soKyHan,
                                    LocalDateTime thanhToanLuc) {}

    // ===== Trả hàng =====
    public record TaoPhieuTraRequest(@NotNull Integer maHoaDon, @NotBlank @Size(max = 500) String lyDo,
                                     @NotBlank String phuongThucHoan, @NotEmpty @Valid List<DongTraRequest> chiTiets) {}
    public record DongTraRequest(@NotNull Integer maChiTietHD, @NotNull @Min(1) Integer soLuong,
                                 @NotBlank String tinhTrangHang, @Size(max = 255) String ghiChu) {}
    public record TuChoiRequest(@NotBlank @Size(max = 255) String lyDo) {}

    public record PhieuTraResponse(Integer maPhieuTra, String maSoPhieu, Integer maHoaDon, String soHoaDon,
                                   String nguoiXuLy, String nguoiDuyet, String lyDo, String phuongThucHoan,
                                   BigDecimal tongTienHoan, String trangThai, LocalDateTime taoLuc,
                                   LocalDateTime duyetLuc, LocalDateTime hoanTatLuc, List<DongTraResponse> chiTiets) {}
    public record DongTraResponse(Integer maChiTiet, Integer maChiTietHD, String tenHang, String maSKU, String soSerial,
                                  Integer soLuong, String tinhTrangHang, BigDecimal soTienHoan, Boolean nhapLaiKho,
                                  String ghiChu) {}

    // ===== Khách hàng =====
    public record KhachHangRequest(@NotBlank @Size(max = 150) String hoTen, @NotBlank @Size(max = 20) String soDienThoai,
                                   @Email @Size(max = 255) String email, @Size(max = 500) String diaChi,
                                   @Size(max = 200) String tenCongTy, @Size(max = 20) String maSoThue) {}
    public record KhachHangResponse(Integer maKhachHang, String hoTen, String soDienThoai, String email, String diaChi,
                                    String tenCongTy, String maSoThue) {}
}