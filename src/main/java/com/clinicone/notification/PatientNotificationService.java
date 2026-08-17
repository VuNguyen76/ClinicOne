package com.clinicone.notification;

import com.clinicone.auth.AuthenticatedIds;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import com.clinicone.appointment.Appointment;

@Service
public class PatientNotificationService {
    private final PatientNotificationRepository repository;
    private final PatientAccountRepository accountRepository;
    private final SmsDeliveryService smsDeliveryService;

    public PatientNotificationService(PatientNotificationRepository repository) {
        this(repository, null, (SmsDeliveryService) null);
    }

    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository,
                                      SmsDeliveryService smsDeliveryService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.smsDeliveryService = smsDeliveryService;
    }

    @Autowired
    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository,
                                      ObjectProvider<SmsDeliveryService> smsDeliveries) {
        this(repository, accountRepository, smsDeliveries.getIfAvailable());
    }

    @Transactional(readOnly = true)
    public List<PatientNotificationResponse> list(String accountId) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        com.clinicone.auth.PatientAccount account = accountRepository == null
                ? null : accountRepository.findById(patientId).orElse(null);
        boolean locked = account != null && account.getStatus() == com.clinicone.auth.AccountStatus.LOCKED;
        boolean pendingActivation = account != null && account.isMustChangePassword();
        String guidance = locked
                ? "Vui lòng mở khóa tài khoản để xem trong ứng dụng."
                : "Vui lòng hoàn tất kích hoạt tài khoản để xem trong ứng dụng.";
        return repository.findByPatientAccountIdOrderByCreatedAtDesc(patientId).stream()
                .map(notification -> locked || pendingActivation
                        ? PatientNotificationResponse.restricted(notification, guidance)
                        : PatientNotificationResponse.from(notification))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String accountId) {
        return repository.countByPatientAccountIdAndReadAtIsNull(AuthenticatedIds.patient(accountId));
    }

    @Transactional
    public void markRead(String accountId, String notificationId) {
        UUID patientId = AuthenticatedIds.patient(accountId);
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
        saveOnce(PatientNotification.recordSigned(patientId, recordId, appointmentCode, doctorName, specialty));
    }

    @Transactional
    public void notifyAppointmentCreated(Appointment appointment) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentCreated(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode(), appointment.getSpecialty(), appointment.getDoctorName(),
                appointment.getAppointmentDate().toString(), appointment.getStartTime().toString()));
    }

    @Transactional
    public void notifyAppointmentCancelled(Appointment appointment) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentCancelled(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode()));
    }

    @Transactional
    public void notifyAppointmentRescheduled(Appointment appointment, String previousDate, String previousTime) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentRescheduled(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode(), appointment.getAppointmentDate().toString(),
                appointment.getStartTime().toString(), previousDate, previousTime));
    }

    @Transactional
    public void notifyAppointmentRescheduleRequired(Appointment appointment) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentRescheduleRequired(appointment.getPatient().getId(),
                appointment.getId(), appointment.getAppointmentCode()));
    }

    @Transactional
    public void notifyAppointmentReminder(Appointment appointment, int hours) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentReminder(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode(), appointment.getSpecialty(), appointment.getDoctorName(),
                appointment.getAppointmentDate().toString(), appointment.getStartTime().toString(), hours));
    }

    @Transactional
    public void notifyAppointmentLate(Appointment appointment) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentLateWarning(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode()));
    }

    @Transactional
    public void notifyAppointmentAbsent(Appointment appointment) {
        if (appointment.getPatient() == null) return;
        saveOnce(PatientNotification.appointmentAbsent(appointment.getPatient().getId(), appointment.getId(),
                appointment.getAppointmentCode()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyAccountSecurityLocked(UUID patientId, Instant lockedUntil) {
        if (patientId == null || lockedUntil == null) return;
        saveOnce(PatientNotification.accountSecurityLocked(patientId, lockedUntil));
    }

    private void saveOnce(PatientNotification notification) {
        Optional<PatientNotification> existing = repository.findByEventKey(notification.getEventKey());
        if (existing.isPresent()) {
            // The outbox may still need to be created after a previous transaction committed
            // the in-app notification, but the legacy direct sender must never resend it.
            if (smsDeliveryService != null) {
                enqueueSms(existing.get());
            }
            return;
        }
        PatientNotification saved = repository.save(notification);
        enqueueSms(saved);
    }

    private void enqueueSms(PatientNotification notification) {
        if (accountRepository == null) {
            return;
        }
        accountRepository.findById(notification.getPatientAccountId())
                .ifPresent(account -> {
                    String phone = account.getPhone();
                    if (phone == null || phone.isBlank()) return;
                    if (smsDeliveryService != null) {
                        smsDeliveryService.enqueue(account.getId(), notification.getEventKey(), phone,
                                smsMessage(account.getStatus(), account.isMustChangePassword(), notification));
                    }
                });
    }

    private String smsMessage(com.clinicone.auth.AccountStatus status, boolean mustChangePassword,
                              PatientNotification notification) {
        if (status == com.clinicone.auth.AccountStatus.LOCKED) {
            return "ClinicOne: Bạn có thông báo mới. Vui lòng mở khóa tài khoản để xem trong ứng dụng.";
        }
        if (mustChangePassword) {
            return "ClinicOne: Bạn có thông báo mới. Vui lòng hoàn tất kích hoạt tài khoản để xem trong ứng dụng.";
        }
        if (notification.getType() == PatientNotificationType.MEDICAL_RECORD_SIGNED) {
            return "ClinicOne: Kết quả khám đã sẵn sàng. Mở ứng dụng ClinicOne để xem.";
        }
        return "ClinicOne: " + notification.getTitle() + ". " + notification.getMessage()
                + " Mở ứng dụng ClinicOne để xem.";
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                "Không tìm thấy thông báo.");
    }
}
