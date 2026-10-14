package com.clinicone.medication;

import com.clinicone.auth.AuthException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR')")
public class MedicationMetadataController {
    private final MedicationUnitRepository unitRepository;
    private final MedicationDosageRepository dosageRepository;

    @GetMapping("/medication-units")
    public List<MedicationUnitResponse> listUnits(@RequestParam(defaultValue = "true") boolean activeOnly) {
        List<MedicationUnit> units = activeOnly || !canManageCatalog()
                ? unitRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                : unitRepository.findAllByOrderBySortOrderAscNameAsc();
        return units.stream().map(MedicationUnitResponse::from).toList();
    }

    @GetMapping("/medication-dosages")
    public List<MedicationDosageResponse> listDosages(
            @RequestParam(required = false) String unit,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        List<MedicationDosage> dosages;
        boolean showActiveOnly = activeOnly || !canManageCatalog();
        if (unit != null && !unit.isBlank()) {
            dosages = showActiveOnly
                    ? dosageRepository.findByActiveTrueAndUnitNameIgnoreCaseOrderBySortOrderAsc(unit.trim())
                    : dosageRepository.findAllByUnitNameIgnoreCaseOrderBySortOrderAsc(unit.trim());
        } else if (showActiveOnly) {
            dosages = dosageRepository.findByActiveTrueOrderBySortOrderAsc();
        } else {
            dosages = dosageRepository.findAllByOrderBySortOrderAsc();
        }
        return dosages.stream().map(MedicationDosageResponse::from).toList();
    }

    @PostMapping("/medication-units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    @Transactional
    public MedicationUnitResponse createUnit(@Valid @RequestBody CreateMedicationUnitRequest request) {
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        String name = request.name().trim();
        if (unitRepository.existsByCodeIgnoreCase(code)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_UNIT_CODE_EXISTS", "Mã đơn vị tính đã tồn tại.");
        }
        if (unitRepository.existsByNameIgnoreCase(name)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_UNIT_NAME_EXISTS", "Tên đơn vị tính đã tồn tại.");
        }
        MedicationUnit saved = unitRepository.save(new MedicationUnit(
                code, name, clean(request.description()), request.sortOrder()));
        return MedicationUnitResponse.from(saved);
    }

    @PutMapping("/medication-units/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    @Transactional
    public MedicationUnitResponse updateUnit(@PathVariable UUID id, @Valid @RequestBody CreateMedicationUnitRequest request) {
        MedicationUnit unit = unitRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "MEDICATION_UNIT_NOT_FOUND", "Không tìm thấy đơn vị tính."));
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        String name = request.name().trim();
        if (unitRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_UNIT_CODE_EXISTS", "Mã đơn vị tính đã tồn tại.");
        }
        if (unitRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_UNIT_NAME_EXISTS", "Tên đơn vị tính đã tồn tại.");
        }
        String previousName = unit.getName();
        unit.update(code, name, clean(request.description()), request.sortOrder());
        MedicationUnit saved = unitRepository.save(unit);
        if (!previousName.equalsIgnoreCase(saved.getName())) {
            dosageRepository.findAllByUnitNameIgnoreCaseOrderBySortOrderAsc(previousName).forEach(dosage -> {
                dosage.update(dosage.getCode(), saved.getName(), dosage.getDosageFormat(), dosage.getDescription(), dosage.getSortOrder());
                dosageRepository.save(dosage);
            });
        }
        return MedicationUnitResponse.from(saved);
    }

    @PatchMapping("/medication-units/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    @Transactional
    public MedicationUnitResponse setUnitActive(@PathVariable UUID id, @Valid @RequestBody SetCatalogActiveRequest request) {
        MedicationUnit unit = unitRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "MEDICATION_UNIT_NOT_FOUND", "Không tìm thấy đơn vị tính."));
        unit.setActive(request.active());
        MedicationUnit saved = unitRepository.save(unit);
        if (!request.active()) {
            dosageRepository.findAllByUnitNameIgnoreCaseOrderBySortOrderAsc(unit.getName()).stream()
                    .filter(MedicationDosage::isActive)
                    .forEach(dosage -> { dosage.setActive(false); dosageRepository.save(dosage); });
        }
        return MedicationUnitResponse.from(saved);
    }

    @PostMapping("/medication-dosages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    @Transactional
    public MedicationDosageResponse createDosage(@Valid @RequestBody CreateMedicationDosageRequest request) {
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        String unitName = request.unitName().trim();
        if (dosageRepository.existsByCodeIgnoreCase(code)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_DOSAGE_CODE_EXISTS", "Mã liều dùng đã tồn tại.");
        }
        MedicationUnit unit = unitRepository.findByNameIgnoreCase(unitName)
                .filter(MedicationUnit::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "MEDICATION_UNIT_NOT_AVAILABLE",
                        "Đơn vị tính không tồn tại hoặc đã ngừng sử dụng."));
        MedicationDosage saved = dosageRepository.save(new MedicationDosage(
                code, unit.getName(), request.dosageFormat().trim(), clean(request.description()), request.sortOrder()));
        return MedicationDosageResponse.from(saved);
    }

    @PutMapping("/medication-dosages/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    @Transactional
    public MedicationDosageResponse updateDosage(@PathVariable UUID id, @Valid @RequestBody CreateMedicationDosageRequest request) {
        MedicationDosage dosage = dosageRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "MEDICATION_DOSAGE_NOT_FOUND", "Không tìm thấy liều dùng chuẩn."));
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        String unitName = request.unitName().trim();
        if (dosageRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_DOSAGE_CODE_EXISTS", "Mã liều dùng đã tồn tại.");
        }
        MedicationUnit unit = unitRepository.findByNameIgnoreCase(unitName)
                .filter(MedicationUnit::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "MEDICATION_UNIT_NOT_AVAILABLE",
                        "Đơn vị tính không tồn tại hoặc đã ngừng sử dụng."));
        dosage.update(code, unit.getName(), request.dosageFormat().trim(), clean(request.description()), request.sortOrder());
        return MedicationDosageResponse.from(dosageRepository.save(dosage));
    }

    @PatchMapping("/medication-dosages/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    @Transactional
    public MedicationDosageResponse setDosageActive(@PathVariable UUID id, @Valid @RequestBody SetCatalogActiveRequest request) {
        MedicationDosage dosage = dosageRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "MEDICATION_DOSAGE_NOT_FOUND", "Không tìm thấy liều dùng chuẩn."));
        if (request.active()) {
            unitRepository.findByNameIgnoreCase(dosage.getUnitName())
                    .filter(MedicationUnit::isActive)
                    .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "MEDICATION_UNIT_NOT_AVAILABLE",
                            "Hãy kích hoạt lại đơn vị tính trước khi dùng liều này."));
        }
        dosage.setActive(request.active());
        return MedicationDosageResponse.from(dosageRepository.save(dosage));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean canManageCatalog() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN") || authority.getAuthority().equals("ROLE_COORDINATOR"));
    }
}
