package com.clinicone.invoice;

import java.util.List;
import java.util.UUID;

public record PublicPatientProfileDataResponse(UUID profileId, String fullName, String relationship,
                                               List<PublicInvoiceResponse> invoices,
                                               List<PublicPatientMedicalRecordResponse> medicalRecords) {
}
