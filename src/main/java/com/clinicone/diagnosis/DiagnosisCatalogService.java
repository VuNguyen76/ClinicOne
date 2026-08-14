package com.clinicone.diagnosis;

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
public class DiagnosisCatalogService {
    private final DiagnosisCatalogRepository repository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "diagnoses", key = "'suggestions:' + (#query == null ? '' : #query.trim().toLowerCase())")
    public List<DiagnosisCatalogResponse> suggestions(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) return List.of();
        return repository.findTop10ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(normalized)
                .stream().map(DiagnosisCatalogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "diagnoses", key = "'list:' + #activeOnly")
    public List<DiagnosisCatalogResponse> list(boolean activeOnly) {
        List<DiagnosisCatalog> diagnoses = activeOnly ? repository.findByActiveTrueOrderByNameAsc()
                : repository.findAllByOrderByNameAsc();
        return diagnoses.stream().map(DiagnosisCatalogResponse::from).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "diagnoses", allEntries = true)
    public DiagnosisCatalogResponse create(CreateDiagnosisCatalogRequest request) {
        String code = code(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new AuthException(HttpStatus.CONFLICT, "DIAGNOSIS_CODE_EXISTS", "Mã chẩn đoán đã tồn tại.");
        }
        return DiagnosisCatalogResponse.from(repository.save(DiagnosisCatalog.create(code, name(request.name()))));
    }

    @Transactional
    @CacheEvict(cacheNames = "diagnoses", allEntries = true)
    public DiagnosisCatalogResponse update(UUID id, UpdateDiagnosisCatalogRequest request) {
        DiagnosisCatalog diagnosis = diagnosis(id);
        String code = code(request.code());
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "DIAGNOSIS_CODE_EXISTS", "Mã chẩn đoán đã tồn tại.");
        }
        diagnosis.update(code, name(request.name()));
        return DiagnosisCatalogResponse.from(diagnosis);
    }

    @Transactional
    @CacheEvict(cacheNames = "diagnoses", allEntries = true)
    public DiagnosisCatalogResponse setActive(UUID id, boolean active) {
        DiagnosisCatalog diagnosis = diagnosis(id);
        diagnosis.setActive(active);
        return DiagnosisCatalogResponse.from(diagnosis);
    }

    private DiagnosisCatalog diagnosis(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND,
                "DIAGNOSIS_NOT_FOUND", "Không tìm thấy chẩn đoán trong danh mục."));
    }

    private String code(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String name(String value) {
        return value.trim();
    }
}
