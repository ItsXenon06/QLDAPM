package com.xenon.qldapm.service;

import com.xenon.qldapm.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** SQL Server transaction locks coordinate all app instances, including empty-table creation. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryCountGuard {
    private final JdbcTemplate jdbc;

    /** Acquire before any invoice/receipt/serial locks in a stock-changing transaction. */
    public void stockMovement() {
        lock("qldapm:stocktake", "Shared");
    }

    /** Snapshot creation and count edits exclude in-flight stock movements until commit. */
    public void stocktake() {
        lock("qldapm:stocktake", "Exclusive");
    }

    public void cashier(Integer employeeId) {
        lock("qldapm:cashier:" + employeeId, "Exclusive");
    }

    public void checkProduct(Integer productId, String documentType, Integer documentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ChiTietKiemKe c
                JOIN PhieuKiemKe p ON p.MaPhieuKiemKe = c.MaPhieuKiemKe
                WHERE p.TrangThai = N'dang_kiem' AND c.MaSanPham = ?
                AND (? <> N'phieu_kiem_ke' OR p.MaPhieuKiemKe <> ?)
                """, Integer.class, productId, documentType == null ? "" : documentType,
                documentId == null ? -1 : documentId);
        if (count != null && count > 0) {
            throw BusinessException.conflict("SAN_PHAM_DANG_KIEM_KE",
                    "Sản phẩm #" + productId + " đang kiểm kê; hãy hoàn thành hoặc hủy phiếu trước khi thay đổi kho");
        }
    }

    private void lock(String resource, String mode) {
        Integer result = jdbc.queryForObject("""
                IF @@TRANCOUNT = 0 BEGIN TRANSACTION; DECLARE @result int;
                EXEC @result = sys.sp_getapplock @Resource = ?, @LockMode = ?,
                    @LockOwner = 'Transaction', @LockTimeout = 5000;
                SELECT @result;
                """, Integer.class, resource, mode);
        if (result == null || result < 0) {
            throw BusinessException.conflict("NGHIEP_VU_DANG_BAN",
                    "Có giao dịch kho hoặc thu ngân đang xử lý; vui lòng thử lại");
        }
    }
}
