package com.clinicone.examination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StopExaminationRequest(
        @NotBlank(message = "Cần nêu lý do dừng lượt khám.")
        @Size(min = 10, max = 500, message = "Lý do dừng lượt khám phải từ 10 đến 500 ký tự.")
        String reason
) {
}
