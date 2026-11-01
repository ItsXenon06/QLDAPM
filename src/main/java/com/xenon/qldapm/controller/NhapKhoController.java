package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.service.NhapKhoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nhap-kho")
@RequiredArgsConstructor
public class NhapKhoController {

    private final NhapKhoService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('KHO_NHAP')")
    public PhieuNhapResponse tao(@Valid @RequestBody PhieuNhapRequest r) { return service.tao(r); }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('KHO_NHAP','KHO_XEM_TON')")
    public PageResponse<PhieuNhapResponse> danhSach(@RequestParam(required = false) String trangThai,
                                                    @RequestParam(required = false) Integer maNCC,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.danhSach(trangThai, maNCC,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("taoLuc").descending()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('KHO_NHAP','KHO_XEM_TON')")
    public PhieuNhapResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    @PostMapping("/{id}/nhan-hang")
    @PreAuthorize("hasAuthority('KHO_NHAP')")
    public PhieuNhapResponse nhanHang(@PathVariable Integer id, @Valid @RequestBody NhanHangRequest r) {
        return service.nhanHang(id, r);
    }

    @PostMapping("/{id}/ket-thuc")
    @PreAuthorize("hasAuthority('KHO_NHAP')")
    public PhieuNhapResponse ketThuc(@PathVariable Integer id) { return service.ketThuc(id); }

    @PostMapping("/{id}/huy")
    @PreAuthorize("hasAuthority('KHO_NHAP')")
    public PhieuNhapResponse huy(@PathVariable Integer id) { return service.huy(id); }
}
