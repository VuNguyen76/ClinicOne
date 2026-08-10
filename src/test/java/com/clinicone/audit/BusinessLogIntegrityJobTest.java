package com.clinicone.audit;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.reconciliation.ReconciliationIncidentRepository;
import com.clinicone.reconciliation.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessLogIntegrityJobTest {
    private final BusinessLogRepository businessLogRepository = mock(BusinessLogRepository.class);
    private final ReconciliationIncidentRepository incidentRepository = mock(ReconciliationIncidentRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final QueueTicketRepository queueTicketRepository = mock(QueueTicketRepository.class);
    private final ExaminationSessionRepository examinationSessionRepository = mock(ExaminationSessionRepository.class);
    private BusinessLogIntegrityJob job;

    @BeforeEach
    void setUp() {
        job = new BusinessLogIntegrityJob(businessLogRepository, incidentRepository, appointmentRepository,
                queueTicketRepository, examinationSessionRepository,
                Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneOffset.UTC));
        when(incidentRepository.existsByEntityTypeAndEntityIdAndStatus(any(), any(), any())).thenReturn(false);
    }

    @Test
    void opensIncidentWhenCurrentStateDiffersFromLatestJournal() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        BusinessLog entry = BusinessLog.transition(eventId, "APPOINTMENT", appointmentId,
                "BOOKED", "CHECKED_IN", "CHECK_IN", "patient", null);
        PatientAccount patient = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        Appointment appointment = Appointment.create(patient, "CL-1", "Nội tổng quát", "BS. An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        setId(appointment, appointmentId);
        when(businessLogRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        BusinessLogIntegrityJob.IntegrityCheckResult result = job.runOnce();

        assertEquals(1, result.inspected());
        assertEquals(1, result.incidentsOpened());
        verify(incidentRepository).save(any());
    }

    @Test
    void doesNotOpenDuplicateIncidentForAlreadyOpenEntity() {
        UUID id = UUID.randomUUID();
        BusinessLog entry = BusinessLog.transition(UUID.randomUUID(), "APPOINTMENT", id,
                "BOOKED", "CHECKED_IN", "CHECK_IN", "patient", null);
        when(businessLogRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());
        when(incidentRepository.existsByEntityTypeAndEntityIdAndStatus("APPOINTMENT", id,
                ReconciliationStatus.OPEN)).thenReturn(true);

        BusinessLogIntegrityJob.IntegrityCheckResult result = job.runOnce();

        assertEquals(1, result.inspected());
        assertEquals(0, result.incidentsOpened());
        verify(incidentRepository, never()).save(any());
    }

    private static void setId(Appointment appointment, UUID id) throws Exception {
        Field field = Appointment.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(appointment, id);
    }
}
