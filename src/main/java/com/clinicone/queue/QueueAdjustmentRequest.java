package com.clinicone.queue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record QueueAdjustmentRequest(
        @NotNull QueueAdjustmentAction action,
        UUID targetDoctorId,
        @Size(max = 120) String targetRoomCode,
        @Size(max = 120) String targetSpecialty,
        @Size(min = 10, max = 500) String reason
) {
}
