package com.clinicone.reason;

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
public class ReasonCatalogService {
    private static final long MINIMUM_CANCELLATION_REASONS = 3;
    private final ReasonCatalogRepository repository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reasons", key = "(#type == null ? 'null' : #type.name()) + ':' + #activeOnly")
    public List<ReasonCatalogResponse> list(ReasonCatalogType type, boolean activeOnly) {
        if (type == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REASON_TYPE_REQUIRED", "Cần chọn loại danh mục lý do.");
        }
        List<ReasonCatalog> items = activeOnly
                ? repository.findByTypeAndActiveTrueOrderByLabelAsc(type)
                : repository.findByTypeOrderByLabelAsc(type);
        return items.stream().map(ReasonCatalogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReasonCatalog requireActive(ReasonCatalogType type, String code) {
        String normalized = normalizeCode(code);
        return repository.findByTypeAndCodeIgnoreCaseAndActiveTrue(type, normalized)
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "REASON_NOT_AVAILABLE",
                        "Lý do đã chọn không còn được sử dụng."));
    }

    @Transactional
    @CacheEvict(cacheNames = "reasons", allEntries = true)
    public ReasonCatalogResponse create(CreateReasonCatalogRequest request) {
        if (request == null || request.type() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REASON_TYPE_REQUIRED", "Cần chọn loại danh mục lý do.");
        }
        String code = normalizeCode(request.code());
        String label = normalizeLabel(request.label());
        if (repository.existsByTypeAndCodeIgnoreCase(request.type(), code)) {
            throw new AuthException(HttpStatus.CONFLICT, "REASON_CODE_EXISTS", "Mã lý do đã tồn tại trong danh mục.");
        }
        return ReasonCatalogResponse.from(repository.save(ReasonCatalog.create(request.type(), code, label)));
    }

    @Transactional
    @CacheEvict(cacheNames = "reasons", allEntries = true)
    public ReasonCatalogResponse update(UUID id, UpdateReasonCatalogRequest request) {
        ReasonCatalog reason = find(id);
        String code = normalizeCode(request == null ? null : request.code());
        String label = normalizeLabel(request == null ? null : request.label());
        if (repository.existsByTypeAndCodeIgnoreCaseAndIdNot(reason.getType(), code, id)) {
            throw new AuthException(HttpStatus.CONFLICT, "REASON_CODE_EXISTS", "Mã lý do đã tồn tại trong danh mục.");
        }
        reason.update(code, label);
        return ReasonCatalogResponse.from(repository.save(reason));
    }

    @Transactional
    @CacheEvict(cacheNames = "reasons", allEntries = true)
    public ReasonCatalogResponse setActive(UUID id, boolean active) {
        ReasonCatalog reason = find(id);
        if (!active && reason.isActive() && reason.getType() == ReasonCatalogType.APPOINTMENT_CANCELLATION
                && repository.countByTypeAndActiveTrue(reason.getType()) <= MINIMUM_CANCELLATION_REASONS) {
            throw new AuthException(HttpStatus.CONFLICT, "REASON_CATALOG_MINIMUM_REQUIRED",
                    "Danh mục lý do hủy phải còn ít nhất 3 lý do đang sử dụng.");
        }
        reason.setActive(active);
        return ReasonCatalogResponse.from(repository.save(reason));
    }

    private ReasonCatalog find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND,
                "REASON_NOT_FOUND", "Không tìm thấy lý do trong danh mục."));
    }

    private String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{2,50}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REASON_CODE_INVALID",
                    "Mã lý do chỉ gồm chữ in hoa, số và dấu gạch dưới (2-50 ký tự).");
        }
        return normalized;
    }

    private String normalizeLabel(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REASON_LABEL_REQUIRED", "Tên lý do không được để trống.");
        }
        return normalized;
    }
}
