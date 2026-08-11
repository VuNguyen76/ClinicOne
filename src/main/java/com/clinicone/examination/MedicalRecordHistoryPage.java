package com.clinicone.examination;

import java.util.List;

/** A bounded page of signed records. Drafts are never represented here. */
public record MedicalRecordHistoryPage(
        List<MedicalRecordResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
