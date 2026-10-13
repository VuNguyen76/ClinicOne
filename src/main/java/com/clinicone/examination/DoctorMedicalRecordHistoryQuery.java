package com.clinicone.examination;

import java.time.LocalDate;

public record DoctorMedicalRecordHistoryQuery(
        String search,
        LocalDate from,
        LocalDate to,
        int page,
        int size
) {
}
