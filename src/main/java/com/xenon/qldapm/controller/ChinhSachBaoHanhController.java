package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.BaoHanhDtos.ChinhSachRequest;
import com.xenon.qldapm.dto.BaoHanhDtos.ChinhSachResponse;
import com.xenon.qldapm.service.ChinhSachBaoHanhService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chinh-sach-bao-hanh")
@RequiredArgsConstructor
public class ChinhSachBaoHanhController {

    private final ChinhSachBaoHanhService service;

    /** Chính sách đang áp dụng. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('BAO_HANH_TRA_CUU','BAO_HANH_TIEP_NHAN','BAO_HANH_CHINH_SACH')")
    public List<ChinhSachResponse> danhSach() { return service.danhSach(false); }

    /** Cả chính sách đã ngưng. */
    @GetMapping("/tat-ca")
    @PreAuthorize("hasAuthority('BAO_HANH_CHINH_SACH')")
    public List<ChinhSachResponse> tatCa() { return service.danhSach(true); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BAO_HANH_CHINH_SACH')")
    public ChinhSachResponse tao(@Valid @RequestBody ChinhSachRequest r, HttpServletRequest http) {
        return service.tao(r, http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BAO_HANH_CHINH_SACH')")
    public ChinhSachResponse sua(@PathVariable Integer id, @Valid @RequestBody ChinhSachRequest r, HttpServletRequest http) {
        return service.sua(id, r, http.getRemoteAddr());
    }

    /** Ngưng áp dụng (không xóa cứng). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BAO_HANH_CHINH_SACH')")
    public void ngung(@PathVariable Integer id, HttpServletRequest http) { service.ngungApDung(id, http.getRemoteAddr()); }
}