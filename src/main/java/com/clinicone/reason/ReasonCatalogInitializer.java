package com.clinicone.reason;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(20)
public class ReasonCatalogInitializer implements CommandLineRunner {
    private final ReasonCatalogRepository repository;

    public ReasonCatalogInitializer(ReasonCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.countByTypeAndActiveTrue(ReasonCatalogType.APPOINTMENT_CANCELLATION) >= 3) {
            return;
        }
        List<ReasonCatalog> defaults = List.of(
                ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION, "MEDICAL_EMERGENCY", "Có việc đột xuất"),
                ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION, "SCHEDULE_CHANGE", "Thay đổi kế hoạch"),
                ReasonCatalog.create(ReasonCatalogType.APPOINTMENT_CANCELLATION, "FOUND_OTHER_PROVIDER", "Đã khám ở nơi khác")
        );
        defaults.forEach(reason -> {
            if (!repository.existsByTypeAndCodeIgnoreCase(reason.getType(), reason.getCode())) {
                repository.save(reason);
            }
        });
    }
}
