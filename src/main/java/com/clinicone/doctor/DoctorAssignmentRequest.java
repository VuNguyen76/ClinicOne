package com.clinicone.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DoctorAssignmentRequest(
        @NotBlank @Size(max = 120) String specialty,
        @NotNull UUID roomId,
        @Size(max = 500) String avatarUrl
) {
    public DoctorAssignmentRequest(String specialty, UUID roomId) {
        this(specialty, roomId, null);
    }
}
