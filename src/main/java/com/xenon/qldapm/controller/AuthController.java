package com.xenon.qldapm.controller;

import com.xenon.qldapm.dto.DoiMatKhauRequest;
import com.xenon.qldapm.dto.LoginRequest;
import com.xenon.qldapm.dto.UserInfoResponse;
import com.xenon.qldapm.security.CurrentUser;
import com.xenon.qldapm.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Đăng nhập; session cookie JSESSIONID được trả về. Đăng xuất: POST /api/auth/logout (có CSRF header). */
    @PostMapping("/login")
    public UserInfoResponse login(@Valid @RequestBody LoginRequest req,
                                  HttpServletRequest request, HttpServletResponse response) {
        return authService.login(req, request, response);
    }

    @GetMapping("/me")
    public UserInfoResponse me() {
        return UserInfoResponse.from(CurrentUser.require());
    }

    @PutMapping("/mat-khau")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void doiMatKhau(@Valid @RequestBody DoiMatKhauRequest req, HttpServletRequest request) {
        authService.doiMatKhau(CurrentUser.require(), req, request.getRemoteAddr());
    }
}
