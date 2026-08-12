package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Số điện thoại là thông tin bắt buộc")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0")
        String phone,
        @NotBlank(message = "Mật khẩu mới là thông tin bắt buộc")
        @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
        String newPassword,
        @NotBlank(message = "Xác nhận mật khẩu là thông tin bắt buộc")
        String confirmPassword
) {
}
