package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhuyenMaiDtos.MaGiamGiaRequest;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.PosDtos.*;
import com.xenon.qldapm.service.PosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Bán hàng tại quầy POS: hóa đơn nháp, thanh toán, in, hủy. */
@RestController
@RequestMapping("/api/pos/hoa-don")
@RequiredArgsConstructor
public class PosController {

    private final PosService service;

    // ---------- tạo / xem ----------

    /** Body có thể bỏ trống: quầy được suy ra từ địa chỉ IP máy POS. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse tao(@Valid @RequestBody(required = false) TaoHoaDonRequest r, HttpServletRequest http) {
        return service.tao(r == null ? new TaoHoaDonRequest(null, null) : r, http.getRemoteAddr());
    }

    /** Hóa đơn đang lập / tạm giữ của thu ngân hiện tại. */
    @GetMapping("/nhap-cua-toi")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public List<HoaDonResponse> nhapCuaToi() { return service.nhapCuaToi(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('POS_BAN_HANG','POS_XEM_HOA_DON')")
    public HoaDonResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    @GetMapping
    @PreAuthorize("hasAuthority('POS_XEM_HOA_DON')")
    public PageResponse<HoaDonResponse> danhSach(@RequestParam(required = false) String soHoaDon,
                                                 @RequestParam(required = false) String trangThai,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.danhSach(soHoaDon, trangThai, tu, den,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by("thoiGianThanhToan").descending()));
    }

    // ---------- chỉnh sửa hóa đơn nháp ----------

    /** ma = SKU, mã vạch, Serial hoặc IMEI. */
    @PostMapping("/{id}/dong")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse themDong(@PathVariable Integer id, @Valid @RequestBody ThemDongRequest r) {
        return service.themDong(id, r);
    }

    @PutMapping("/{id}/dong/{maChiTiet}")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse capNhatDong(@PathVariable Integer id, @PathVariable Integer maChiTiet,
                                      @Valid @RequestBody CapNhatDongRequest r) {
        return service.capNhatDong(id, maChiTiet, r);
    }

    @DeleteMapping("/{id}/dong/{maChiTiet}")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse xoaDong(@PathVariable Integer id, @PathVariable Integer maChiTiet) {
        return service.xoaDong(id, maChiTiet);
    }

    /** maKhachHang = null để chuyển về khách vãng lai. */
    @PutMapping("/{id}/khach-hang")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse ganKhach(@PathVariable Integer id, @RequestBody GanKhachRequest r) {
        return service.ganKhach(id, r.maKhachHang());
    }

    @PostMapping("/{id}/ma-giam-gia")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse apDungMa(@PathVariable Integer id, @Valid @RequestBody MaGiamGiaRequest r) {
        return service.apDungMa(id, r.ma());
    }

    @DeleteMapping("/{id}/ma-giam-gia")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse goMa(@PathVariable Integer id, @RequestParam String ma) {
        return service.goMa(id, ma);
    }

    @PostMapping("/{id}/tam-giu")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse tamGiu(@PathVariable Integer id) { return service.tamGiu(id); }

    @PostMapping("/{id}/tiep-tuc")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse tiepTuc(@PathVariable Integer id) { return service.tiepTuc(id); }

    /** Hủy hóa đơn nháp (xóa hẳn). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public void huyNhap(@PathVariable Integer id) { service.huyNhap(id); }

    // ---------- thanh toán / in / hủy ----------

    @PostMapping("/{id}/thanh-toan")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public HoaDonResponse thanhToan(@PathVariable Integer id, @Valid @RequestBody ThanhToanRequest r) {
        return service.thanhToan(id, r);
    }

    /** Ghi nhận 1 lần in (client tự in theo dữ liệu hóa đơn trả về). */
    @PostMapping("/{id}/in")
    @PreAuthorize("hasAnyAuthority('POS_BAN_HANG','POS_XEM_HOA_DON')")
    public HoaDonResponse ghiNhanIn(@PathVariable Integer id) { return service.ghiNhanIn(id); }

    /** Hủy hóa đơn đã thanh toán (cùng ngày, chưa có phiếu trả). */
    @PostMapping("/{id}/huy")
    @PreAuthorize("hasAuthority('POS_HUY_HOA_DON')")
    public HoaDonResponse huy(@PathVariable Integer id, @Valid @RequestBody HuyHoaDonRequest r,
                              HttpServletRequest http) {
        return service.huyHoaDon(id, r.lyDo(), http.getRemoteAddr());
    }
}