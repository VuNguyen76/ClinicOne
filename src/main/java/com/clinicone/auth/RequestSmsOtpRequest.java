package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RequestSmsOtpRequest(
        @NotBlank(message = "Số điện thoại là thông tin bắt buộc")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0")
        String phone,
        @NotNull(message = "Mục đích OTP là thông tin bắt buộc")
        OtpPurpose purpose
) {
}
