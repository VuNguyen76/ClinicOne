package com.clinicone.examination;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExaminationSessionService {
    private final ExaminationSessionRepository repository;

    @Transactional(readOnly = true)
    public List<ExaminationSessionResponse> list(String accountId) {
        UUID patientId = parseAccountId(accountId);
        return repository.findByAppointment_Patient_IdOrderByCreatedAtDesc(patientId).stream()
                .map(ExaminationSessionResponse::from)
                .toList();
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập không hợp lệ.");
        }
    }
}
