package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.dto.PosDtos.KhachHangRequest;
import com.xenon.qldapm.dto.PosDtos.KhachHangResponse;
import com.xenon.qldapm.service.KhachHangService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/khach-hang")
@RequiredArgsConstructor
public class KhachHangController {

    private static final String XEM = "hasAnyAuthority('POS_BAN_HANG','POS_XEM_HOA_DON','BAO_HANH_TRA_CUU','BAO_HANH_TIEP_NHAN')";
    private static final String GHI = "hasAnyAuthority('POS_BAN_HANG','BAO_HANH_TIEP_NHAN')";

    private final KhachHangService service;

    @GetMapping
    @PreAuthorize(XEM)
    public PageResponse<KhachHangResponse> tim(@RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.tim(keyword,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("hoTen")));
    }

    @GetMapping("/tra-cuu")
    @PreAuthorize(XEM)
    public KhachHangResponse traCuu(@RequestParam String sdt) { return service.traCuuSdt(sdt); }

    @GetMapping("/{id}")
    @PreAuthorize(XEM)
    public KhachHangResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GHI)
    public KhachHangResponse tao(@Valid @RequestBody KhachHangRequest r) { return service.tao(r); }

    @PutMapping("/{id}")
    @PreAuthorize(GHI)
    public KhachHangResponse sua(@PathVariable Integer id, @Valid @RequestBody KhachHangRequest r) {
        return service.sua(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('HE_THONG_NGUOI_DUNG')")
    public void xoa(@PathVariable Integer id) { service.xoa(id); }
}