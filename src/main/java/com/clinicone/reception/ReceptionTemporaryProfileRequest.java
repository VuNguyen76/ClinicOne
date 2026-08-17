package com.clinicone.reception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import com.clinicone.validation.VietnamesePhone;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Thông tin tối thiểu để lập hồ sơ tạm khi người bệnh chưa có tài khoản. */
public record ReceptionTemporaryProfileRequest(
        @NotBlank @VietnamesePhone
        String phone,
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotBlank @Size(max = 20) String gender,
        @Pattern(regexp = "^$|\\d{9}|\\d{12}", message = "CMND/CCCD phải gồm 9 hoặc 12 chữ số")
        String identityNumber,
        @Size(max = 100) String nationality,
        @Size(max = 100) String ethnicity,
        @Size(max = 500) String address
) {
}
