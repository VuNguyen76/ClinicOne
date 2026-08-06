package com.clinicone.queue;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QueueCheckInRequest(@NotNull UUID appointmentId) {
}
