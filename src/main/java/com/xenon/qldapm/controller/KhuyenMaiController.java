package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.KhuyenMaiDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.service.KhuyenMaiService;
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
@RequestMapping("/api/khuyen-mai")
@RequiredArgsConstructor
public class KhuyenMaiController {

    private static final String XEM = "hasAnyAuthority('KHUYEN_MAI_XEM','KHUYEN_MAI_QUAN_LY')";

    private final KhuyenMaiService service;

    @GetMapping
    @PreAuthorize(XEM)
    public PageResponse<KhuyenMaiResponse> danhSach(@RequestParam(required = false) String trangThai,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.danhSach(trangThai,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("ngayTao").descending()));
    }

    /** Chương trình tự động đang có hiệu lực. */
    @GetMapping("/dang-ap-dung")
    @PreAuthorize(XEM)
    public List<KhuyenMaiResponse> dangApDung() { return service.dangApDung(); }

    @GetMapping("/{id}")
    @PreAuthorize(XEM)
    public KhuyenMaiResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('KHUYEN_MAI_QUAN_LY')")
    public KhuyenMaiResponse tao(@Valid @RequestBody KhuyenMaiRequest r, HttpServletRequest http) {
        return service.tao(r, http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('KHUYEN_MAI_QUAN_LY')")
    public KhuyenMaiResponse capNhat(@PathVariable Integer id, @Valid @RequestBody KhuyenMaiRequest r,
                                     HttpServletRequest http) {
        return service.capNhat(id, r, http.getRemoteAddr());
    }

    /** trangThai: hoat_dong | tam_dung | huy. */
    @PutMapping("/{id}/trang-thai")
    @PreAuthorize("hasAuthority('KHUYEN_MAI_QUAN_LY')")
    public KhuyenMaiResponse doiTrangThai(@PathVariable Integer id, @Valid @RequestBody TrangThaiKmRequest r,
                                          HttpServletRequest http) {
        return service.doiTrangThai(id, r.trangThai(), http.getRemoteAddr());
    }
}