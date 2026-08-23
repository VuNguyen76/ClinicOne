package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import com.clinicone.validation.VietnamesePhone;

public record CheckPhoneRequest(
        @NotBlank(message = "Số điện thoại là thông tin bắt buộc")
        @VietnamesePhone
        String phone
) {
}
