package com.clinicone.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateStaffRolesRequest(
        @NotEmpty @Size(min = 1, max = 3) List<@Valid @NotNull StaffRole> roles) {
}
