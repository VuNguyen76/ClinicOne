package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class SpecialtyCatalogService {
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

    public List<SpecialtyResponse> list(String query) {
        if (query == null || query.isBlank()) {
            return SPECIALTIES;
        }
        String normalizedQuery = normalize(query);
        return SPECIALTIES.stream()
                .filter(item -> normalize(item.name()).contains(normalizedQuery)
                        || normalize(item.description()).contains(normalizedQuery))
                .toList();
    }

    public SpecialtyResponse require(String name) {
        return SPECIALTIES.stream()
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
}
