package com.clinicone.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClinicRoomRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String specialty
) {
}
