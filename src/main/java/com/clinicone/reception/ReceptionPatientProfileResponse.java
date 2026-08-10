package com.clinicone.reception;

import com.clinicone.auth.AccountStatus;
import com.clinicone.patientprofile.PatientProfile;

import java.time.LocalDate;
import java.util.UUID;

public record ReceptionPatientProfileResponse(
        UUID id,
        String fullName,
        String relationship,
        LocalDate dateOfBirth,
        boolean primaryProfile,
        AccountStatus accountStatus,
        boolean mustChangePassword
) {
    static ReceptionPatientProfileResponse from(PatientProfile profile) {
        var owner = profile.getOwner();
        return new ReceptionPatientProfileResponse(profile.getId(), profile.getFullName(), profile.getRelationship(),
                profile.getDateOfBirth(), profile.isPrimaryProfile(), owner.getStatus(), owner.isMustChangePassword());
    }
}
