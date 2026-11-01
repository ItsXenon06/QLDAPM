package com.xenon.qldapm.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.xenon.qldapm.dto.BaoCaoDtos.BaoCaoBang;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ghi BaoCaoBang ra PDF. Tiếng Việt cần font Unicode: thử app.report.pdf-font, classpath fonts/report.ttf,
 * rồi các font hệ thống phổ biến. Không có font nào thì hạ cấp về Helvetica và bỏ dấu (vẫn đọc được).
 */
final class BaoCaoPdfWriter {

    private record DungFont(BaseFont font, boolean unicode) {}

    private BaoCaoPdfWriter() {}

    static byte[] ghi(BaoCaoBang b, String duongDanFont) throws Exception {
        DungFont ff = taiFont(duongDanFont);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(b.cot().size() > 6 ? PageSize.A4.rotate() : PageSize.A4, 28, 28, 32, 32);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font fTieu = new Font(ff.font(), 15, Font.BOLD);
        Font fThuong = new Font(ff.font(), 9, Font.NORMAL);
        Font fDam = new Font(ff.font(), 9, Font.BOLD);
        Font fMo = new Font(ff.font(), 8, Font.ITALIC, Color.GRAY);

        doc.add(new Paragraph(chu(b.tieuDe(), ff), fTieu));
        if (b.moTa() != null) doc.add(new Paragraph(chu(b.moTa(), ff), fThuong));
        doc.add(new Paragraph("Xuất lúc " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), fMo));

        int n = b.cot().size();
        PdfPTable t = new PdfPTable(n);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.setHeaderRows(1);
        float[] w = new float[n];
        for (int i = 0; i < n; i++) w[i] = Math.min(Math.max(b.cot().get(i).length(), 6), 40);
        for (List<Object> d : b.dong()) {
            for (int i = 0; i < n && i < d.size(); i++) {
                if (d.get(i) != null) w[i] = Math.max(w[i], Math.min(String.valueOf(d.get(i)).length(), 40));
            }
        }
        t.setWidths(w);
        for (String c : b.cot()) {
            PdfPCell h = new PdfPCell(new Phrase(chu(c, ff), fDam));
            h.setBackgroundColor(new Color(0xE5, 0xE7, 0xEB));
            h.setPadding(4);
            t.addCell(h);
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("vi", "VN"));
        nf.setMaximumFractionDigits(2);
        for (List<Object> d : b.dong()) them(t, d, nf, fThuong, ff);
        if (b.tong() != null) them(t, b.tong(), nf, fDam, ff);
        doc.add(t);
        doc.close();
        return out.toByteArray();
    }

    private static void them(PdfPTable t, List<Object> d, NumberFormat nf, Font f, DungFont ff) {
        for (Object o : d) {
            boolean so = o instanceof Number;
            String s = o == null ? "" : so ? nf.format(o) : String.valueOf(o);
            PdfPCell c = new PdfPCell(new Phrase(chu(s, ff), f));
            c.setPadding(4);
            if (so) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(c);
        }
    }

    private static String chu(String s, DungFont ff) {
        if (s == null || ff.unicode()) return s == null ? "" : s;
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
    }

    private static DungFont taiFont(String duongDanCauHinh) throws Exception {
        List<String> ungVien = new ArrayList<>();
        if (duongDanCauHinh != null && !duongDanCauHinh.isBlank()) ungVien.add(duongDanCauHinh);
        ungVien.add("C:/Windows/Fonts/arial.ttf");
        ungVien.add("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        ungVien.add("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf");
        ungVien.add("/System/Library/Fonts/Supplemental/Arial.ttf");

        ClassPathResource cp = new ClassPathResource("fonts/report.ttf");
        if (cp.exists()) {
            try (InputStream in = cp.getInputStream()) {
                return new DungFont(BaseFont.createFont("report.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                        in.readAllBytes(), null), true);
            }
        }
        for (String p : ungVien) {
            if (Files.isReadable(Path.of(p))) {
                return new DungFont(BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED), true);
            }
        }
        return new DungFont(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), false);
    }
}