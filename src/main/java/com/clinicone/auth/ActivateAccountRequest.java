package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import com.clinicone.validation.VietnamesePhone;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @NotBlank @VietnamesePhone String phone,
        @Size(min = 6, max = 6) String otpCode,
        @NotBlank @Size(min = 8, max = 64) String newPassword,
        @NotBlank @Size(min = 8, max = 64) String confirmPassword
) {
    public ActivateAccountRequest(String phone, String newPassword, String confirmPassword) {
        this(phone, null, newPassword, confirmPassword);
    }
}
