package com.xenon.qldapm.seed;

import com.xenon.qldapm.entity.*;
import com.xenon.qldapm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Khởi tạo dữ liệu nền (idempotent): quyền, vai trò, tài khoản admin lần đầu.
 * - Quyền mới thêm vào code sẽ tự được tạo và tự cấp cho vai trò "Quản trị hệ thống".
 * - Các vai trò khác chỉ được gán quyền mặc định lúc tạo lần đầu (không ghi đè chỉnh sửa của quản trị).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final QuyenRepository quyenRepository;
    private final VaiTroRepository vaiTroRepository;
    private final NhanVienRepository nhanVienRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:Admin@123}")
    private String adminPassword;

    private record P(String code, String ten, String nhom) {}
    private record Role(String ten, String moTa, List<String> codes) {}   // codes == null -> tất cả quyền

    private static final List<P> PERMISSIONS = List.of(
            new P("SAN_PHAM_XEM", "Xem danh mục sản phẩm", "kho"),
            new P("SAN_PHAM_QUAN_LY", "Quản lý sản phẩm, danh mục, thương hiệu", "kho"),
            new P("GIA_QUAN_LY", "Thay đổi giá niêm yết / giá sàn", "kho"),
            new P("NHA_CUNG_CAP_QUAN_LY", "Quản lý nhà cung cấp", "kho"),
            new P("KHO_XEM_TON", "Xem tồn kho và Serial/IMEI", "kho"),
            new P("KHO_NHAP", "Lập phiếu và nhận hàng nhập kho", "kho"),
            new P("KHO_KIEM_KE", "Kiểm kê và điều chỉnh tồn kho", "kho"),
            new P("POS_BAN_HANG", "Bán hàng tại quầy POS", "pos"),
            new P("POS_XEM_HOA_DON", "Xem/tra cứu hóa đơn", "pos"),
            new P("POS_TRA_HANG", "Lập phiếu trả hàng", "pos"),
            new P("POS_DUYET_TRA_HANG", "Duyệt phiếu trả hàng", "pos"),
            new P("POS_HUY_HOA_DON", "Hủy hóa đơn", "pos"),
            new P("KHUYEN_MAI_XEM", "Xem chương trình khuyến mãi", "khuyen_mai"),
            new P("KHUYEN_MAI_QUAN_LY", "Quản lý chương trình khuyến mãi, voucher", "khuyen_mai"),
            new P("BAO_HANH_TRA_CUU", "Tra cứu bảo hành theo Serial/IMEI/SĐT", "bao_hanh"),
            new P("BAO_HANH_TIEP_NHAN", "Tiếp nhận yêu cầu bảo hành", "bao_hanh"),
            new P("BAO_HANH_CHINH_SACH", "Quản lý chính sách bảo hành", "bao_hanh"),
            new P("BAO_CAO_XEM", "Xem báo cáo - thống kê", "bao_cao"),
            new P("BAO_CAO_XUAT", "Xuất báo cáo Excel/PDF", "bao_cao"),
            new P("HE_THONG_NGUOI_DUNG", "Quản lý nhân viên và tài khoản", "he_thong"),
            new P("HE_THONG_PHAN_QUYEN", "Quản lý vai trò và phân quyền", "he_thong"),
            new P("HE_THONG_QUAY_POS", "Quản lý quầy POS", "he_thong"),
            new P("HE_THONG_NHAT_KY", "Xem nhật ký hệ thống", "he_thong"));

    private static final String ADMIN_ROLE = "Quản trị hệ thống";

    private static final List<Role> ROLES = List.of(
            new Role(ADMIN_ROLE, "Toàn quyền cấu hình hệ thống", null),
            new Role("Ban quản lý siêu thị", "Báo cáo, chính sách giá, khuyến mãi, duyệt trả/hủy hóa đơn", List.of(
                    "SAN_PHAM_XEM", "GIA_QUAN_LY", "KHO_XEM_TON", "POS_XEM_HOA_DON", "POS_DUYET_TRA_HANG",
                    "POS_HUY_HOA_DON", "KHUYEN_MAI_XEM", "KHUYEN_MAI_QUAN_LY", "BAO_HANH_TRA_CUU",
                    "BAO_HANH_CHINH_SACH", "BAO_CAO_XEM", "BAO_CAO_XUAT", "HE_THONG_NHAT_KY")),
            new Role("Nhân viên quản lý kho", "Danh mục, nhập kho, kiểm kê, Serial/IMEI", List.of(
                    "SAN_PHAM_XEM", "SAN_PHAM_QUAN_LY", "NHA_CUNG_CAP_QUAN_LY", "KHO_XEM_TON", "KHO_NHAP",
                    "KHO_KIEM_KE", "BAO_CAO_XEM")),
            new Role("Nhân viên thu ngân", "Bán hàng, trả hàng tại quầy POS", List.of(
                    "SAN_PHAM_XEM", "KHO_XEM_TON", "POS_BAN_HANG", "POS_XEM_HOA_DON", "POS_TRA_HANG",
                    "KHUYEN_MAI_XEM", "BAO_HANH_TRA_CUU")),
            new Role("Nhân viên CSKH / bảo hành", "Tra cứu lịch sử mua, tiếp nhận bảo hành", List.of(
                    "SAN_PHAM_XEM", "POS_XEM_HOA_DON", "BAO_HANH_TRA_CUU", "BAO_HANH_TIEP_NHAN")));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1. Quyền
        Map<String, Quyen> quyens = new HashMap<>();
        quyenRepository.findAll().forEach(q -> quyens.put(q.getMaCode(), q));
        for (P p : PERMISSIONS) {
            quyens.computeIfAbsent(p.code(), c -> {
                Quyen q = new Quyen();
                q.setMaCode(c);
                q.setTenQuyen(p.ten());
                q.setNhom(p.nhom());
                return quyenRepository.save(q);
            });
        }

        // 2. Vai trò
        VaiTro admin = null;
        for (Role r : ROLES) {
            Optional<VaiTro> existing = vaiTroRepository.findByTenVaiTro(r.ten());
            boolean isAdmin = r.codes() == null;
            VaiTro vt = existing.orElseGet(() -> {
                VaiTro n = new VaiTro();
                n.setTenVaiTro(r.ten());
                n.setMoTa(r.moTa());
                if (!isAdmin) {
                    r.codes().forEach(c -> n.getQuyens().add(quyens.get(c)));
                }
                return n;
            });
            if (isAdmin) {
                vt.getQuyens().addAll(quyens.values());      // admin luôn có đủ quyền
                admin = vt;
            }
            vaiTroRepository.save(vt);
        }

        // 3. Tài khoản admin lần đầu
        if (taiKhoanRepository.count() == 0) {
            NhanVien nv = new NhanVien();
            nv.setMaSoNhanVien("NV0001");
            nv.setHoTen("Quản trị hệ thống");
            nv.setVaiTro(admin);
            nv = nhanVienRepository.save(nv);

            TaiKhoan tk = new TaiKhoan();
            tk.setNhanVien(nv);
            tk.setTenDangNhap("admin");
            tk.setMatKhauHash(passwordEncoder.encode(adminPassword));
            taiKhoanRepository.save(tk);
            log.warn("Đã tạo tài khoản mặc định 'admin'. Hãy ĐỔI MẬT KHẨU ngay (PUT /api/auth/mat-khau).");
        }
    }
}
