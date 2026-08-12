package com.clinicone.notification;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class PatientNotificationServiceTest {
    private final PatientNotificationRepository repository = mock(PatientNotificationRepository.class);
    private final PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
    private final SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
    private final PatientNotificationService service = new PatientNotificationService(repository);

    @Test
    void createsOnlyOneNotificationForSignedRecordEvent() {
        UUID patientId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        when(repository.existsByEventKey("MEDICAL_RECORD_SIGNED:" + recordId)).thenReturn(false);
        service.notifyMedicalRecordSigned(patientId, recordId, "CL-001", "Bác sĩ An", "Nội khoa");

        verify(repository).save(any(PatientNotification.class));
    }

    @Test
    void doesNotCreateNotificationForMissingRecordId() {
        service.notifyMedicalRecordSigned(UUID.randomUUID(), null, "CL-001", "Bác sĩ An", "Nội khoa");

        verify(repository, never()).existsByEventKey(any());
        verify(repository, never()).save(any(PatientNotification.class));
    }

    @Test
    void stillStoresInAppNotificationWhenOutboxBeanIsUnavailable() {
        PatientNotificationService notificationService = new PatientNotificationService(
                repository, accountRepository, (SmsDeliveryService) null);
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, patientId);
        when(repository.findByEventKey("APPOINTMENT_CREATED:" + appointmentId)).thenReturn(Optional.empty());
        when(repository.save(any(PatientNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(patientId)).thenReturn(Optional.of(account));

        var appointment = com.clinicone.appointment.Appointment.create(account, appointmentId,
                "CL-001", "Nội khoa", "Bác sĩ An", java.time.LocalDate.of(2026, 8, 8),
                java.time.LocalTime.of(8, 30), "Đau đầu");
        setAppointmentId(appointment, appointmentId);
        notificationService.notifyAppointmentCreated(appointment);

        verify(repository).save(any(PatientNotification.class));
    }

    @Test
    void enqueuesAppointmentNotificationWhenPhoneIsAvailable() {
        PatientNotificationService smsService = serviceWithOutbox();
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, patientId);
        when(repository.existsByEventKey("APPOINTMENT_CREATED:" + appointmentId)).thenReturn(false);
        when(repository.save(any(PatientNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(patientId)).thenReturn(Optional.of(account));

        var appointment = com.clinicone.appointment.Appointment.create(account, appointmentId,
                "CL-001", "Nội khoa", "Bác sĩ An", java.time.LocalDate.of(2026, 8, 8),
                java.time.LocalTime.of(8, 30), "Đau đầu");
        setAppointmentId(appointment, appointmentId);
        smsService.notifyAppointmentCreated(appointment);

        verify(smsDeliveryService).enqueue(patientId, "APPOINTMENT_CREATED:" + appointmentId, "0912345678",
                "ClinicOne: Đặt lịch thành công. Lịch hẹn CL-001 với Bác sĩ An đã được ghi nhận vào 2026-08-08 lúc 08:30. Mở ứng dụng ClinicOne để xem.");
    }

    @Test
    void rechecksOutboxForExistingNotificationEvent() {
        PatientNotificationService smsService = serviceWithOutbox();
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, patientId);
        PatientNotification existing = PatientNotification.appointmentCreated(patientId, appointmentId,
                "CL-001", "Nội khoa", "Bác sĩ An", "2026-08-08", "08:30");
        when(repository.findByEventKey("APPOINTMENT_CREATED:" + appointmentId)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(patientId)).thenReturn(Optional.of(account));

        var appointment = com.clinicone.appointment.Appointment.create(account, appointmentId,
                "CL-001", "Nội khoa", "Bác sĩ An", java.time.LocalDate.of(2026, 8, 8),
                java.time.LocalTime.of(8, 30), "Đau đầu");
        setAppointmentId(appointment, appointmentId);
        smsService.notifyAppointmentCreated(appointment);

        verify(repository, never()).save(any(PatientNotification.class));
        verify(smsDeliveryService).enqueue(patientId, existing.getEventKey(), "0912345678",
                "ClinicOne: Đặt lịch thành công. Lịch hẹn CL-001 với Bác sĩ An đã được ghi nhận vào 2026-08-08 lúc 08:30. Mở ứng dụng ClinicOne để xem.");
    }

    @Test
    void hidesAppointmentDetailsUntilPatientCompletesActivation() {
        PatientNotificationService smsService = serviceWithOutbox();
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, true);
        setId(account, patientId);
        when(repository.existsByEventKey("APPOINTMENT_CREATED:" + appointmentId)).thenReturn(false);
        when(repository.save(any(PatientNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findById(patientId)).thenReturn(Optional.of(account));

        var appointment = com.clinicone.appointment.Appointment.create(account, appointmentId,
                "CL-001", "Nội khoa", "Bác sĩ An", java.time.LocalDate.of(2026, 8, 8),
                java.time.LocalTime.of(8, 30), "Đau đầu");
        setAppointmentId(appointment, appointmentId);
        smsService.notifyAppointmentCreated(appointment);

        verify(smsDeliveryService).enqueue(patientId, "APPOINTMENT_CREATED:" + appointmentId, "0912345678",
                "ClinicOne: Bạn có thông báo mới. Vui lòng hoàn tất kích hoạt tài khoản để xem trong ứng dụng.");
    }

    @Test
    void masksInAppNotificationUntilPatientCompletesActivation() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, true);
        setId(account, patientId);
        PatientNotification notification = PatientNotification.appointmentCreated(patientId, appointmentId,
                "CL-001", "Nội khoa", "Bác sĩ An", "2026-08-08", "08:30");
        when(accountRepository.findById(patientId)).thenReturn(Optional.of(account));
        when(repository.findByPatientAccountIdOrderByCreatedAtDesc(patientId)).thenReturn(java.util.List.of(notification));

        PatientNotificationService notificationService = serviceWithOutbox();
        PatientNotificationResponse response = notificationService.list(patientId.toString()).get(0);

        org.assertj.core.api.Assertions.assertThat(response.title()).isEqualTo("Bạn có thông báo mới");
        org.assertj.core.api.Assertions.assertThat(response.message()).contains("hoàn tất kích hoạt")
                .doesNotContain("Bác sĩ An", "2026-08-08", "08:30");
        org.assertj.core.api.Assertions.assertThat(response.targetUrl()).isNull();
    }

    private PatientNotificationService serviceWithOutbox() {
        return new PatientNotificationService(repository, accountRepository, smsDeliveryService);
    }

    private static void setId(PatientAccount account, UUID id) {
        try {
            var field = PatientAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setAppointmentId(com.clinicone.appointment.Appointment appointment, UUID id) {
        try {
            var field = com.clinicone.appointment.Appointment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(appointment, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
