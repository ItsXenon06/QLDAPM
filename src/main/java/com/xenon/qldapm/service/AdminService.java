package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.AdminDtos.*;
import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.repository.*;
import com.xenon.qldapm.exception.*;
import com.xenon.qldapm.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {
    private final JdbcTemplate jdbc;
    private final TaiKhoanRepository accounts;
    private final NhanVienRepository employees;
    private final VaiTroRepository roles;
    private final QuyenRepository permissions;
    private final QuayPOSRepository counters;
    private final PasswordEncoder encoder;
    private final SessionRevocationService revocation;
    private final NhatKyService audit;

    @Transactional(readOnly=true)
    public List<Map<String,Object>> users() {
        return jdbc.queryForList("""
            SELECT n.MaNhanVien AS maNhanVien,n.MaSoNhanVien AS maSoNhanVien,n.HoTen AS hoTen,
            n.Email AS email,n.SoDienThoai AS soDienThoai,n.MaVaiTro AS maVaiTro,
            v.TenVaiTro AS tenVaiTro,t.TenDangNhap AS tenDangNhap,t.TrangThai AS trangThai,
            t.DangNhapLanCuoi AS dangNhapLanCuoi FROM NhanVien n
            JOIN TaiKhoan t ON t.MaNhanVien=n.MaNhanVien JOIN VaiTro v ON v.MaVaiTro=n.MaVaiTro
            WHERE n.NgayXoa IS NULL AND t.NgayXoa IS NULL ORDER BY n.MaNhanVien
            """);
    }

    public void saveUser(Integer id, UserRequest r, String ip) {
        adminLock();
        if (!Set.of("hoat_dong","bi_khoa","vo_hieu").contains(r.trangThai()))
            throw BusinessException.badRequest("TRANG_THAI_SAI","Trạng thái tài khoản không hợp lệ");
        VaiTro role=roles.findById(r.maVaiTro()).filter(v -> Boolean.TRUE.equals(v.getTrangThai()))
            .orElseThrow(() -> new NotFoundException("Vai trò không hoạt động"));
        Set<String> currentPermissions=new HashSet<>();
        CurrentUser.require().getAuthorities().forEach(a -> currentPermissions.add(a.getAuthority()));
        if(!currentPermissions.contains("HE_THONG_PHAN_QUYEN") &&
                role.getQuyens().stream().anyMatch(p -> !currentPermissions.contains(p.getMaCode())))
            throw new org.springframework.security.access.AccessDeniedException("Không được cấp quyền cao hơn quyền của bạn");
        NhanVien employee=id==null?new NhanVien():employees.findById(id)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        TaiKhoan account=id==null?new TaiKhoan():accounts.findByNhanVien_MaNhanVienAndNgayXoaIsNull(id)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));
        if (id!=null && id.equals(CurrentUser.require().getMaNhanVien()) &&
                (!role.getMaVaiTro().equals(employee.getVaiTro().getMaVaiTro()) || !"hoat_dong".equals(r.trangThai())))
            throw BusinessException.conflict("TU_KHOA_TAI_KHOAN","Không thể khóa hoặc đổi vai trò của chính bạn");
        if (id==null && r.matKhau()==null) throw BusinessException.badRequest("THIEU_MAT_KHAU","Cần mật khẩu cho tài khoản mới");
        employee.setMaSoNhanVien(r.maSoNhanVien().trim()); employee.setHoTen(r.hoTen().trim());
        employee.setEmail(blank(r.email())); employee.setSoDienThoai(blank(r.soDienThoai())); employee.setVaiTro(role);
        employee=employees.save(employee);
        account.setNhanVien(employee); account.setTenDangNhap(r.tenDangNhap().trim()); account.setTrangThai(r.trangThai());
        account.setSoLanDangNhapSai(0); account.setKhoaDen(null);
        if(r.matKhau()!=null) {account.setMatKhauHash(encoder.encode(r.matKhau())); account.setDoiMatKhauLuc(LocalDateTime.now());}
        accounts.saveAndFlush(account);
        if(id!=null) revocation.thuHoi(id);
        audit.ghi(CurrentUser.require().getMaNhanVien(),"luu_nguoi_dung","TaiKhoan",account.getMaTaiKhoan().toString(),null,r.tenDangNhap()+": "+r.trangThai(),ip);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> roles() {
        return roles.findAll().stream().map(role -> {
            Map<String,Object> out=new LinkedHashMap<>(); out.put("maVaiTro",role.getMaVaiTro());
            out.put("tenVaiTro",role.getTenVaiTro()); out.put("moTa",role.getMoTa()); out.put("trangThai",role.getTrangThai());
            out.put("maQuyens",role.getQuyens().stream().map(Quyen::getMaQuyen).sorted().toList()); return out;
        }).toList();
    }

    @Transactional(readOnly=true)
    public List<Quyen> permissions() {return permissions.findAll();}

    public void saveRole(Integer id, RoleRequest r, String ip) {
        adminLock();
        VaiTro role=id==null?new VaiTro():roles.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));
        if("Quản trị hệ thống".equals(role.getTenVaiTro()) || "Quản trị hệ thống".equals(r.tenVaiTro().trim()))
            throw BusinessException.conflict("VAI_TRO_HE_THONG","Vai trò quản trị hệ thống được bảo vệ; tạo vai trò khác để tùy chỉnh");
        if(id!=null && jdbc.queryForObject("SELECT COUNT(*) FROM NhanVien WHERE MaNhanVien=? AND MaVaiTro=?",Integer.class,CurrentUser.require().getMaNhanVien(),id)>0)
            throw BusinessException.conflict("VAI_TRO_CUA_BAN","Không sửa vai trò hiện tại của chính bạn");
        List<Quyen> granted=permissions.findAllById(r.maQuyens());
        if(granted.size()!=r.maQuyens().size()) throw BusinessException.badRequest("QUYEN_SAI","Có quyền không tồn tại");
        role.setTenVaiTro(r.tenVaiTro().trim()); role.setMoTa(blank(r.moTa())); role.setTrangThai(r.trangThai());
        role.getQuyens().clear(); role.getQuyens().addAll(granted); role=roles.saveAndFlush(role);
        for(Integer employee:jdbc.queryForList("SELECT MaNhanVien FROM NhanVien WHERE MaVaiTro=?",Integer.class,role.getMaVaiTro())) revocation.thuHoi(employee);
        audit.ghi(CurrentUser.require().getMaNhanVien(),"luu_vai_tro","VaiTro",role.getMaVaiTro().toString(),null,r.tenVaiTro(),ip);
    }

    @Transactional(readOnly=true)
    public List<QuayPOS> counters(boolean all) {return counters.findAll().stream().filter(c -> all || "hoat_dong".equals(c.getTrangThai())).toList();}

    public void saveCounter(Integer id, CounterRequest r, String ip) {
        adminLock();
        if(!Set.of("hoat_dong","bao_tri","ngung_hoat_dong").contains(r.trangThai())) throw BusinessException.badRequest("TRANG_THAI_SAI","Trạng thái quầy không hợp lệ");
        QuayPOS c=id==null?new QuayPOS():counters.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Không tìm thấy quầy"));
        if(id!=null && !"hoat_dong".equals(r.trangThai()) && jdbc.queryForObject("SELECT COUNT(*) FROM HoaDon WHERE MaQuay=? AND TrangThai IN (N'dang_lap',N'tam_giu')",Integer.class,id)>0)
            throw BusinessException.conflict("QUAY_CO_HOA_DON","Hãy xử lý các hóa đơn nháp trước khi đóng quầy");
        c.setMaQuayCode(r.maQuayCode().trim()); c.setTenQuay(r.tenQuay().trim()); c.setViTri(blank(r.viTri()));
        c.setDiaChiIP(blank(r.diaChiIP())); c.setTenMayQuet(blank(r.tenMayQuet())); c.setTenMayIn(blank(r.tenMayIn())); c.setTrangThai(r.trangThai());
        c=counters.saveAndFlush(c);
        audit.ghi(CurrentUser.require().getMaNhanVien(),"luu_quay_pos","QuayPOS",c.getMaQuay().toString(),null,r.tenQuay()+": "+r.trangThai(),ip);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> logs(int page) {
        return jdbc.queryForList("""
            SELECT k.MaNhatKy AS maNhatKy,k.ThoiGian AS thoiGian,n.HoTen AS hoTen,k.HanhDong AS hanhDong,
            k.BangTacDong AS bangTacDong,k.MaBanGhi AS maBanGhi,k.GiaTriCu AS giaTriCu,k.GiaTriMoi AS giaTriMoi
            FROM NhatKyHeThong k LEFT JOIN NhanVien n ON n.MaNhanVien=k.MaNhanVien
            ORDER BY k.MaNhatKy DESC OFFSET ? ROWS FETCH NEXT 50 ROWS ONLY
            """,Math.max(0,page)*50);
    }
    private void adminLock() {
        Integer result=jdbc.queryForObject("IF @@TRANCOUNT = 0 BEGIN TRANSACTION; DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='qldapm:admin',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000; SELECT @r;",Integer.class);
        if(result==null || result<0) throw BusinessException.conflict("HE_THONG_BAN","Quản trị đang bận; vui lòng thử lại");
    }
    private static String blank(String value) {return value==null || value.isBlank()?null:value.trim();}
}
