package com.clinicone.reporting;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OperationalStatisticsServiceTest {
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final QueueTicketRepository ticketRepository = mock(QueueTicketRepository.class);
    private final ExaminationSessionRepository sessionRepository = mock(ExaminationSessionRepository.class);
    private final OperationalStatisticsService service = new OperationalStatisticsService(
            appointmentRepository, ticketRepository, sessionRepository);

    @Test
    void rejectsRangeLongerThan366Days() {
        assertThatThrownBy(() -> service.summarize(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2),
                "Khám Tổng Quát", null))
                .hasMessageContaining("366 ngày");
        verifyNoInteractions(appointmentRepository, ticketRepository, sessionRepository);
    }

    @Test
    void rejectsUnknownGrouping() {
        assertThatThrownBy(() -> service.summarize(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                "Khám Tổng Quát", null, "YEAR"))
                .hasMessageContaining("ngày, tuần hoặc tháng");
        verifyNoInteractions(appointmentRepository, ticketRepository, sessionRepository);
    }

    @Test
    void calculatesCountsAndDurationsFromRecordedEvents() {
        LocalDate day = LocalDate.of(2026, 8, 10);
        Appointment completed = appointment(AppointmentStatus.COMPLETED);
        Appointment absent = appointment(AppointmentStatus.ABSENT);
        when(appointmentRepository.findBySpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                "Khám Tổng Quát", day, day)).thenReturn(List.of(completed, absent));

        Instant arrived = Instant.parse("2026-08-10T01:00:00Z");
        Instant started = arrived.plusSeconds(15 * 60L);
        Instant ended = started.plusSeconds(30 * 60L);
        QueueTicket ticket = mock(QueueTicket.class);
        when(ticket.getAppointment()).thenReturn(completed);
        when(ticket.getCheckedInAt()).thenReturn(arrived);
        ExaminationSession session = mock(ExaminationSession.class);
        when(session.getAppointment()).thenReturn(completed);
        when(session.getStartedAt()).thenReturn(started);
        when(session.getEndedAt()).thenReturn(ended);
        when(ticketRepository.findByAppointmentIdIn(any())).thenReturn(List.of(ticket));
        when(sessionRepository.findByAppointment_IdIn(any())).thenReturn(List.of(session));

        OperationalStatisticsResponse response = service.summarize(day, day, "Khám Tổng Quát", null);

        assertThat(response.totalAppointments()).isEqualTo(2);
        assertThat(response.checkedInAppointments()).isEqualTo(1);
        assertThat(response.absentAppointments()).isEqualTo(1);
        assertThat(response.completedAppointments()).isEqualTo(1);
        assertThat(response.averageWaitMinutes()).isEqualByComparingTo("15.0");
        assertThat(response.averageExaminationMinutes()).isEqualByComparingTo("30.0");
    }

    @Test
    void groupsResultsByWeekWithoutChangingAggregateCounts() {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        Appointment first = appointment(AppointmentStatus.COMPLETED, monday);
        Appointment second = appointment(AppointmentStatus.ABSENT, monday.plusDays(1));
        when(appointmentRepository.findBySpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                "Khám Tổng Quát", monday, monday.plusDays(1))).thenReturn(List.of(first, second));
        when(ticketRepository.findByAppointmentIdIn(any())).thenReturn(List.of());
        when(sessionRepository.findByAppointment_IdIn(any())).thenReturn(List.of());

        OperationalStatisticsResponse response = service.summarize(monday, monday.plusDays(1), "Khám Tổng Quát", null, "WEEK");

        assertThat(response.groupBy()).isEqualTo("WEEK");
        assertThat(response.buckets()).hasSize(1);
        assertThat(response.buckets().get(0).period()).isEqualTo("2026-08-10");
        assertThat(response.buckets().get(0).totalAppointments()).isEqualTo(2);
    }

    private Appointment appointment(AppointmentStatus status) {
        return appointment(status, LocalDate.of(2026, 8, 10));
    }

    private Appointment appointment(AppointmentStatus status, LocalDate date) {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(UUID.randomUUID());
        when(appointment.getStatus()).thenReturn(status);
        when(appointment.getAppointmentDate()).thenReturn(date);
        return appointment;
    }
}
