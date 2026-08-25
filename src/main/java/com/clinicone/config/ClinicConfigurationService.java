package com.clinicone.config;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

@Service
public class ClinicConfigurationService {
    private final ClinicConfigurationRepository repository;
    private final String defaultUnitName;
    private final String defaultDepartmentName;
    private final int defaultHoldMinutes;
    private final int defaultCancellationThresholdHours;

    public ClinicConfigurationService(ClinicConfigurationRepository repository) {
        this(repository, "ClinicOne", "Khám bệnh", 10, 12);
    }

    public ClinicConfigurationService(
            ClinicConfigurationRepository repository,
            @Value("${app.clinic.unit-name:ClinicOne}") String defaultUnitName,
            @Value("${app.clinic.department-name:Khám bệnh}") String defaultDepartmentName,
            @Value("${app.clinic.hold-minutes:10}") int defaultHoldMinutes,
            @Value("${app.clinic.cancellation-threshold-hours:12}") int defaultCancellationThresholdHours
    ) {
        this.repository = repository;
        this.defaultUnitName = defaultUnitName;
        this.defaultDepartmentName = defaultDepartmentName;
        this.defaultHoldMinutes = defaultHoldMinutes;
        this.defaultCancellationThresholdHours = defaultCancellationThresholdHours;
    }

    public ClinicConfiguration defaultConfiguration() {
        return new ClinicConfiguration(defaultUnitName, defaultDepartmentName, defaultHoldMinutes, defaultCancellationThresholdHours, "SYSTEM");
    }

    @Transactional
    public ClinicConfiguration current() {
        return repository.findById(ClinicConfiguration.DEFAULT_ID)
                .orElseGet(() -> repository.save(defaultConfiguration()));
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
