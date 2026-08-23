package com.clinicone.reception;

import jakarta.validation.constraints.NotBlank;
import com.clinicone.validation.VietnamesePhone;

public record ReceptionPatientOtpRequest(
        @NotBlank @VietnamesePhone String phone
) {
}
