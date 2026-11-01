package com.xenon.qldapm.dto;

import java.math.BigDecimal;

/** DTO bổ sung cho phân hệ trả hàng (các DTO chính nằm trong PosDtos). */
public final class TraHangDtos {

    private TraHangDtos() {}

    /** Dòng hóa đơn còn có thể trả: giúp màn hình trả hàng hiển thị số lượng tối đa. */
    public record DongCoTheTraResponse(Integer maChiTietHD, String maSKU, String tenHang, String soSerial,
                                       Integer soLuongMua, Long soLuongDaTra, Long soLuongConTra,
                                       BigDecimal thanhTien) {}
}