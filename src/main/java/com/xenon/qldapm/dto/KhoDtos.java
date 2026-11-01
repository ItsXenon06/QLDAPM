package com.xenon.qldapm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO của phân hệ Danh mục hàng hóa & Kho. */
public final class KhoDtos {

    private KhoDtos() {}

    // ===== Danh mục / Thương hiệu / NCC =====
    public record DanhMucRequest(@NotBlank @Size(max = 100) String tenDanhMuc, Integer maDanhMucCha,
                                 @Size(max = 2000) String moTa, @Min(0) Integer thuTuHienThi) {}
    public record DanhMucResponse(Integer maDanhMuc, String tenDanhMuc, Integer maDanhMucCha, String moTa,
                                  Integer thuTuHienThi) {}

    public record ThuongHieuRequest(@NotBlank @Size(max = 100) String tenThuongHieu, @Size(max = 100) String quocGia,
                                    @Size(max = 2000) String moTa) {}
    public record ThuongHieuResponse(Integer maThuongHieu, String tenThuongHieu, String quocGia, String moTa) {}

    public record NhaCungCapRequest(@NotBlank @Size(max = 150) String tenNCC, @Email @Size(max = 255) String email,
                                    @Size(max = 20) String soDienThoai, @Size(max = 500) String diaChi,
                                    @Size(max = 150) String nguoiLienHe, @Size(max = 20) String maSoThue) {}
    public record NhaCungCapResponse(Integer maNCC, String tenNCC, String email, String soDienThoai, String diaChi,
                                     String nguoiLienHe, String maSoThue) {}

    // ===== Sản phẩm =====
    public record SanPhamRequest(
            @NotBlank @Size(max = 60) String maSKU, @Size(max = 60) String maVach,
            @NotBlank @Size(max = 255) String tenSanPham,
            @NotNull Integer maDanhMuc, @NotNull Integer maThuongHieu,
            @Size(max = 20) String donViTinh, @Size(max = 4000) String moTa, String thongSoKyThuat,
            @NotNull @DecimalMin("0") BigDecimal giaNiemYet, @DecimalMin("0") BigDecimal giaToiThieu,
            @DecimalMin("0") @DecimalMax("100") BigDecimal thueSuatVAT,
            @Min(0) Integer baoHanhThang, Boolean coTheoDoiSerial, @Min(0) Integer tonToiThieu) {}

    /** Cập nhật thông tin (KHÔNG gồm giá: đổi giá qua endpoint riêng, cần quyền GIA_QUAN_LY). */
    public record SanPhamCapNhatRequest(
            @NotBlank @Size(max = 60) String maSKU, @Size(max = 60) String maVach,
            @NotBlank @Size(max = 255) String tenSanPham,
            @NotNull Integer maDanhMuc, @NotNull Integer maThuongHieu,
            @Size(max = 20) String donViTinh, @Size(max = 4000) String moTa, String thongSoKyThuat,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal thueSuatVAT,
            @NotNull @Min(0) Integer baoHanhThang, @NotNull Boolean coTheoDoiSerial,
            @NotBlank String trangThai) {}

    public record GiaRequest(@NotNull @DecimalMin("0") BigDecimal giaNiemYet, @DecimalMin("0") BigDecimal giaToiThieu,
                             @Size(max = 255) String ghiChu) {}

    public record SanPhamResponse(Integer maSanPham, String maSKU, String maVach, String tenSanPham,
                                  Integer maDanhMuc, String tenDanhMuc, Integer maThuongHieu, String tenThuongHieu,
                                  String donViTinh, String moTa, String thongSoKyThuat,
                                  BigDecimal giaNiemYet, BigDecimal giaToiThieu, BigDecimal thueSuatVAT,
                                  Integer baoHanhThang, Boolean coTheoDoiSerial, String trangThai,
                                  Integer slTon, Integer tonToiThieu) {}

    public record LichSuGiaResponse(BigDecimal giaNiemYet, BigDecimal giaToiThieu, LocalDateTime ngayHieuLuc,
                                    String nguoiThayDoi, String ghiChu) {}

    // ===== Nhập kho =====
    public record PhieuNhapRequest(@NotNull Integer maNCC, @Size(max = 50) String soChungTuNCC,
                                   LocalDateTime ngayDuKien, @Size(max = 500) String ghiChu,
                                   @NotEmpty @Valid List<DongNhapRequest> chiTiets) {}
    public record DongNhapRequest(@NotNull Integer maSanPham, @NotNull @Min(1) Integer slDatHang,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal giaNhap,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal chietKhauPhanTram,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal thueSuat) {}

    public record NhanHangRequest(@NotEmpty @Valid List<DongNhanRequest> dongs) {}
    public record DongNhanRequest(@NotNull Integer maChiTiet, @NotNull @Min(1) Integer soLuong,
                                  @Valid List<SerialInput> serials) {}
    public record SerialInput(@NotBlank @Size(max = 100) String soSerial, @Size(max = 15) String imei,
                              @Size(max = 15) String imei2) {}

    public record PhieuNhapResponse(Integer maPhieuNhap, String maSoPhieu, Integer maNCC, String tenNCC,
                                    String soChungTuNCC, String trangThai, String nguoiLap,
                                    LocalDateTime ngayDuKien, LocalDateTime ngayNhanThuc,
                                    BigDecimal tongTienHang, BigDecimal tongTienThue, BigDecimal tongCong,
                                    String ghiChu, LocalDateTime taoLuc, List<DongNhapResponse> chiTiets) {}
    public record DongNhapResponse(Integer maChiTiet, Integer maSanPham, String maSKU, String tenSanPham,
                                   Boolean coTheoDoiSerial, Integer slDatHang, Integer slDaNhan,
                                   BigDecimal giaNhap, BigDecimal chietKhauPhanTram, BigDecimal thueSuat) {}

    // ===== Tồn kho & Serial =====
    public record TonKhoResponse(Integer maSanPham, String maSKU, String tenSanPham, Integer slTon,
                                 Integer tonToiThieu, Integer tonToiDa, String viTriKho, String mucDo,
                                 LocalDateTime capNhatLuc) {}
    public record TonKhoNguongRequest(@NotNull @Min(0) Integer tonToiThieu, @Min(0) Integer tonToiDa,
                                      @Size(max = 100) String viTriKho) {}

    public record SerialResponse(Integer maSerial, Integer maSanPham, String maSKU, String tenSanPham,
                                 String soSerial, String imei, String imei2, String trangThai,
                                 BigDecimal giaVon, LocalDateTime ngayNhap) {}
    public record LichSuSerialResponse(String trangThaiCu, String trangThaiMoi, String loaiTaiLieu,
                                       Integer maTaiLieu, String thucHienBoi, String ghiChu, LocalDateTime thoiGian) {}
    public record SerialChiTietResponse(SerialResponse serial, String soHoaDon, LocalDateTime ngayMua,
                                        List<LichSuSerialResponse> lichSu) {}

    // ===== Kiểm kê =====
    public record KiemKeRequest(@Size(max = 500) String ghiChu, List<Integer> maSanPhams) {}
    public record KiemKeKetQuaRequest(@NotEmpty @Valid List<DongKiemKeRequest> dongs) {}
    public record DongKiemKeRequest(@NotNull Integer maChiTiet, @Min(0) Integer slThucTe,
                                    @Size(max = 255) String lyDoChenhLech, @Valid List<SerialKiemRequest> serials) {}
    public record SerialKiemRequest(@NotNull Integer maSerial, @NotNull Boolean timThay, @Size(max = 255) String ghiChu) {}

    public record KiemKeResponse(Integer maPhieuKiemKe, String maSoPhieu, String trangThai, String nguoiLap,
                                 LocalDateTime ngayKiemKe, LocalDateTime hoanThanhLuc, String ghiChu,
                                 List<DongKiemKeResponse> chiTiets) {}
    public record DongKiemKeResponse(Integer maChiTiet, Integer maSanPham, String maSKU, String tenSanPham,
                                     Boolean coTheoDoiSerial, Integer slHeThong, Integer slThucTe, Integer chenhLech,
                                     String lyDoChenhLech, Boolean daDieuChinh, List<SerialKiemResponse> serials) {}
    public record SerialKiemResponse(Integer maSerial, String soSerial, String imei, Boolean timThay, String ghiChu) {}
}
