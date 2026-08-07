package com.clinicone.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueueLeaveRequest(
        @NotBlank @Size(max = 250) String reason
) {
}
