package com.clinicone.examination;

import com.clinicone.auth.AuthenticatedIds;
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
        UUID patientId = AuthenticatedIds.patient(accountId);
        return repository.findByAppointment_Patient_IdOrderByCreatedAtDesc(patientId).stream()
                .map(ExaminationSessionResponse::from)
                .toList();
    }

}
