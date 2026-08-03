package com.clinicone.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Email là thông tin bắt buộc")
        @Email(message = "Email không đúng định dạng")
        String email,
        @NotNull(message = "Mục đích OTP là thông tin bắt buộc")
        OtpPurpose purpose,
        @NotBlank(message = "Mã OTP là thông tin bắt buộc")
        @Pattern(regexp = "^\\d{6}$", message = "Mã OTP phải gồm 6 chữ số")
        String code
) {
}
