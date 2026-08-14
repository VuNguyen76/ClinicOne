package com.clinicone.config;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClinicConfigurationService {
    private final ClinicConfigurationRepository repository;

    @Transactional
    public ClinicConfiguration current() {
        return repository.findById(ClinicConfiguration.DEFAULT_ID)
                .orElseGet(() -> repository.save(ClinicConfiguration.defaults()));
    }

    @Transactional
    public ClinicConfigurationResponse get() {
        return ClinicConfigurationResponse.from(current());
    }

    @Transactional
    public ClinicConfigurationResponse update(UpdateClinicConfigurationRequest request, String actor) {
        if (request == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CONFIGURATION_REQUIRED",
                    "Cần cung cấp cấu hình cần lưu.");
        }
        String unit = normalize(request.unitName(), "Tên đơn vị không được để trống.");
        String department = normalize(request.departmentName(), "Tên phòng ban không được để trống.");
        if (request.holdMinutes() < 5 || request.holdMinutes() > 30) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "HOLD_MINUTES_INVALID",
                    "Thời gian giữ chỗ phải từ 5 đến 30 phút.");
        }
        if (request.cancellationThresholdHours() < 0 || request.cancellationThresholdHours() > 72) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CANCELLATION_THRESHOLD_INVALID",
                    "Ngưỡng hủy phải từ 0 đến 72 giờ.");
        }
        String updatedBy = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        ClinicConfiguration configuration = repository.findById(ClinicConfiguration.DEFAULT_ID)
                .orElseGet(ClinicConfiguration::defaults);
        configuration.update(unit, department, request.holdMinutes(), request.cancellationThresholdHours(), updatedBy);
        return ClinicConfigurationResponse.from(repository.save(configuration));
    }

    private String normalize(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CONFIGURATION_FIELD_REQUIRED", message);
        }
        return normalized;
    }
}
