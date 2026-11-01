package com.xenon.qldapm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class VoucherDtos {

    private VoucherDtos() {}

    public record HuyVoucherRequest(@NotBlank @Size(max = 255) String lyDo) {}
}