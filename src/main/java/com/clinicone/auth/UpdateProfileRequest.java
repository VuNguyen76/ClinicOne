package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên là thông tin bắt buộc")
        @Size(min = 2, max = 200, message = "Họ và tên phải có từ 2 đến 200 ký tự")
        String fullName
) {
}
