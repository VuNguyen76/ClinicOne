package com.clinicone.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestOtpRequest(
        @NotBlank(message = "Email là thông tin bắt buộc")
        @Email(message = "Email không đúng định dạng")
        String email,
        @NotNull(message = "Mục đích OTP là thông tin bắt buộc")
        OtpPurpose purpose
) {
}
