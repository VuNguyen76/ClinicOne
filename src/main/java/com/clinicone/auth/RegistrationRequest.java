package com.clinicone.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0") String phone,
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull(message = "Ngày sinh là thông tin bắt buộc")
        @PastOrPresent(message = "Ngày sinh không được ở tương lai") LocalDate dateOfBirth,
        @NotBlank(message = "Giới tính là thông tin bắt buộc") @Size(max = 20) String gender,
        @Size(max = 500) String address,
        @Size(max = 10) String provinceCode,
        @Size(max = 120) String provinceName,
        @Size(max = 10) String districtCode,
        @Size(max = 120) String districtName,
        @Size(max = 10) String wardCode,
        @Size(max = 120) String wardName,
        @Size(max = 500) String streetAddress
) {
    public RegistrationRequest(String phone, String fullName, String password, LocalDate dateOfBirth,
                               String gender, String address) {
        this(phone, fullName, password, dateOfBirth, gender, address, null, null, null, null, null, null, null);
    }

    public RegistrationRequest(String phone, String fullName, String password) {
        this(phone, fullName, password, null, null, null, null, null, null, null, null, null, null);
    }
}
