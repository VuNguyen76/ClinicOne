package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên là thông tin bắt buộc")
        @Size(min = 2, max = 100, message = "Họ và tên phải có từ 2 đến 100 ký tự")
        String fullName,
        @NotNull(message = "Ngày sinh là thông tin bắt buộc")
        @PastOrPresent(message = "Ngày sinh không được ở tương lai")
        LocalDate dateOfBirth,
        @NotBlank(message = "Giới tính là thông tin bắt buộc")
        @Size(max = 20, message = "Giới tính không hợp lệ")
        String gender,
        @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
        String address,
        @Pattern(regexp = "^(|\\d{9}|\\d{12})$", message = "CMND/CCCD phải gồm 9 hoặc 12 chữ số")
        String identityNumber,
        @Size(max = 100) String nationality,
        @Size(max = 100) String ethnicity,
        @Size(max = 10) String provinceCode,
        @Size(max = 120) String provinceName,
        @Size(max = 10) String districtCode,
        @Size(max = 120) String districtName,
        @Size(max = 10) String wardCode,
        @Size(max = 120) String wardName,
        @Size(max = 500) String streetAddress
) {
    public UpdateProfileRequest(String fullName, LocalDate dateOfBirth, String gender, String address) {
        this(fullName, dateOfBirth, gender, address, null, null, null, null, null, null, null, null, null, null);
    }
}
