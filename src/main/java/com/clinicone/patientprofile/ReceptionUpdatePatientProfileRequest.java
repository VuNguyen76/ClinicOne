package com.clinicone.patientprofile;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Chỉ mang các trường lễ tân có thể bổ sung khi hồ sơ còn thiếu dữ liệu. */
public record ReceptionUpdatePatientProfileRequest(
        @Size(min = 2, max = 100) String fullName,
        @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 20) String gender,
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
    public boolean isEmpty() {
        return blank(fullName) && dateOfBirth == null && blank(gender) && blank(phone)
                && blank(identityNumber) && blank(nationality) && blank(ethnicity) && blank(address)
                && blank(provinceCode) && blank(provinceName) && blank(districtCode) && blank(districtName)
                && blank(wardCode) && blank(wardName) && blank(streetAddress);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
