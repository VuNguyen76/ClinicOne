package com.clinicone.rescheduling;

import java.time.LocalDate;
import java.util.UUID;

public record DoctorTimeOffWindow(UUID doctorId, LocalDate startDate, LocalDate endDate) {
}
