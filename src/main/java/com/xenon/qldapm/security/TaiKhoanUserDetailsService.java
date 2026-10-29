package com.xenon.qldapm.security;

import com.xenon.qldapm.entity.NhanVien;
import com.xenon.qldapm.entity.TaiKhoan;
import com.xenon.qldapm.entity.VaiTro;
import com.xenon.qldapm.repository.TaiKhoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaiKhoanUserDetailsService implements UserDetailsService {

    private final TaiKhoanRepository taiKhoanRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        TaiKhoan tk = taiKhoanRepository.findForLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        NhanVien nv = tk.getNhanVien();
        VaiTro vt = nv.getVaiTro();

        List<GrantedAuthority> authorities = vt.getQuyens().stream()
                .<GrantedAuthority>map(q -> new SimpleGrantedAuthority(q.getMaCode()))
                .toList();

        boolean enabled = !"vo_hieu".equals(tk.getTrangThai())
                && !"da_nghi".equals(nv.getTrangThai())
                && Boolean.TRUE.equals(vt.getTrangThai());
        boolean khoaThuCong = "bi_khoa".equals(tk.getTrangThai()) || "tam_khoa".equals(nv.getTrangThai());

        return new TaiKhoanUserDetails(tk.getMaTaiKhoan(), nv.getMaNhanVien(), nv.getMaSoNhanVien(),
                nv.getHoTen(), vt.getTenVaiTro(), tk.getTenDangNhap(), tk.getMatKhauHash(),
                enabled, khoaThuCong, tk.getKhoaDen(), authorities);
    }
}
