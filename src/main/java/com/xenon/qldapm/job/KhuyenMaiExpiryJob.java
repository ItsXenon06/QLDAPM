package com.xenon.qldapm.job;

import com.xenon.qldapm.service.KhuyenMaiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Đánh dấu khuyến mãi và voucher quá hạn (engine vẫn tự kiểm tra thời hạn, job chỉ giữ dữ liệu/báo cáo đúng). */
@Slf4j
@Component
@RequiredArgsConstructor
public class KhuyenMaiExpiryJob {

    private final KhuyenMaiService service;

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void run() {
        try {
            int[] n = service.danhDauHetHan();
            if (n[0] > 0 || n[1] > 0) log.info("Hết hạn: {} khuyến mãi, {} voucher", n[0], n[1]);
        } catch (Exception e) {
            log.warn("Đánh dấu hết hạn khuyến mãi/voucher thất bại: {}", e.getMessage());
        }
    }
}