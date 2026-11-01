package com.xenon.qldapm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Hủy session của người dùng đã bị thu hồi quyền; request đó rơi về trạng thái chưa đăng nhập (401). */
public class SessionRevocationFilter extends OncePerRequestFilter {

    private final SessionRevocationService service;

    public SessionRevocationFilter(SessionRevocationService service) { this.service = service; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof TaiKhoanUserDetails u
                    && service.biThuHoi(u.getMaNhanVien(), session.getCreationTime())) {
                SecurityContextHolder.clearContext();
                try { session.invalidate(); } catch (IllegalStateException ignored) { }
            }
        }
        chain.doFilter(request, response);
    }
}