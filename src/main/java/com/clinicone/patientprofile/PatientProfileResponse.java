package com.clinicone.patientprofile;

import java.time.LocalDate;
import java.util.UUID;

public record PatientProfileResponse(
        UUID id,
        String fullName,
        String relationship,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String identityNumber,
        String nationality,
        String ethnicity,
        String address,
        boolean primaryProfile
) {
    public static PatientProfileResponse from(PatientProfile profile) {
        return new PatientProfileResponse(profile.getId(), profile.getFullName(), profile.getRelationship(),
                profile.getDateOfBirth(), profile.getGender(), profile.getPhone(), profile.getIdentityNumber(),
                profile.getNationality(), profile.getEthnicity(), profile.getAddress(), profile.isPrimaryProfile());
    }
}
