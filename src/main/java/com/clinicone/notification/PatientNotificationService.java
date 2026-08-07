package com.clinicone.notification;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PatientNotificationService {
    private final PatientNotificationRepository repository;

    public PatientNotificationService(PatientNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PatientNotificationResponse> list(String accountId) {
        UUID patientId = parseAccountId(accountId);
        return repository.findByPatientAccountIdOrderByCreatedAtDesc(patientId).stream()
                .map(PatientNotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String accountId) {
        return repository.countByPatientAccountIdAndReadAtIsNull(parseAccountId(accountId));
    }

    @Transactional
    public void markRead(String accountId, String notificationId) {
        UUID patientId = parseAccountId(accountId);
        UUID id;
        try {
            id = UUID.fromString(notificationId);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        PatientNotification notification = repository.findByIdAndPatientAccountId(id, patientId)
                .orElseThrow(this::notFound);
        notification.markRead();
        repository.save(notification);
    }

    @Transactional
    public void notifyMedicalRecordSigned(UUID patientId, UUID recordId, String appointmentCode,
                                          String doctorName, String specialty) {
        if (patientId == null || recordId == null) {
            return;
        }
        String eventKey = "MEDICAL_RECORD_SIGNED:" + recordId;
        if (!repository.existsByEventKey(eventKey)) {
            repository.save(PatientNotification.recordSigned(patientId, recordId, appointmentCode, doctorName, specialty));
        }
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
        return new AuthException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                "Không tìm thấy thông báo.");
    }
}
