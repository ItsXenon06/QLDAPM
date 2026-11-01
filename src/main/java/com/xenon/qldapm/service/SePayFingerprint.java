package com.xenon.qldapm.service;

import com.xenon.qldapm.dto.PosDtos.HoaDonResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;

/** Bind each payment to the exact quoted basket, including gifts and tax. */
public final class SePayFingerprint {
    private SePayFingerprint() {}
    public static String of(HoaDonResponse invoice) {
        StringBuilder text=new StringBuilder().append(invoice.maKhachHang()).append('|');
        invoice.dongs().stream().sorted(Comparator.comparing(d->d.maChiTiet())).forEach(d->text
            .append(d.maSanPham()).append(':').append(d.maSerial()).append(':').append(d.soLuong()).append(':')
            .append(d.donGia().stripTrailingZeros().toPlainString()).append(':')
            .append(d.giamGia().stripTrailingZeros().toPlainString()).append(':')
            .append(d.thueSuat().stripTrailingZeros().toPlainString()).append(':').append(d.laQuaTang()).append('|'));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
