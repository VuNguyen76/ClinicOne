package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SpecialtyCatalogService {
    private final SpecialtyCatalogRepository repository;

    @Cacheable(cacheNames = "specialties", key = "#query == null ? '' : #query.trim().toLowerCase()")
    public List<SpecialtyResponse> list(String query) {
        List<SpecialtyResponse> source = repository.findByActiveTrueOrderByNameAsc().stream()
                .map(item -> new SpecialtyResponse(item.getCode(), item.getName(), item.getDescription()))
                .toList();

        if (query == null || query.isBlank()) {
            return source.stream().sorted(Comparator.comparing(SpecialtyResponse::name)).toList();
        }
        String normalizedQuery = normalize(query);
        return source.stream()
                .filter(item -> normalize(item.name()).contains(normalizedQuery)
                        || normalize(item.description()).contains(normalizedQuery))
                .toList();
    }

    public SpecialtyResponse require(String name) {
        return list(null).stream()
                .filter(item -> item.name().equalsIgnoreCase(name == null ? "" : name.trim()))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND",
                        "Chuyên khoa không tồn tại."));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    @CacheEvict(cacheNames = "specialties", allEntries = true)
    public SpecialtyResponse create(CreateSpecialtyRequest request) {
        String code = normalizeCode(request.code());
        String name = normalizeRequired(request.name(), 120);
        if (repository.existsByCodeIgnoreCase(code) || repository.existsByNameIgnoreCase(name)) {
            throw new AuthException(HttpStatus.CONFLICT, "SPECIALTY_ALREADY_EXISTS",
                    "Mã hoặc tên chuyên khoa đã tồn tại.");
        }
        SpecialtyCatalogEntry saved = repository.save(SpecialtyCatalogEntry.create(code, name, request.description()));
        return new SpecialtyResponse(saved.getCode(), saved.getName(), saved.getDescription());
    }

    @CacheEvict(cacheNames = "specialties", allEntries = true)
    public SpecialtyResponse update(String code, CreateSpecialtyRequest request) {
        SpecialtyCatalogEntry entry = repository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND",
                        "Chuyên khoa không tồn tại."));
        String nextCode = normalizeCode(request.code());
        String nextName = normalizeRequired(request.name(), 120);
        if ((!entry.getCode().equalsIgnoreCase(nextCode) && repository.existsByCodeIgnoreCase(nextCode))
                || (!entry.getName().equalsIgnoreCase(nextName) && repository.existsByNameIgnoreCase(nextName))) {
            throw new AuthException(HttpStatus.CONFLICT, "SPECIALTY_ALREADY_EXISTS",
                    "Mã hoặc tên chuyên khoa đã tồn tại.");
        }
        entry.update(nextCode, nextName, request.description());
        SpecialtyCatalogEntry saved = repository.save(entry);
        return new SpecialtyResponse(saved.getCode(), saved.getName(), saved.getDescription());
    }

    @CacheEvict(cacheNames = "specialties", allEntries = true)
    public void deactivate(String code) {
        SpecialtyCatalogEntry entry = repository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND",
                        "Chuyên khoa không tồn tại."));
        entry.setActive(false);
        repository.save(entry);
    }

    private String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.matches("[A-Z0-9_-]{2,20}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SPECIALTY_CODE_INVALID",
                    "Mã chuyên khoa chỉ gồm 2 đến 20 ký tự chữ, số, gạch ngang hoặc gạch dưới.");
        }
        return normalized;
    }

    private String normalizeRequired(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SPECIALTY_FIELD_INVALID",
                    "Thông tin chuyên khoa không hợp lệ.");
        }
        return normalized;
    }
}
