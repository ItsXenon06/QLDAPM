package com.xenon.qldapm.job;

import com.xenon.qldapm.service.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Giải phóng Serial bị giữ bởi hóa đơn nháp bị bỏ quên. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PosDraftCleanupJob {

    private final PosService posService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void run() {
        try {
            int n = posService.donNhapQuaHan();
            if (n > 0) log.info("Đã hủy {} hóa đơn nháp quá hạn", n);
        } catch (Exception e) {
            log.warn("Dọn hóa đơn nháp thất bại: {}", e.getMessage());
        }
    }
}