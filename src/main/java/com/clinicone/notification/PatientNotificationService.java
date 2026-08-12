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
    private final SmsDeliveryService smsDeliveryService;

    public PatientNotificationService(PatientNotificationRepository repository) {
        this(repository, null, (SmsSender) null, (SmsDeliveryService) null);
    }

    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository, SmsSender smsSender) {
        this(repository, accountRepository, smsSender, null);
    }

    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository, SmsSender smsSender,
                                      SmsDeliveryService smsDeliveryService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.smsSender = smsSender;
        this.smsDeliveryService = smsDeliveryService;
    }

    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository,
                                      ObjectProvider<SmsSender> smsSenders) {
        this(repository, accountRepository, smsSenders.getIfAvailable(), null);
    }

    @Autowired
    public PatientNotificationService(PatientNotificationRepository repository,
                                      PatientAccountRepository accountRepository,
                                      ObjectProvider<SmsSender> smsSenders,
                                      ObjectProvider<SmsDeliveryService> smsDeliveries) {
        this(repository, accountRepository, smsSenders.getIfAvailable(), smsDeliveries.getIfAvailable());
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

    private void saveOnce(PatientNotification notification) {
        PatientNotification saved = repository.findByEventKey(notification.getEventKey())
                .orElseGet(() -> repository.save(notification));
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
                                smsMessage(account.getStatus(), notification));
                    } else if (smsSender != null) {
                        try {
                            smsSender.sendText(phone, notification.getMessage());
                        } catch (RuntimeException ignored) {
                            // Legacy direct sender is best-effort; production uses the outbox worker.
                        }
                    }
                });
    }

    private String smsMessage(com.clinicone.auth.AccountStatus status, PatientNotification notification) {
        if (status == com.clinicone.auth.AccountStatus.LOCKED) {
            return "ClinicOne: Bạn có thông báo mới. Vui lòng mở khóa tài khoản để xem trong ứng dụng.";
        }
        if (notification.getType() == PatientNotificationType.MEDICAL_RECORD_SIGNED) {
            return "ClinicOne: Kết quả khám đã sẵn sàng. Mở ứng dụng ClinicOne để xem.";
        }
        return "ClinicOne: " + notification.getTitle() + ". " + notification.getMessage()
                + " Mở ứng dụng ClinicOne để xem.";
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
