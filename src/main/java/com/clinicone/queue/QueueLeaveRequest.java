package com.clinicone.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueueLeaveRequest(
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
