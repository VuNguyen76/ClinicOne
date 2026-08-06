package com.clinicone.queue;

import jakarta.validation.constraints.Size;

public record QueueSkipRequest(@Size(max = 250) String reason) {
}
