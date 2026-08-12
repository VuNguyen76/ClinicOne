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
    private final SmsSender smsSender = mock(SmsSender.class);
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
    void sendsAppointmentNotificationBySmsWhenPhoneIsAvailable() {
        PatientNotificationService smsService = new PatientNotificationService(repository, accountRepository, smsSender);
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

        verify(smsSender).sendText("0912345678", "Lịch hẹn CL-001 với Bác sĩ An đã được ghi nhận vào 2026-08-08 lúc 08:30.");
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
