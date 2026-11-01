package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.service.TonKhoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tồn kho & Serial/IMEI. */
@RestController
@RequestMapping("/api/kho")
@RequiredArgsConstructor
public class KhoController {

    private final TonKhoService service;

    @GetMapping("/ton/{maSanPham}")
    @PreAuthorize("hasAuthority('KHO_XEM_TON')")
    public TonKhoResponse ton(@PathVariable Integer maSanPham) { return service.layTon(maSanPham); }

    @PutMapping("/ton/{maSanPham}/nguong")
    @PreAuthorize("hasAnyAuthority('KHO_NHAP','KHO_KIEM_KE')")
    public TonKhoResponse nguong(@PathVariable Integer maSanPham, @Valid @RequestBody TonKhoNguongRequest r) {
        return service.capNhatNguong(maSanPham, r);
    }

    @GetMapping("/canh-bao")
    @PreAuthorize("hasAuthority('KHO_XEM_TON')")
    public List<TonKhoResponse> canhBao() { return service.canhBao(); }

    /** Nhập/quét Serial hoặc IMEI vào cùng 1 ô. */
    @GetMapping("/serial/tra-cuu")
    @PreAuthorize("hasAnyAuthority('KHO_XEM_TON','BAO_HANH_TRA_CUU')")
    public SerialChiTietResponse traCuu(@RequestParam String ma) { return service.traCuuSerial(ma); }

    @GetMapping("/serial")
    @PreAuthorize("hasAuthority('KHO_XEM_TON')")
    public PageResponse<SerialResponse> serials(@RequestParam Integer maSanPham,
                                                @RequestParam(defaultValue = "trong_kho") String trangThai,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return service.timSerial(maSanPham, trangThai,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("ngayNhap").descending()));
    }
}
