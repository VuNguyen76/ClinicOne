package com.clinicone.schedule;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.notification.PatientNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentLifecycleJobTest {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final PatientNotificationService notificationService = mock(PatientNotificationService.class);
    private final BusinessLogService businessLogService = mock(BusinessLogService.class);

    @Test
    void createsOneTwentyFourHourReminderCandidate() {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        Appointment appointment = appointment(AppointmentStatus.BOOKED, LocalDate.of(2026, 8, 11),
                LocalTime.of(7, 0), now.minusSeconds(48 * 3600L));
        when(appointmentRepository.findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                eq(AppointmentStatus.BOOKED), any(), any())).thenReturn(List.of(appointment));

        AppointmentLifecycleJob.LifecycleJobResult result = job(now).runOnce();

        assertThat(result.reminderCandidates()).isEqualTo(1);
        verify(notificationService).notifyAppointmentReminder(appointment, 24);
        verify(notificationService, never()).notifyAppointmentAbsent(any());
        verifyNoInteractions(businessLogService);
    }

    @Test
    void skipsReminderWhenAppointmentWasCreatedAfterTheReminderThreshold() {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        Instant appointmentAt = LocalDate.of(2026, 8, 11).atTime(7, 0).atZone(CLINIC_ZONE).toInstant();
        Appointment appointment = appointment(AppointmentStatus.BOOKED, LocalDate.of(2026, 8, 11),
                LocalTime.of(7, 0), appointmentAt.minusSeconds(23 * 3600L));
        when(appointmentRepository.findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                eq(AppointmentStatus.BOOKED), any(), any())).thenReturn(List.of(appointment));

        AppointmentLifecycleJob.LifecycleJobResult result = job(now).runOnce();

        assertThat(result.reminderCandidates()).isZero();
        verifyNoInteractions(notificationService);
    }

    @Test
    void doesNotMarkBookedAppointmentAbsentUntilTwentyFourHoursAfterScheduledEnd() {
        Instant now = Instant.parse("2026-08-10T01:15:00Z");
        Appointment appointment = appointment(AppointmentStatus.BOOKED, LocalDate.of(2026, 8, 9),
                LocalTime.of(8, 0), now.minusSeconds(30 * 3600L), 30);
        when(appointmentRepository.findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                eq(AppointmentStatus.BOOKED), any(), any())).thenReturn(List.of(appointment));

        AppointmentLifecycleJob.LifecycleJobResult result = job(now).runOnce();

        assertThat(result.absentTransitions()).isZero();
        verify(appointment, never()).markAbsent();
        verifyNoInteractions(businessLogService);
        verify(notificationService).notifyAppointmentLate(appointment);
    }

    @Test
    void marksBookedAppointmentAbsentTwentyFourHoursAfterScheduledEnd() {
        Instant now = Instant.parse("2026-08-10T01:45:00Z");
        Appointment appointment = appointment(AppointmentStatus.BOOKED, LocalDate.of(2026, 8, 9),
                LocalTime.of(8, 0), now.minusSeconds(30 * 3600L), 30);
        AtomicBoolean marked = new AtomicBoolean();
        when(appointment.getStatus()).thenAnswer(invocation -> marked.get()
                ? AppointmentStatus.ABSENT : AppointmentStatus.BOOKED);
        doAnswer(invocation -> {
            marked.set(true);
            return null;
        }).when(appointment).markAbsent();
        when(appointmentRepository.findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                eq(AppointmentStatus.BOOKED), any(), any())).thenReturn(List.of(appointment));
        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        AppointmentLifecycleJob.LifecycleJobResult result = job(now).runOnce();

        assertThat(result.absentTransitions()).isEqualTo(1);
        verify(appointment).markAbsent();
        verify(businessLogService).recordTransition(any(), eq("APPOINTMENT"), any(), eq("BOOKED"),
                eq("ABSENT"), eq("MARK_ABSENT"), eq("SYSTEM"), any());
        verify(notificationService).notifyAppointmentAbsent(appointment);
    }

    @Test
    void skipsAbsentTransitionWhenAppointmentWasChangedBeforeTheLifecycleLock() {
        Instant now = Instant.parse("2026-08-10T01:45:00Z");
        Appointment listed = appointment(AppointmentStatus.BOOKED, LocalDate.of(2026, 8, 9),
                LocalTime.of(8, 0), now.minusSeconds(30 * 3600L), 30);
        UUID appointmentId = listed.getId();
        Appointment current = mock(Appointment.class);
        when(current.getId()).thenReturn(appointmentId);
        when(current.getStatus()).thenReturn(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                eq(AppointmentStatus.BOOKED), any(), any())).thenReturn(List.of(listed));
        when(appointmentRepository.findByIdForUpdate(appointmentId)).thenReturn(Optional.of(current));

        AppointmentLifecycleJob.LifecycleJobResult result = job(now).runOnce();

        assertThat(result.absentTransitions()).isZero();
        verify(current, never()).markAbsent();
        verify(businessLogService, never()).recordTransition(any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationService).notifyAppointmentLate(listed);
    }

    @Test
    void warnsLateFifteenMinutesAfterScheduledEnd() {
        Instant now = Instant.parse("2026-08-10T01:45:00Z");
        Appointment appointment = appointment(AppointmentStatus.BOOKED, LocalDate.of(2026, 8, 10),
                LocalTime.of(8, 0), now.minusSeconds(48 * 3600L), 30);
        when(appointmentRepository.findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                eq(AppointmentStatus.BOOKED), any(), any())).thenReturn(List.of(appointment));

        AppointmentLifecycleJob.LifecycleJobResult result = job(now).runOnce();

        assertThat(result.lateWarningCandidates()).isEqualTo(1);
        assertThat(result.absentTransitions()).isZero();
        verify(notificationService).notifyAppointmentLate(appointment);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void scheduledEntryPointKeepsLifecycleWorkInsideATransaction() throws Exception {
        assertThat(AppointmentLifecycleJob.class.getMethod("runScheduled")
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private AppointmentLifecycleJob job(Instant now) {
        return new AppointmentLifecycleJob(appointmentRepository, notificationService, businessLogService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private Appointment appointment(AppointmentStatus status, LocalDate date, LocalTime time, Instant createdAt) {
        return appointment(status, date, time, createdAt, null);
    }

    private Appointment appointment(AppointmentStatus status, LocalDate date, LocalTime time, Instant createdAt,
                                    Integer durationMinutes) {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(UUID.randomUUID());
        when(appointment.getStatus()).thenReturn(status);
        when(appointment.getAppointmentDate()).thenReturn(date);
        when(appointment.getStartTime()).thenReturn(time);
        when(appointment.getCreatedAt()).thenReturn(createdAt);
        when(appointment.getServiceDurationMinutes()).thenReturn(durationMinutes);
        return appointment;
    }
}
