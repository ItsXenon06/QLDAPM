package com.xenon.qldapm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO phân hệ Khuyến mãi & Voucher. */
public final class KhuyenMaiDtos {

    private KhuyenMaiDtos() {}

    public record MaGiamGiaRequest(@NotBlank @Size(max = 50) String ma) {}

    /** Tóm tắt khuyến mãi/voucher đang áp dụng trên hóa đơn. */
    public record KmApDungResponse(String tenChuongTrinh, String maCode, String loai, BigDecimal soTienGiam) {}

    public record KhuyenMaiItemRequest(@NotBlank String vaiTro, Integer maSanPham, Integer maDanhMuc,
                                       @Min(1) Integer soLuong, @DecimalMin("0") BigDecimal giaUuDai) {}

    public record KhuyenMaiRequest(
            @NotBlank @Size(max = 255) String tenChuongTrinh, @Size(max = 50) String maCode,
            @NotBlank String loaiKhuyenMai,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal phanTramGiam,
            @DecimalMin(value = "0", inclusive = false) BigDecimal soTienGiamToiDa,
            @DecimalMin(value = "0", inclusive = false) BigDecimal giamCodinh,
            @DecimalMin(value = "0", inclusive = false) BigDecimal giaTriVoucher,
            @DecimalMin("0") BigDecimal giaTriDonToiThieu,
            Boolean tuDongApDung, Boolean choPhepCongDon, Integer doUuTien, Boolean apDungToanBo,
            @Min(1) Integer soLuongToiDa,
            @NotNull LocalDateTime ngayBatDau, @NotNull LocalDateTime ngayKetThuc,
            @Size(max = 2000) String moTa, List<@Valid KhuyenMaiItemRequest> items) {}

    public record TrangThaiKmRequest(@NotBlank String trangThai) {}

    public record KhuyenMaiItemResponse(String vaiTro, Integer maSanPham, String maSKU, String tenSanPham,
                                        Integer maDanhMuc, String tenDanhMuc, Integer soLuong, BigDecimal giaUuDai) {}

    public record KhuyenMaiResponse(Integer maKhuyenMai, String tenChuongTrinh, String maCode, String loaiKhuyenMai,
                                    BigDecimal phanTramGiam, BigDecimal soTienGiamToiDa, BigDecimal giamCodinh,
                                    BigDecimal giaTriVoucher, BigDecimal giaTriDonToiThieu, Boolean tuDongApDung,
                                    Boolean choPhepCongDon, Integer doUuTien, Boolean apDungToanBo,
                                    Integer soLuongToiDa, Integer soLuongDaDung, LocalDateTime ngayBatDau,
                                    LocalDateTime ngayKetThuc, String trangThai, String moTa,
                                    List<KhuyenMaiItemResponse> items) {}

    public record VoucherResponse(Integer maVoucher, String maCode, Integer maKhuyenMai, BigDecimal giaTri,
                                  Integer maKhachHang, String trangThai, LocalDateTime ngayPhatHanh,
                                  LocalDateTime hanSuDung, LocalDateTime ngaySuDung, String tenKhachHang, String soDienThoai,
                                  String tenChuongTrinh, String soHoaDonPhatHanh) {}
}