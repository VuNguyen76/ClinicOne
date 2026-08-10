package com.clinicone.schedule;

import java.util.List;
import java.util.UUID;

public record ClinicServiceResponse(UUID id, String name, String specialty, String visitType,
                                    int durationMinutes, boolean active,
                                    List<EligibleDoctorResponse> eligibleDoctors) {
}
