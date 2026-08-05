package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MedicalRecordService {
    private final MedicalRecordRepository repository;

    public MedicalRecordService(MedicalRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> list(String accountId) {
        UUID patientId = parseAccountId(accountId);
        return repository.findBySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(patientId).stream()
                .map(MedicalRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse get(String accountId, String recordId) {
        UUID patientId = parseAccountId(accountId);
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

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập không hợp lệ.");
        }
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "MEDICAL_RECORD_NOT_FOUND",
                "Không tìm thấy phiếu khám đã ký.");
    }
}
