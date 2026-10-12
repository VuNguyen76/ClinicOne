package com.clinicone.invoice;

import java.util.List;

public record PublicPatientDataLookupResponse(List<PublicPatientProfileDataResponse> profiles) {
}
