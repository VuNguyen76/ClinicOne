package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import com.clinicone.validation.VietnamesePhone;
import jakarta.validation.constraints.Size;

public record PasswordLoginRequest(
        @NotBlank(message = "Số điện thoại là thông tin bắt buộc")
        @VietnamesePhone
        String phone,
        @NotBlank(message = "Mật khẩu là thông tin bắt buộc")
        @Size(min = 6, max = 72, message = "Mật khẩu phải có từ 6 đến 72 ký tự")
        String password
) {
}
