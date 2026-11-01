package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.BaoCaoDtos.BaoCaoBang;
import com.xenon.qldapm.dto.BaoCaoDtos.BaoCaoThamSo;
import com.xenon.qldapm.dto.BaoCaoDtos.TongQuanResponse;
import com.xenon.qldapm.security.CurrentUser;
import com.xenon.qldapm.service.BaoCaoService;
import com.xenon.qldapm.service.BaoCaoXuatService;
import com.xenon.qldapm.service.NhatKyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Báo cáo - thống kê. loai: doanh-thu | doanh-thu-quay | doanh-thu-thu-ngan | doanh-thu-nganh-hang |
 * doanh-thu-san-pham | ton-kho | ton-kho-serial | ton-lau | chenh-lech-ton.
 */
@RestController
@RequestMapping("/api/bao-cao")
@RequiredArgsConstructor
public class BaoCaoController {

    private final BaoCaoService service;
    private final BaoCaoXuatService xuatService;
    private final NhatKyService nhatKyService;

    @GetMapping("/tong-quan")
    @PreAuthorize("hasAuthority('BAO_CAO_XEM')")
    public TongQuanResponse tongQuan() { return service.tongQuan(); }

    @GetMapping("/{loai}")
    @PreAuthorize("hasAuthority('BAO_CAO_XEM')")
    public BaoCaoBang xem(@PathVariable String loai, @ModelAttribute BaoCaoThamSo p) {
        return service.tao(loai, p);
    }

    @GetMapping("/{loai}/xuat")
    @PreAuthorize("hasAuthority('BAO_CAO_XEM') and hasAuthority('BAO_CAO_XUAT')")
    public ResponseEntity<byte[]> xuat(@PathVariable String loai, @RequestParam(defaultValue = "xlsx") String dinhDang,
                                       @ModelAttribute BaoCaoThamSo p, HttpServletRequest http) {
        var tep = xuatService.xuat(loai, service.tao(loai, p), dinhDang);
        nhatKyService.ghi(CurrentUser.require().getMaNhanVien(), "xuat_bao_cao", "BaoCao", loai, null,
                dinhDang, http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(tep.ten(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(tep.contentType()))
                .body(tep.data());
    }
}