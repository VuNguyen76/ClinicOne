package com.clinicone.examination;

import java.util.List;

public record DoctorMedicalRecordHistoryPage(
        List<DoctorMedicalRecordResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
