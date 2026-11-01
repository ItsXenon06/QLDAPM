package com.xenon.qldapm.controller;

import com.xenon.qldapm.service.SePayService;
import com.xenon.qldapm.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SePayController {
    private final SePayService service;

    @GetMapping("/api/payments/sepay/settings")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public SePayService.Settings settings(){return service.settings();}

    @GetMapping("/api/pos/hoa-don/{id}/sepay")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public SePayService.Payment latest(@PathVariable Integer id){return service.latest(id);}

    @PostMapping("/api/pos/hoa-don/{id}/sepay")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public SePayService.Payment create(@PathVariable Integer id){return service.create(id);}

    @PostMapping("/api/pos/hoa-don/{id}/sepay/{attempt}/cancel")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public SePayService.Payment cancel(@PathVariable Integer id,@PathVariable Integer attempt){return service.cancel(id,attempt);}

    @PostMapping("/api/pos/hoa-don/{id}/sepay/{attempt}/retry")
    @PreAuthorize("hasAuthority('POS_BAN_HANG')")
    public SePayService.Payment retry(@PathVariable Integer id,@PathVariable Integer attempt){return service.retry(id,attempt);}

    @PostMapping(value="/api/payments/sepay/webhook",consumes="application/json")
    public Map<String,Boolean> receive(HttpServletRequest request) throws IOException {
        byte[] body=request.getInputStream().readNBytes(32769);
        if(body.length>32768) throw BusinessException.badRequest("SEPAY_PAYLOAD","Thông báo quá lớn.");
        service.receive(body,request.getHeader("X-SePay-Timestamp"),request.getHeader("X-SePay-Signature"));
        return Map.of("success",true);
    }
}
