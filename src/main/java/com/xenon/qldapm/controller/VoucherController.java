package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhuyenMaiDtos.VoucherResponse;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.VoucherDtos.HuyVoucherRequest;
import com.xenon.qldapm.service.VoucherService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private static final String XEM = "hasAnyAuthority('KHUYEN_MAI_XEM','KHUYEN_MAI_QUAN_LY')";

    private final VoucherService service;

    @GetMapping
    @PreAuthorize("hasAuthority('KHUYEN_MAI_QUAN_LY')")
    public PageResponse<VoucherResponse> tim(@RequestParam(required = false) String trangThai,
                                             @RequestParam(required = false) Integer maKhachHang,
                                             @RequestParam(required = false) Integer maKhuyenMai,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.tim(trangThai, maKhachHang, maKhuyenMai,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("ngayPhatHanh").descending()));
    }

    @GetMapping("/tra-cuu")
    @PreAuthorize(XEM)
    public VoucherResponse traCuu(@RequestParam String ma) { return service.traCuu(ma); }

    @GetMapping("/khach-hang/{maKhachHang}/kha-dung")
    @PreAuthorize(XEM)
    public List<VoucherResponse> khaDung(@PathVariable Integer maKhachHang) { return service.khaDungCuaKhach(maKhachHang); }

    @PostMapping("/{id}/huy")
    @PreAuthorize("hasAuthority('KHUYEN_MAI_QUAN_LY')")
    public VoucherResponse huy(@PathVariable Integer id, @Valid @RequestBody HuyVoucherRequest r, HttpServletRequest http) {
        return service.huy(id, r.lyDo(), http.getRemoteAddr());
    }
}