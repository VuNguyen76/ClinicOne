package com.clinicone.medication;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicationCatalogService {
    private final MedicationRepository repository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "medications", key = "'suggestions:' + (#query == null ? '' : #query.trim().toLowerCase())")
    public List<MedicationResponse> suggestions(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) return List.of();
        return repository.findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(normalized)
                .stream().map(MedicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "medications", key = "'list:' + #activeOnly")
    public List<MedicationResponse> list(boolean activeOnly) {
        List<Medication> medications = activeOnly ? repository.findByActiveTrueOrderByNameAsc()
                : repository.findAllByOrderByNameAsc();
        return medications.stream().map(MedicationResponse::from).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "medications", allEntries = true)
    public MedicationResponse create(CreateMedicationRequest request) {
        String code = code(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_CODE_EXISTS", "Mã thuốc đã tồn tại.");
        }
        return MedicationResponse.from(repository.save(Medication.create(code, name(request.name()))));
    }

    @Transactional
    @CacheEvict(cacheNames = "medications", allEntries = true)
    public MedicationResponse update(UUID id, UpdateMedicationRequest request) {
        Medication medication = medication(id);
        String code = code(request.code());
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "MEDICATION_CODE_EXISTS", "Mã thuốc đã tồn tại.");
        }
        medication.update(code, name(request.name()));
        return MedicationResponse.from(medication);
    }

    @Transactional
    @CacheEvict(cacheNames = "medications", allEntries = true)
    public MedicationResponse setActive(UUID id, boolean active) {
        Medication medication = medication(id);
        medication.setActive(active);
        return MedicationResponse.from(medication);
    }

    @Transactional(readOnly = true)
    public Medication requireActive(UUID id) {
        return repository.findByIdAndActiveTrue(id).orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST,
                "MEDICATION_NOT_AVAILABLE", "Thuốc được chọn không còn trong danh mục đang sử dụng."));
    }

    private Medication medication(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND,
                "MEDICATION_NOT_FOUND", "Không tìm thấy thuốc."));
    }

    private String code(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String name(String value) {
        return value.trim();
    }
}
