package com.clinicone.auth;

import java.util.UUID;
import java.time.LocalDate;

public record PatientProfileResponse(
        UUID accountId,
        String phone,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String identityNumber,
        String nationality,
        String ethnicity,
        String provinceCode,
        String provinceName,
        String districtCode,
        String districtName,
        String wardCode,
        String wardName,
        String streetAddress,
        AccountStatus status,
        boolean mustChangePassword
) {
    public PatientProfileResponse(UUID accountId, String phone, String fullName, AccountStatus status,
                                  boolean mustChangePassword) {
        this(accountId, phone, fullName,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                status, mustChangePassword);
    }

    public PatientProfileResponse(UUID accountId, String phone, String fullName, LocalDate dateOfBirth,
                                  String gender, String address, AccountStatus status, boolean mustChangePassword) {
        this(accountId, phone, fullName, dateOfBirth, gender, address, null, null, null, null, null, null, null,
                null, null, null, status, mustChangePassword);
    }
}
