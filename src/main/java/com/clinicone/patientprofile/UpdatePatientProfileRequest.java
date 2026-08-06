package com.clinicone.patientprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePatientProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Size(max = 50) String relationship,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotBlank @Size(max = 20) String gender,
        @Pattern(regexp = "^$|0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0") String phone,
        @Pattern(regexp = "^$|\\d{9}|\\d{12}", message = "CMND/CCCD phải gồm 9 hoặc 12 chữ số") String identityNumber,
        @Size(max = 100) String nationality,
        @Size(max = 100) String ethnicity,
        @Size(max = 500) String address,
        @Size(max = 10) String provinceCode,
        @Size(max = 120) String provinceName,
        @Size(max = 10) String districtCode,
        @Size(max = 120) String districtName,
        @Size(max = 10) String wardCode,
        @Size(max = 120) String wardName,
        @Size(max = 500) String streetAddress
) {
    public UpdatePatientProfileRequest(String fullName, String relationship, LocalDate dateOfBirth, String gender,
                                       String phone, String identityNumber, String nationality, String ethnicity,
                                       String address) {
        this(fullName, relationship, dateOfBirth, gender, phone, identityNumber, nationality, ethnicity, address,
                null, null, null, null, null, null, null);
    }
}
