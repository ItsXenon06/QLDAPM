package com.xenon.qldapm.service;

import com.xenon.qldapm.config.SePayConfig;
import com.xenon.qldapm.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Called only while holding the invoice row lock. */
@Service
@RequiredArgsConstructor
public class SePayGuard {
    private final SePayConfig config; // ensures schema exists before the guard is used
    private final JdbcTemplate jdbc;

    public void editable(Integer id) {
        Integer n=jdbc.queryForObject("""
            SELECT COUNT(*) FROM SePayAttempt a WHERE a.InvoiceId=? AND
            (a.Status='REVIEW' OR (a.Status='PENDING' AND a.ExpiresAt>SYSDATETIME())
             OR EXISTS(SELECT 1 FROM SePayEvent e WHERE e.Reference=a.Reference AND e.Status='RECEIVED'))
            """,Integer.class,id);
        if(n!=null && n>0) throw BusinessException.conflict("SEPAY_DANG_CHO",
                "Hóa đơn đang chờ QR hoặc cần đối soát. Mở Thanh toán để kiểm tra / hủy QR trước khi sửa.");
    }

    public boolean hasHistory(Integer id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM SePayAttempt WHERE InvoiceId=?",Integer.class,id)>0;
    }
}
