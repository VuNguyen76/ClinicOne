package com.clinicone.notification;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import com.clinicone.appointment.Appointment;

@Service
public class PatientNotificationService {
    private final PatientNotificationRepository repository;
    private final PatientAccountRepository accountRepository;
    private final SmsSender smsSender;

    public PatientNotificationService(PatientNotificationRepository repository) {
        this(repository, null, (SmsSender) null);
    }

    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository, SmsSender smsSender) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.smsSender = smsSender;
    }

    @Autowired
    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository,
                                      ObjectProvider<SmsSender> smsSenders) {
        this(repository, accountRepository, smsSenders.getIfAvailable());
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

    @Transactional
    public void notifyAppointmentCreated(Appointment appointment) {
        saveOnce(PatientNotification.appointmentCreated(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode(), appointment.getSpecialty(), appointment.getDoctorName(),
                appointment.getAppointmentDate().toString(), appointment.getStartTime().toString()));
    }

    @Transactional
    public void notifyAppointmentCancelled(Appointment appointment) {
        saveOnce(PatientNotification.appointmentCancelled(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode()));
    }

    @Transactional
    public void notifyAppointmentRescheduled(Appointment appointment, String previousDate, String previousTime) {
        saveOnce(PatientNotification.appointmentRescheduled(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode(), appointment.getAppointmentDate().toString(),
                appointment.getStartTime().toString(), previousDate, previousTime));
    }

    private void saveOnce(PatientNotification notification) {
        if (!repository.existsByEventKey(notification.getEventKey())) {
            PatientNotification saved = repository.save(notification);
            sendSmsBestEffort(saved);
        }
    }

    private void sendSmsBestEffort(PatientNotification notification) {
        if (smsSender == null || accountRepository == null) {
            return;
        }
        accountRepository.findById(notification.getPatientAccountId())
                .map(account -> account.getPhone())
                .filter(phone -> phone != null && !phone.isBlank())
                .ifPresent(phone -> {
                    try {
                        smsSender.sendText(phone, notification.getMessage());
                    } catch (RuntimeException ignored) {
                        // SMS is a best-effort channel; the in-app notification remains authoritative.
                    }
                });
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
