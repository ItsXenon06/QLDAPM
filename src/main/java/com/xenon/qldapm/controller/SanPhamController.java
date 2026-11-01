package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.service.SanPhamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/san-pham")
@RequiredArgsConstructor
public class SanPhamController {

    private final SanPhamService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SAN_PHAM_XEM')")
    public PageResponse<SanPhamResponse> tim(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer maDanhMuc,
                                             @RequestParam(required = false) Integer maThuongHieu,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.tim(keyword, maDanhMuc, maThuongHieu,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("tenSanPham")));
    }

    /** POS: tra cứu nhanh theo SKU hoặc mã vạch. */
    @GetMapping("/tra-cuu")
    @PreAuthorize("hasAuthority('SAN_PHAM_XEM')")
    public SanPhamResponse traCuu(@RequestParam String ma) {
        return service.traCuuMa(ma);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SAN_PHAM_XEM')")
    public SanPhamResponse chiTiet(@PathVariable Integer id) {
        return service.chiTiet(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public SanPhamResponse tao(@Valid @RequestBody SanPhamRequest req) {
        return service.tao(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public SanPhamResponse capNhat(@PathVariable Integer id, @Valid @RequestBody SanPhamCapNhatRequest req) {
        return service.capNhat(id, req);
    }

    @PutMapping("/{id}/gia")
    @PreAuthorize("hasAuthority('GIA_QUAN_LY')")
    public SanPhamResponse doiGia(@PathVariable Integer id, @Valid @RequestBody GiaRequest req, HttpServletRequest http) {
        return service.doiGia(id, req, http.getRemoteAddr());
    }

    @GetMapping("/{id}/lich-su-gia")
    @PreAuthorize("hasAnyAuthority('GIA_QUAN_LY','SAN_PHAM_QUAN_LY')")
    public List<LichSuGiaResponse> lichSuGia(@PathVariable Integer id) {
        return service.lichSuGia(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public void xoa(@PathVariable Integer id, HttpServletRequest http) {
        service.xoa(id, http.getRemoteAddr());
    }
}
