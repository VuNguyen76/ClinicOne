package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStaffProfileRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 160) String unitName,
        @Size(max = 160) String departmentName
) {
}
