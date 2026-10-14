package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.AuthenticatedIds;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DoctorMedicalRecordService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 100;
    private final MedicalRecordRepository repository;

    public DoctorMedicalRecordHistoryPage listHistory(String accountId, DoctorMedicalRecordHistoryQuery query) {
        UUID doctorId = AuthenticatedIds.staff(accountId);
        DoctorMedicalRecordHistoryQuery normalized = normalize(query);
        PageRequest pageRequest = PageRequest.of(normalized.page(), normalized.size());
        Page<MedicalRecord> records = repository.findSignedHistoryForDoctor(doctorId, normalized.search(),
                normalized.from(), normalized.to(), pageRequest);
        return new DoctorMedicalRecordHistoryPage(
                records.getContent().stream().map(DoctorMedicalRecordResponse::fromSummary).toList(),
                records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    public DoctorMedicalRecordResponse get(String accountId, UUID recordId) {
        UUID doctorId = AuthenticatedIds.staff(accountId);
        return repository.findByIdAndSession_Appointment_DoctorStaffIdAndSignedAtIsNotNull(recordId, doctorId)
                .map(DoctorMedicalRecordResponse::from)
                .orElseThrow(this::notFound);
    }

    private DoctorMedicalRecordHistoryQuery normalize(DoctorMedicalRecordHistoryQuery query) {
        if (query == null) {
            return new DoctorMedicalRecordHistoryQuery(null, null, null, 0, 20);
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_MEDICAL_RECORD_PAGE_INVALID",
                    "Trang lịch sử khám không hợp lệ.");
        }
        String search = query.search() == null ? "" : query.search().trim();
        if (search.length() > MAX_SEARCH_LENGTH) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_MEDICAL_RECORD_SEARCH_INVALID",
                    "Nội dung tìm kiếm quá dài.");
        }
        if ((query.from() == null) != (query.to() == null)
                || query.from() != null && ChronoUnit.DAYS.between(query.from(), query.to()) + 1 > 366
                || query.from() != null && query.from().isAfter(query.to())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_MEDICAL_RECORD_DATE_RANGE_INVALID",
                    "Khoảng ngày tìm kiếm không hợp lệ.");
        }
        return new DoctorMedicalRecordHistoryQuery(search, query.from(), query.to(), query.page(), query.size());
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_MEDICAL_RECORD_NOT_FOUND",
                "Không tìm thấy phiếu khám.");
    }
}
