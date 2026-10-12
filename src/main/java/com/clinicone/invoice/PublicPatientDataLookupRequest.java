package com.clinicone.invoice;

import com.clinicone.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PublicPatientDataLookupRequest(
        @NotBlank @VietnamesePhone String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code
) {
}
