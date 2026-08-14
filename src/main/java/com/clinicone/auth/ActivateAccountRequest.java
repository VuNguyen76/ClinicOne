package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @NotBlank @Pattern(regexp = "0\\d{9}") String phone,
        @Size(min = 6, max = 6) String otpCode,
        @NotBlank @Size(min = 8, max = 64) String newPassword,
        @NotBlank @Size(min = 8, max = 64) String confirmPassword
) {
    public ActivateAccountRequest(String phone, String newPassword, String confirmPassword) {
        this(phone, null, newPassword, confirmPassword);
    }
}
