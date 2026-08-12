package com.clinicone.examination;

import java.time.LocalDate;
import java.util.UUID;

/** Filters applied to the signed medical-record history of the current patient. */
public record MedicalRecordHistoryQuery(
        UUID profileId,
        LocalDate from,
        LocalDate to,
        int page,
        int size
) {
}
