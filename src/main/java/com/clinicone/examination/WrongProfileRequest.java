package com.clinicone.examination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WrongProfileRequest(
        @NotBlank(message = "Cần ghi lý do bắt đầu nhầm hồ sơ.")
        @Size(min = 10, max = 500, message = "Lý do bắt đầu nhầm hồ sơ phải từ 10 đến 500 ký tự.")
        String reason
) {
}
