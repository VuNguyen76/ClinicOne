package com.clinicone.reception;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Dữ liệu tối thiểu để nhân viên tiếp nhận hỗ trợ một người bệnh đã có tài khoản.
 * Nhân viên không được tự nhập trạng thái, số thứ tự hoặc phòng; các giá trị đó
 * được suy ra từ bác sĩ đã chọn và được tạo trong cùng giao dịch.
 */
public record ReceptionWalkInRequest(
        @NotBlank @Pattern(regexp = "0\\d{9}", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
        String phone,
        UUID profileId,
        @NotNull UUID doctorId,
        @NotNull @FutureOrPresent LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        @NotBlank @Size(min = 3, max = 500) String reason,
        @NotBlank @Size(min = 3, max = 500) String exceptionReason,
        Boolean overCapacity
) {
    @JsonCreator
    public ReceptionWalkInRequest {
    }

    public ReceptionWalkInRequest(String phone, UUID profileId, UUID doctorId, LocalDate appointmentDate,
                                  LocalTime startTime, String reason, String exceptionReason) {
        this(phone, profileId, doctorId, appointmentDate, startTime, reason, exceptionReason, false);
    }
}
