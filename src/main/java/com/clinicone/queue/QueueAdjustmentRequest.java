package com.clinicone.queue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record QueueAdjustmentRequest(
        @NotNull QueueAdjustmentAction action,
        UUID targetDoctorId,
        @Size(max = 120) String targetRoomCode,
        @Size(max = 120) String targetSpecialty,
        @Size(min = 10, max = 500) String reason,
        String targetStartTime
) {
    public QueueAdjustmentRequest {
        if (targetStartTime != null && !targetStartTime.isBlank()) {
            try { java.time.LocalTime.parse(targetStartTime); } catch (Exception e) { throw new IllegalArgumentException("targetStartTime must be HH:mm"); }
        }
    }
    public QueueAdjustmentRequest(QueueAdjustmentAction action, UUID targetDoctorId, String targetRoomCode, String targetSpecialty, String reason) {
        this(action, targetDoctorId, targetRoomCode, targetSpecialty, reason, null);
    }
}
