package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.BaoCaoDtos.BaoCaoBang;
import com.xenon.qldapm.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Xuất báo cáo ra Excel/PDF phục vụ kế toán và trình ban quản lý. */
@Service
public class BaoCaoXuatService {

    public record Tep(String ten, String contentType, byte[] data) {}

    @Value("${app.report.pdf-font:}")
    private String fontPdf;

    public Tep xuat(String loai, BaoCaoBang b, String dinhDang) {
        String dd = dinhDang == null ? "xlsx" : dinhDang.trim().toLowerCase();
        String ten = "bao-cao-" + loai + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        try {
            return switch (dd) {
                case "xlsx" -> new Tep(ten + ".xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", BaoCaoExcelWriter.ghi(b));
                case "pdf" -> new Tep(ten + ".pdf", "application/pdf", BaoCaoPdfWriter.ghi(b, fontPdf));
                default -> throw BusinessException.badRequest("DINH_DANG_KHONG_HOP_LE", "Định dạng phải là xlsx hoặc pdf");
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Không xuất được báo cáo", e);
        }
    }
}