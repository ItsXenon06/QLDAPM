package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.PosDtos.*;
import com.xenon.qldapm.dto.TraHangDtos.DongCoTheTraResponse;
import com.xenon.qldapm.service.TraHangService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Trả hàng: lập phiếu (thu ngân) -> duyệt (quản lý) -> hoàn tất (hoàn tiền + nhập kho). */
@RestController
@RequestMapping("/api/tra-hang")
@RequiredArgsConstructor
public class TraHangController {

    private static final String XEM = "hasAnyAuthority('POS_TRA_HANG','POS_DUYET_TRA_HANG','POS_XEM_HOA_DON')";

    private final TraHangService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POS_TRA_HANG')")
    public PhieuTraResponse tao(@Valid @RequestBody TaoPhieuTraRequest r) { return service.tao(r); }

    @GetMapping
    @PreAuthorize(XEM)
    public PageResponse<PhieuTraResponse> danhSach(@RequestParam(required = false) String trangThai,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.danhSach(trangThai,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("taoLuc").descending()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(XEM)
    public PhieuTraResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    @GetMapping("/hoa-don/{maHoaDon}")
    @PreAuthorize(XEM)
    public List<PhieuTraResponse> theoHoaDon(@PathVariable Integer maHoaDon) { return service.theoHoaDon(maHoaDon); }

    @GetMapping("/hoa-don/{maHoaDon}/co-the-tra")
    @PreAuthorize("hasAuthority('POS_TRA_HANG')")
    public List<DongCoTheTraResponse> coTheTra(@PathVariable Integer maHoaDon) { return service.coTheTra(maHoaDon); }

    @PostMapping("/{id}/duyet")
    @PreAuthorize("hasAuthority('POS_DUYET_TRA_HANG')")
    public PhieuTraResponse duyet(@PathVariable Integer id, HttpServletRequest http) {
        return service.duyet(id, http.getRemoteAddr());
    }

    @PostMapping("/{id}/tu-choi")
    @PreAuthorize("hasAuthority('POS_DUYET_TRA_HANG')")
    public PhieuTraResponse tuChoi(@PathVariable Integer id, @Valid @RequestBody TuChoiRequest r,
                                   HttpServletRequest http) {
        return service.tuChoi(id, r.lyDo(), http.getRemoteAddr());
    }

    @PostMapping("/{id}/hoan-tat")
    @PreAuthorize("hasAnyAuthority('POS_TRA_HANG','POS_DUYET_TRA_HANG')")
    public PhieuTraResponse hoanTat(@PathVariable Integer id, HttpServletRequest http) {
        return service.hoanTat(id, http.getRemoteAddr());
    }
}