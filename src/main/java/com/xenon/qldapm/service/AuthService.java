package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.DoiMatKhauRequest;
import com.xenon.qldapm.dto.LoginRequest;
import com.xenon.qldapm.dto.UserInfoResponse;
import com.xenon.qldapm.entity.TaiKhoan;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.TaiKhoanRepository;
import com.xenon.qldapm.security.TaiKhoanUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final TaiKhoanRepository taiKhoanRepository;
    private final PasswordEncoder passwordEncoder;
    private final NhatKyService nhatKyService;

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    /** Không @Transactional: mỗi bước đếm sai/ghi nhật ký tự có transaction riêng và vẫn commit khi đăng nhập thất bại. */
    public UserInfoResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String username = req.tenDangNhap().trim();
        String ip = request.getRemoteAddr();
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, req.matKhau()));
        } catch (BadCredentialsException e) {
            loginAttemptService.onFailure(username, ip);
            throw e;
        }
        loginAttemptService.onSuccess(username, ip);

        if (request.getSession(false) != null) {
            request.changeSessionId();          // chống session fixation
        }
        SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        contextRepository.saveContext(context, request, response);

        return UserInfoResponse.from((TaiKhoanUserDetails) auth.getPrincipal());
    }

    @Transactional
    public void doiMatKhau(TaiKhoanUserDetails user, DoiMatKhauRequest req, String ip) {
        TaiKhoan tk = taiKhoanRepository.findById(user.getMaTaiKhoan())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));
        if (!passwordEncoder.matches(req.matKhauCu(), tk.getMatKhauHash())) {
            throw BusinessException.badRequest("MAT_KHAU_CU_SAI", "Mật khẩu hiện tại không đúng");
        }
        if (req.matKhauMoi().equals(req.matKhauCu())) {
            throw BusinessException.badRequest("MAT_KHAU_TRUNG", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        tk.setMatKhauHash(passwordEncoder.encode(req.matKhauMoi()));
        tk.setDoiMatKhauLuc(LocalDateTime.now());
        nhatKyService.ghi(user.getMaNhanVien(), "doi_mat_khau", "TaiKhoan",
                String.valueOf(tk.getMaTaiKhoan()), null, null, ip);
    }
}
