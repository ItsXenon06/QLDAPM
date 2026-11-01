package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.BaoCaoDtos.BaoCaoBang;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/** Ghi BaoCaoBang ra .xlsx (số giữ nguyên kiểu số để kế toán còn tính tiếp). */
final class BaoCaoExcelWriter {

    private BaoCaoExcelWriter() {}

    static byte[] ghi(BaoCaoBang b) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Bao cao");
            DataFormat df = wb.createDataFormat();
            Font dam = wb.createFont();
            dam.setBold(true);
            Font to = wb.createFont();
            to.setBold(true);
            to.setFontHeightInPoints((short) 14);

            CellStyle sTieuDe = wb.createCellStyle();
            sTieuDe.setFont(to);
            CellStyle sDau = vien(wb);
            sDau.setFont(dam);
            sDau.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            sDau.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sDau.setWrapText(true);
            CellStyle[][] kieu = new CellStyle[2][3];            // [thường|tổng][chữ|số nguyên|số thập phân]
            for (int t = 0; t < 2; t++) {
                for (int i = 0; i < 3; i++) {
                    kieu[t][i] = vien(wb);
                    if (t == 1) kieu[t][i].setFont(dam);
                }
                kieu[t][1].setDataFormat(df.getFormat("#,##0"));
                kieu[t][2].setDataFormat(df.getFormat("#,##0.00"));
            }

            int n = b.cot().size();
            int[] rong = new int[n];
            int r = 0;
            Cell c = sh.createRow(r++).createCell(0);
            c.setCellValue(b.tieuDe());
            c.setCellStyle(sTieuDe);
            if (b.moTa() != null) sh.createRow(r++).createCell(0).setCellValue(b.moTa());
            r++;
            Row h = sh.createRow(r++);
            for (int i = 0; i < n; i++) {
                Cell hc = h.createCell(i);
                hc.setCellValue(b.cot().get(i));
                hc.setCellStyle(sDau);
                rong[i] = b.cot().get(i).length();
            }
            sh.createFreezePane(0, r);
            for (List<Object> d : b.dong()) ghiDong(sh.createRow(r++), d, kieu[0], rong);
            if (b.tong() != null) ghiDong(sh.createRow(r), b.tong(), kieu[1], rong);
            for (int i = 0; i < n; i++) sh.setColumnWidth(i, Math.min(Math.max(rong[i] + 2, 10), 50) * 256);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void ghiDong(Row row, List<Object> d, CellStyle[] kieu, int[] rong) {
        for (int i = 0; i < d.size(); i++) {
            Object o = d.get(i);
            Cell c = row.createCell(i);
            if (o instanceof Number num) {
                BigDecimal bd = new BigDecimal(num.toString());
                c.setCellValue(bd.doubleValue());
                c.setCellStyle(bd.stripTrailingZeros().scale() <= 0 ? kieu[1] : kieu[2]);
                rong[i] = Math.max(rong[i], bd.toPlainString().length() + 3);
            } else {
                String s = o == null ? "" : String.valueOf(o);
                c.setCellValue(s);                     // luôn là ô chuỗi: không bao giờ bị hiểu thành công thức
                c.setCellStyle(kieu[0]);
                rong[i] = Math.max(rong[i], s.length());
            }
        }
    }

    private static CellStyle vien(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }
}