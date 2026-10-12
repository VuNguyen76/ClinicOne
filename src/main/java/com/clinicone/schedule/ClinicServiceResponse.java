package com.clinicone.schedule;

import java.util.List;
import java.util.UUID;

public record ClinicServiceResponse(UUID id, String name, String specialty, String visitType,
                                    int durationMinutes, boolean active,
                                    List<EligibleDoctorResponse> eligibleDoctors,
                                    boolean requiresMedicalRecord,
                                    long consultationFee) {
    public ClinicServiceResponse(UUID id, String name, String specialty, String visitType,
                                 int durationMinutes, boolean active,
                                 List<EligibleDoctorResponse> eligibleDoctors,
                                 boolean requiresMedicalRecord) {
        this(id, name, specialty, visitType, durationMinutes, active, eligibleDoctors, requiresMedicalRecord, 0L);
    }

    public ClinicServiceResponse(UUID id, String name, String specialty, String visitType,
                                 int durationMinutes, boolean active,
                                 List<EligibleDoctorResponse> eligibleDoctors) {
        this(id, name, specialty, visitType, durationMinutes, active, eligibleDoctors, true, 0L);
    }
}
