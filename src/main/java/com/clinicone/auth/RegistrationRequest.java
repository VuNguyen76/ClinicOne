package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0") String phone,
        @NotBlank @Size(min = 2, max = 200) String fullName,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
