package com.xenon.qldapm.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Principal được lưu trong session nên đổi vai trò/khóa/xóa tài khoản không có hiệu lực ngay.
 * Service này ghi nhớ thời điểm "thu hồi" theo nhân viên; mọi session tạo TRƯỚC thời điểm đó bị hủy
 * ở request kế tiếp (xem SessionRevocationFilter) và người dùng phải đăng nhập lại.
 * Một máy chủ duy nhất cho 1 siêu thị nên giữ trong bộ nhớ là đủ.
 */
@Component
public class SessionRevocationService {

    private final ConcurrentMap<Integer, Long> moc = new ConcurrentHashMap<>();

    /** Thu hồi sau khi transaction hiện tại commit (nếu có) để không hủy session khi giao dịch bị rollback. */
    public void thuHoi(Integer maNhanVien) {
        if (maNhanVien == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { moc.put(maNhanVien, System.currentTimeMillis()); }
            });
        } else {
            moc.put(maNhanVien, System.currentTimeMillis());
        }
    }

    public boolean biThuHoi(Integer maNhanVien, long thoiDiemTaoSession) {
        Long m = moc.get(maNhanVien);
        return m != null && m > thoiDiemTaoSession;
    }
}