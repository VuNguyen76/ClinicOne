package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.ArrayList;
import java.util.UUID;

@Service
public class SpecialtyCatalogService {
    private final SpecialtyCatalogRepository repository;
    private static final List<SpecialtyResponse> SPECIALTIES = List.of(
            new SpecialtyResponse("TQ", "Khám Tổng Quát", "Khám và tầm soát tổng quát, được hướng dẫn tới đúng chuyên khoa khi cần."),
            new SpecialtyResponse("TGM", "Khám Tiêu Hoá - Gan Mật", "Đau bụng, ợ hơi, trào ngược, rối loạn tiêu hoá hoặc bệnh lý gan mật."),
            new SpecialtyResponse("TK", "Khám Thần Kinh", "Đau đầu, chóng mặt, mất ngủ, tê bì hoặc đau cổ vai gáy."),
            new SpecialtyResponse("XK", "Khám Xương Khớp", "Đau khớp, đau lưng, chấn thương thể thao hoặc hạn chế vận động."),
            new SpecialtyResponse("DL", "Khám Da Liễu", "Mụn, ngứa, nổi mẩn, nấm da, rụng tóc hoặc bất thường trên da."),
            new SpecialtyResponse("PK", "Khám Phụ Khoa", "Tư vấn và thăm khám các vấn đề phụ khoa thường gặp."),
            new SpecialtyResponse("HH", "Khám Hô Hấp", "Ho kéo dài, khó thở, khò khè hoặc các vấn đề về phổi."),
            new SpecialtyResponse("MAT", "Khám Mắt", "Mờ mắt, đau mắt, đỏ mắt, cộm ngứa hoặc các bệnh lý về mắt."),
            new SpecialtyResponse("NHI", "Khám Nhi", "Dành cho người đi khám dưới 16 tuổi."),
            new SpecialtyResponse("TMH", "Khám Tai Mũi Họng", "Đau họng, nghẹt mũi, viêm xoang, ù tai hoặc nghe kém."),
            new SpecialtyResponse("NT", "Khám Nội Tiết", "Theo dõi tiểu đường, tuyến giáp và các rối loạn nội tiết."),
            new SpecialtyResponse("TM", "Khám Tim Mạch", "Đau ngực, hồi hộp, khó thở, huyết áp hoặc mỡ máu.")
    );

    public SpecialtyCatalogService() {
        this.repository = null;
    }

    @Autowired
    public SpecialtyCatalogService(SpecialtyCatalogRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "specialties", key = "#query == null ? '' : #query.trim().toLowerCase()")
    public List<SpecialtyResponse> list(String query) {
        List<SpecialtyResponse> source = new ArrayList<>(SPECIALTIES);
        if (repository != null) {
            repository.findByActiveTrueOrderByNameAsc().stream()
                    .map(item -> new SpecialtyResponse(item.getCode(), item.getName(), item.getDescription()))
                    .forEach(item -> {
                        source.removeIf(existing -> existing.code().equalsIgnoreCase(item.code()));
                        source.add(item);
                    });
        }
        if (query == null || query.isBlank()) {
            return source.stream().sorted(java.util.Comparator.comparing(SpecialtyResponse::name)).toList();
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
        requireRepository();
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
        requireRepository();
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
        requireRepository();
        SpecialtyCatalogEntry entry = repository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND",
                        "Chuyên khoa không tồn tại."));
        entry.setActive(false);
        repository.save(entry);
    }

    private void requireRepository() {
        if (repository == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "SPECIALTY_ADMIN_UNAVAILABLE",
                    "Chưa bật quản trị danh mục chuyên khoa.");
        }
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
