package com.xenon.qldapm.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/** DTO phân hệ Bảo hành: tra cứu, tiếp nhận yêu cầu, chính sách. */
public final class BaoHanhDtos {

    private BaoHanhDtos() {}

    // ===== Chính sách =====
    public record ChinhSachRequest(@NotBlank @Size(max = 150) String tenChinhSach,
                                   @NotNull @Min(1) @Max(120) Integer thoiHanThang,
                                   @Size(max = 4000) String noiDung, Boolean hoatDong) {}
    public record ChinhSachResponse(Integer maChinhSach, String tenChinhSach, Integer thoiHanThang, String noiDung,
                                    Boolean hoatDong) {}

    // ===== Bảo hành =====
    /** trangThai là trạng thái HIỆU LỰC (con_han quá hạn hiển thị het_han); dangCoHieuLuc = đang trong thời hạn. */
    public record BaoHanhResponse(Integer maBaoHanh, Integer maChiTietHD, String soHoaDon, LocalDateTime ngayMua,
                                  String maSKU, String tenHang, Integer maSerial, String soSerial, String imei,
                                  String loaiBaoHanh, String tenChinhSach, LocalDateTime ngayBatDau,
                                  LocalDateTime ngayKetThuc, String trangThai, Boolean dangCoHieuLuc,
                                  Long soNgayConLai, String tenKhachHang, String soDienThoai) {}

    public record GiaHanRequest(@NotNull Integer maChiTietHD, @NotNull Integer maChinhSach) {}

    public record SerialTomTat(Integer maSerial, String soSerial, String imei, String imei2, String trangThai,
                               String maSKU, String tenSanPham) {}

    /** loaiTraCuu: serial | hoa_don | khach_hang. serial chỉ có khi tra theo Serial/IMEI. */
    public record TraCuuBaoHanhResponse(String loaiTraCuu, SerialTomTat serial, List<BaoHanhResponse> baoHanhs,
                                        List<YeuCauResponse> yeuCaus) {}

    // ===== Yêu cầu bảo hành =====
    public record TiepNhanRequest(@NotNull Integer maBaoHanh,
                                  @NotBlank @Size(max = 150) String tenNguoiGui,
                                  @NotBlank @Size(max = 20) String soDienThoaiNguoiGui,
                                  @NotBlank @Size(max = 2000) String moTaSuCo,
                                  @Size(max = 500) String tinhTrangNhanMay,
                                  @Size(max = 500) String phuKienKemTheo,
                                  LocalDateTime ngayHenTra) {}

    /** trangThai = null: chỉ cập nhật ghi chú/ngày hẹn. */
    public record CapNhatYeuCauRequest(String trangThai, @Size(max = 2000) String ketQuaXuLy,
                                       LocalDateTime ngayHenTra) {}

    public record YeuCauResponse(Integer maYeuCau, String maSoTiepNhan, Integer maBaoHanh, String soHoaDon,
                                 String maSKU, String tenHang, String soSerial, String tenNguoiGui,
                                 String soDienThoaiNguoiGui, String moTaSuCo, String tinhTrangNhanMay,
                                 String phuKienKemTheo, String trangThai, String ketQuaXuLy,
                                 LocalDateTime ngayHenTra, LocalDateTime tiepNhanLuc, LocalDateTime hoanTatLuc,
                                 LocalDateTime traKhachLuc, String nhanVienTiepNhan) {}
}