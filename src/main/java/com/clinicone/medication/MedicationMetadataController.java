package com.clinicone.medication;

import com.clinicone.auth.AuthException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR')")
public class MedicationMetadataController {
    private final MedicationUnitRepository unitRepository;
    private final MedicationDosageRepository dosageRepository;

    @GetMapping("/medication-units")
    public List<MedicationUnitResponse> listUnits(@RequestParam(defaultValue = "true") boolean activeOnly) {
        List<MedicationUnit> units = activeOnly
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
        if (unit != null && !unit.isBlank()) {
            dosages = activeOnly
                    ? dosageRepository.findByActiveTrueAndUnitNameIgnoreCaseOrderBySortOrderAsc(unit.trim())
                    : dosageRepository.findAllByUnitNameIgnoreCaseOrderBySortOrderAsc(unit.trim());
        } else if (activeOnly) {
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

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
