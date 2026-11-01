package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Danh mục, thương hiệu, nhà cung cấp. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService service;

    // ---- danh mục ----
    @GetMapping("/danh-muc")
    @PreAuthorize("hasAuthority('SAN_PHAM_XEM')")
    public List<DanhMucResponse> danhMuc() { return service.danhSachDanhMuc(); }

    @PostMapping("/danh-muc")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public DanhMucResponse taoDanhMuc(@Valid @RequestBody DanhMucRequest r) { return service.taoDanhMuc(r); }

    @PutMapping("/danh-muc/{id}")
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public DanhMucResponse suaDanhMuc(@PathVariable Integer id, @Valid @RequestBody DanhMucRequest r) {
        return service.suaDanhMuc(id, r);
    }

    @DeleteMapping("/danh-muc/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public void xoaDanhMuc(@PathVariable Integer id) { service.xoaDanhMuc(id); }

    // ---- thương hiệu ----
    @GetMapping("/thuong-hieu")
    @PreAuthorize("hasAuthority('SAN_PHAM_XEM')")
    public List<ThuongHieuResponse> thuongHieu() { return service.danhSachThuongHieu(); }

    @PostMapping("/thuong-hieu")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public ThuongHieuResponse taoThuongHieu(@Valid @RequestBody ThuongHieuRequest r) { return service.taoThuongHieu(r); }

    @PutMapping("/thuong-hieu/{id}")
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public ThuongHieuResponse suaThuongHieu(@PathVariable Integer id, @Valid @RequestBody ThuongHieuRequest r) {
        return service.suaThuongHieu(id, r);
    }

    @DeleteMapping("/thuong-hieu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SAN_PHAM_QUAN_LY')")
    public void xoaThuongHieu(@PathVariable Integer id) { service.xoaThuongHieu(id); }

    // ---- nhà cung cấp ----
    @GetMapping("/nha-cung-cap")
    @PreAuthorize("hasAnyAuthority('NHA_CUNG_CAP_QUAN_LY','KHO_NHAP')")
    public List<NhaCungCapResponse> ncc() { return service.danhSachNcc(); }

    @PostMapping("/nha-cung-cap")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('NHA_CUNG_CAP_QUAN_LY')")
    public NhaCungCapResponse taoNcc(@Valid @RequestBody NhaCungCapRequest r) { return service.taoNcc(r); }

    @PutMapping("/nha-cung-cap/{id}")
    @PreAuthorize("hasAuthority('NHA_CUNG_CAP_QUAN_LY')")
    public NhaCungCapResponse suaNcc(@PathVariable Integer id, @Valid @RequestBody NhaCungCapRequest r) {
        return service.suaNcc(id, r);
    }

    @DeleteMapping("/nha-cung-cap/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('NHA_CUNG_CAP_QUAN_LY')")
    public void xoaNcc(@PathVariable Integer id) { service.xoaNcc(id); }
}
