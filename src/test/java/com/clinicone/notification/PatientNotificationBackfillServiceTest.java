package com.clinicone.notification;

import com.clinicone.appointment.Appointment;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PatientNotificationBackfillServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    private final MedicalRecordRepository recordRepository = mock(MedicalRecordRepository.class);
    private final PatientNotificationService notificationService = mock(PatientNotificationService.class);
    private final PatientNotificationBackfillService service = new PatientNotificationBackfillService(
            recordRepository, notificationService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void notifiesSignedRecordsForLinkedProfileFromLastThirtyDays() {
        UUID patientId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        MedicalRecord record = mock(MedicalRecord.class);
        ExaminationSession session = mock(ExaminationSession.class);
        Appointment appointment = mock(Appointment.class);
        when(record.getId()).thenReturn(recordId);
        when(record.getSession()).thenReturn(session);
        when(session.getAppointment()).thenReturn(appointment);
        when(appointment.getAppointmentCode()).thenReturn("APT-001");
        when(appointment.getDoctorName()).thenReturn("Bác sĩ An");
        when(appointment.getSpecialty()).thenReturn("Nội khoa");
        when(recordRepository.findSignedForProfileSince(eq(patientId), eq(profileId),
                eq(NOW.minusSeconds(30L * 24 * 60 * 60))))
                .thenReturn(List.of(record));

        service.notifyRecentSignedRecords(patientId, profileId);

        verify(notificationService).notifyMedicalRecordSigned(patientId, recordId,
                "APT-001", "Bác sĩ An", "Nội khoa");
    }

    @Test
    void doesNothingWhenProfileHasNoRecentSignedRecords() {
        UUID patientId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(recordRepository.findSignedForProfileSince(eq(patientId), eq(profileId),
                eq(NOW.minusSeconds(30L * 24 * 60 * 60))))
                .thenReturn(List.of());

        service.notifyRecentSignedRecords(patientId, profileId);

        verifyNoInteractions(notificationService);
    }
}
