package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhoDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.service.KiemKeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kiem-ke")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('KHO_KIEM_KE')")
public class KiemKeController {

    private final KiemKeService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KiemKeResponse tao(@Valid @RequestBody KiemKeRequest r) { return service.tao(r); }

    @GetMapping
    public PageResponse<KiemKeResponse> danhSach(@RequestParam(required = false) String trangThai,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.danhSach(trangThai,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("ngayKiemKe").descending()));
    }

    @GetMapping("/{id}")
    public KiemKeResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    @PutMapping("/{id}/ket-qua")
    public KiemKeResponse ketQua(@PathVariable Integer id, @Valid @RequestBody KiemKeKetQuaRequest r) {
        return service.nhapKetQua(id, r);
    }

    @PostMapping("/{id}/hoan-thanh")
    public KiemKeResponse hoanThanh(@PathVariable Integer id, HttpServletRequest http) {
        return service.hoanThanh(id, http.getRemoteAddr());
    }

    @PostMapping("/{id}/huy")
    public KiemKeResponse huy(@PathVariable Integer id) { return service.huy(id); }
}
