package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.clinicone.validation.VietnamesePhone;

public record RequestSmsOtpRequest(
        @NotBlank(message = "Số điện thoại là thông tin bắt buộc")
        @VietnamesePhone
        String phone,
        @NotNull(message = "Mục đích OTP là thông tin bắt buộc")
        OtpPurpose purpose
) {
}
