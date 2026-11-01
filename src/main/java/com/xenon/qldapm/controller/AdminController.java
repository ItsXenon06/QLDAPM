package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.AdminDtos.*;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/he-thong")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService service;
    @GetMapping("/nguoi-dung") @PreAuthorize("hasAuthority('HE_THONG_NGUOI_DUNG')")
    public List<Map<String,Object>> users() {return service.users();}
    @PostMapping("/nguoi-dung") @PreAuthorize("hasAuthority('HE_THONG_NGUOI_DUNG')")
    public void createUser(@Valid @RequestBody UserRequest r,HttpServletRequest h) {service.saveUser(null,r,h.getRemoteAddr());}
    @PutMapping("/nguoi-dung/{id}") @PreAuthorize("hasAuthority('HE_THONG_NGUOI_DUNG')")
    public void updateUser(@PathVariable Integer id,@Valid @RequestBody UserRequest r,HttpServletRequest h) {service.saveUser(id,r,h.getRemoteAddr());}
    @GetMapping("/vai-tro") @PreAuthorize("hasAnyAuthority('HE_THONG_PHAN_QUYEN','HE_THONG_NGUOI_DUNG')")
    public List<Map<String,Object>> roles() {return service.roles();}
    @GetMapping("/quyen") @PreAuthorize("hasAuthority('HE_THONG_PHAN_QUYEN')")
    public List<Quyen> permissions() {return service.permissions();}
    @PostMapping("/vai-tro") @PreAuthorize("hasAuthority('HE_THONG_PHAN_QUYEN')")
    public void createRole(@Valid @RequestBody RoleRequest r,HttpServletRequest h) {service.saveRole(null,r,h.getRemoteAddr());}
    @PutMapping("/vai-tro/{id}") @PreAuthorize("hasAuthority('HE_THONG_PHAN_QUYEN')")
    public void updateRole(@PathVariable Integer id,@Valid @RequestBody RoleRequest r,HttpServletRequest h) {service.saveRole(id,r,h.getRemoteAddr());}
    @GetMapping("/quay") @PreAuthorize("hasAnyAuthority('POS_BAN_HANG','HE_THONG_QUAY_POS','BAO_CAO_XEM')")
    public List<QuayPOS> counters(@RequestParam(defaultValue="false") boolean all) {return service.counters(all);}
    @PostMapping("/quay") @PreAuthorize("hasAuthority('HE_THONG_QUAY_POS')")
    public void createCounter(@Valid @RequestBody CounterRequest r,HttpServletRequest h) {service.saveCounter(null,r,h.getRemoteAddr());}
    @PutMapping("/quay/{id}") @PreAuthorize("hasAuthority('HE_THONG_QUAY_POS')")
    public void updateCounter(@PathVariable Integer id,@Valid @RequestBody CounterRequest r,HttpServletRequest h) {service.saveCounter(id,r,h.getRemoteAddr());}
    @GetMapping("/nhat-ky") @PreAuthorize("hasAuthority('HE_THONG_NHAT_KY')")
    public List<Map<String,Object>> logs(@RequestParam(defaultValue="0") int page) {return service.logs(page);}
}
