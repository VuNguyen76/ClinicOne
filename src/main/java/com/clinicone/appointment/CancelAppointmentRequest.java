package com.clinicone.appointment;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(@Size(max = 500) String reason,
                                       @Size(max = 50) String reasonCode) {
    public CancelAppointmentRequest(String reason) {
        this(reason, null);
    }
}
