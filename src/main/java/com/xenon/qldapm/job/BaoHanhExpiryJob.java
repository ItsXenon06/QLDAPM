package com.xenon.qldapm.job;

import com.xenon.qldapm.service.BaoHanhService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Đánh dấu bảo hành quá hạn (tra cứu vẫn tự tính theo ngày kết thúc, job giữ dữ liệu/báo cáo đúng). */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaoHanhExpiryJob {

    private final BaoHanhService service;

    @Scheduled(fixedDelay = 600_000, initialDelay = 45_000)
    public void run() {
        try {
            int n = service.danhDauHetHan();
            if (n > 0) log.info("Đã đánh dấu hết hạn {} bảo hành", n);
        } catch (Exception e) {
            log.warn("Đánh dấu hết hạn bảo hành thất bại: {}", e.getMessage());
        }
    }
}