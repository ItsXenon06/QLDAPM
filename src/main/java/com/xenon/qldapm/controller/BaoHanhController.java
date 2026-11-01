package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.BaoHanhDtos.*;
import com.xenon.qldapm.dto.PageResponse;
import com.xenon.qldapm.service.BaoHanhService;
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
@RequestMapping("/api/bao-hanh")
@RequiredArgsConstructor
public class BaoHanhController {

    private static final String XEM = "hasAnyAuthority('BAO_HANH_TRA_CUU','BAO_HANH_TIEP_NHAN')";

    private final BaoHanhService service;

    /** Một ô: Serial/IMEI, số hóa đơn hoặc số điện thoại khách. */
    @GetMapping("/tra-cuu")
    @PreAuthorize(XEM)
    public TraCuuBaoHanhResponse traCuu(@RequestParam String ma) { return service.traCuu(ma); }

    @PostMapping("/gia-han")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BAO_HANH_CHINH_SACH')")
    public BaoHanhResponse giaHan(@Valid @RequestBody GiaHanRequest r, HttpServletRequest http) {
        return service.giaHan(r, http.getRemoteAddr());
    }

    @PostMapping("/yeu-cau")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BAO_HANH_TIEP_NHAN')")
    public YeuCauResponse tiepNhan(@Valid @RequestBody TiepNhanRequest r, HttpServletRequest http) {
        return service.tiepNhan(r, http.getRemoteAddr());
    }

    @GetMapping("/yeu-cau")
    @PreAuthorize(XEM)
    public PageResponse<YeuCauResponse> danhSach(@RequestParam(required = false) String trangThai,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.danhSach(trangThai,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("tiepNhanLuc").descending()));
    }

    @GetMapping("/yeu-cau/theo-sdt")
    @PreAuthorize(XEM)
    public List<YeuCauResponse> theoSdt(@RequestParam String sdt) { return service.theoSdt(sdt); }

    @GetMapping("/yeu-cau/ma/{maSoTiepNhan}")
    @PreAuthorize(XEM)
    public YeuCauResponse theoMa(@PathVariable String maSoTiepNhan) { return service.theoMa(maSoTiepNhan); }

    @GetMapping("/yeu-cau/{id}")
    @PreAuthorize(XEM)
    public YeuCauResponse chiTiet(@PathVariable Integer id) { return service.chiTiet(id); }

    /** Đổi trạng thái (tiep_nhan -> dang_xu_ly -> hoan_tat -> da_tra_khach | tu_choi) hoặc cập nhật ghi chú/ngày hẹn. */
    @PutMapping("/yeu-cau/{id}")
    @PreAuthorize("hasAuthority('BAO_HANH_TIEP_NHAN')")
    public YeuCauResponse capNhat(@PathVariable Integer id, @Valid @RequestBody CapNhatYeuCauRequest r,
                                  HttpServletRequest http) {
        return service.capNhat(id, r, http.getRemoteAddr());
    }
}