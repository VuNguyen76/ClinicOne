package com.clinicone.medication;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            dosages = dosageRepository.findByActiveTrueAndUnitNameIgnoreCaseOrderBySortOrderAsc(unit.trim());
        } else if (activeOnly) {
            dosages = dosageRepository.findByActiveTrueOrderBySortOrderAsc();
        } else {
            dosages = dosageRepository.findAllByOrderBySortOrderAsc();
        }
        return dosages.stream().map(MedicationDosageResponse::from).toList();
    }
}
