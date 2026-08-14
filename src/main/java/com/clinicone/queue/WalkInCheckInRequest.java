package com.clinicone.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WalkInCheckInRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^$|0\\d{9}$", message = "Số điện thoại phải 10 chữ số bắt đầu bằng 0")
        String phone,

        @NotBlank(message = "Vui lòng chọn chuyên khoa hoặc dịch vụ")
        String specialty,

        @NotBlank(message = "Lý do khám không được để trống")
        @Size(min = 3, max = 500, message = "Lý do khám phải từ 3 đến 500 ký tự")
        String reason,

        // --- Các trường bên dưới chỉ dùng cho trường hợp NGOẠI LỆ VƯỢT NĂNG LỰC ---
        
        UUID doctorId, // Không bắt buộc, chỉ Lễ tân chọn khi vượt năng lực

        @Size(min = 10, max = 500, message = "Lý do vượt năng lực phải từ 10 đến 500 ký tự")
        String overCapacityReason
) {
}