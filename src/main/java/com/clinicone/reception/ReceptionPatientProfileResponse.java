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
        boolean mustChangePassword,
        String gender,
        String phone,
        String identityNumber,
        String nationality,
        String ethnicity,
        String address,
        String provinceCode,
        String provinceName,
        String districtCode,
        String districtName,
        String wardCode,
        String wardName,
        String streetAddress
) {
    public ReceptionPatientProfileResponse(UUID id, String fullName, String relationship, LocalDate dateOfBirth,
                                           boolean primaryProfile, AccountStatus accountStatus,
                                           boolean mustChangePassword) {
        this(id, fullName, relationship, dateOfBirth, primaryProfile, accountStatus, mustChangePassword,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    static ReceptionPatientProfileResponse from(PatientProfile profile) {
        var owner = profile.getOwner();
        return new ReceptionPatientProfileResponse(profile.getId(), profile.getFullName(), profile.getRelationship(),
                profile.getDateOfBirth(), profile.isPrimaryProfile(), owner == null ? null : owner.getStatus(),
                owner != null && owner.isMustChangePassword(), profile.getGender(), profile.getPhone(),
                profile.getIdentityNumber(), profile.getNationality(), profile.getEthnicity(), profile.getAddress(),
                profile.getProvinceCode(), profile.getProvinceName(), profile.getDistrictCode(),
                profile.getDistrictName(), profile.getWardCode(), profile.getWardName(), profile.getStreetAddress());
    }
}
