package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsLoginRequest(
        @NotBlank(message = "Số điện thoại là thông tin bắt buộc")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0")
        String phone,
        @NotBlank(message = "Mã OTP là thông tin bắt buộc")
        @Pattern(regexp = "^\\d{6}$", message = "Mã OTP phải gồm 6 chữ số")
        String code
) {
}
