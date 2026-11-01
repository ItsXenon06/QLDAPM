package com.xenon.qldapm.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Định dạng phân trang ổn định cho API (không lộ cấu trúc nội bộ của Spring Data). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
