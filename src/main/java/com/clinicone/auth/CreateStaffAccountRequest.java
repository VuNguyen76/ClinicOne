package com.clinicone.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStaffAccountRequest(
        @NotBlank @Size(min = 1, max = 100) String fullName,
        @NotBlank @Size(min = 1, max = 20) String employeeCode,
        @NotBlank @Size(max = 160) String unitName,
        @NotBlank @Size(max = 160) String departmentName,
        @NotEmpty @Size(min = 1, max = 3) List<@Valid @NotNull StaffRole> roles) {
}
