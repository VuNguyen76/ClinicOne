package com.clinicone.examination;

import com.clinicone.auth.AuthenticatedIds;
import lombok.RequiredArgsConstructor;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {
    private static final int MAX_PAGE_SIZE = 20;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant HISTORY_START = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant HISTORY_END = Instant.parse("9999-12-31T23:59:59.999999Z");
    private final MedicalRecordRepository repository;

    @Transactional(readOnly = true)
    public MedicalRecordHistoryPage listHistory(String accountId, MedicalRecordHistoryQuery query) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        MedicalRecordHistoryQuery normalized = normalize(query);
        Instant fromAt = normalized.from() == null ? HISTORY_START
                : normalized.from().atStartOfDay(CLINIC_ZONE).toInstant();
        Instant toExclusive = normalized.to() == null ? HISTORY_END
                : normalized.to().plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        PageRequest pageRequest = PageRequest.of(normalized.page(), normalized.size());
        Page<MedicalRecord> records = normalized.profileId() == null
                ? repository.findSignedHistory(patientId, fromAt, toExclusive, pageRequest)
                : repository.findSignedHistoryForProfile(patientId, normalized.profileId(), fromAt, toExclusive,
                        pageRequest);
        return new MedicalRecordHistoryPage(records.getContent().stream().map(MedicalRecordResponse::from).toList(),
                records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse get(String accountId, String recordId) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        UUID id;
        try {
            id = UUID.fromString(recordId);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        return repository.findByIdAndSession_Appointment_Patient_IdAndSignedAtIsNotNull(id, patientId)
                .map(MedicalRecordResponse::from)
                .orElseThrow(this::notFound);
    }

    private MedicalRecordHistoryQuery normalize(MedicalRecordHistoryQuery query) {
        if (query == null) {
            return new MedicalRecordHistoryQuery(null, null, null, 0, MAX_PAGE_SIZE);
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_RECORD_PAGE_INVALID",
                    "Trang phiếu khám không hợp lệ.");
        }
        if ((query.from() == null) != (query.to() == null)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_RECORD_DATE_RANGE_INVALID",
                    "Cần chọn đủ ngày bắt đầu và ngày kết thúc.");
        }
        if (query.from() != null) {
            long days = ChronoUnit.DAYS.between(query.from(), query.to()) + 1;
            if (days < 1 || days > 366) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "MEDICAL_RECORD_DATE_RANGE_INVALID",
                        "Khoảng thời gian xem phiếu khám phải từ 1 đến 366 ngày.");
            }
        }
        return query;
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "MEDICAL_RECORD_NOT_FOUND",
                "Không tìm thấy phiếu khám đã ký.");
    }
}
