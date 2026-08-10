package com.clinicone.reporting;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OperationalStatisticsService {
    private static final int MAX_RANGE_DAYS = 366;
    private static final Set<String> GROUPINGS = Set.of("DAY", "WEEK", "MONTH");

    private final AppointmentRepository appointmentRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final ExaminationSessionRepository examinationSessionRepository;

    public OperationalStatisticsService(AppointmentRepository appointmentRepository,
                                        QueueTicketRepository queueTicketRepository,
                                        ExaminationSessionRepository examinationSessionRepository) {
        this.appointmentRepository = appointmentRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.examinationSessionRepository = examinationSessionRepository;
    }

    @Transactional(readOnly = true)
    public OperationalStatisticsResponse summarize(LocalDate from, LocalDate to, String specialty, UUID doctorId) {
        return summarize(from, to, specialty, doctorId, "DAY");
    }

    @Transactional(readOnly = true)
    public OperationalStatisticsResponse summarize(LocalDate from, LocalDate to, String specialty, UUID doctorId,
                                                   String groupBy) {
        validateRange(from, to);
        String normalizedSpecialty = specialty == null ? "" : specialty.trim();
        if (normalizedSpecialty.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STATISTICS_SPECIALTY_REQUIRED",
                    "Cần chọn chuyên khoa để xem thống kê.");
        }
        String normalizedGroupBy = normalizeGrouping(groupBy);

        List<Appointment> appointments = doctorId == null
                ? appointmentRepository.findBySpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                        normalizedSpecialty, from, to)
                : appointmentRepository.findByDoctorStaffIdAndSpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                        doctorId, normalizedSpecialty, from, to);
        List<UUID> appointmentIds = appointments.stream().map(Appointment::getId).filter(Objects::nonNull).toList();
        List<QueueTicket> tickets = appointmentIds.isEmpty()
                ? List.of()
                : queueTicketRepository.findByAppointmentIdIn(appointmentIds);
        List<ExaminationSession> sessions = appointmentIds.isEmpty()
                ? List.of()
                : examinationSessionRepository.findByAppointment_IdIn(appointmentIds);

        StatisticsMetrics aggregate = metrics(appointments, tickets, sessions);
        List<OperationalStatisticsBucket> buckets = buildBuckets(appointments, tickets, sessions, normalizedGroupBy);
        return new OperationalStatisticsResponse(from, to, normalizedSpecialty, doctorId,
                aggregate.totalAppointments(), aggregate.checkedInAppointments(), aggregate.absentAppointments(),
                aggregate.cancelledAppointments(), aggregate.completedAppointments(), aggregate.notPerformedAppointments(),
                aggregate.averageWaitMinutes(), aggregate.averageExaminationMinutes(), normalizedGroupBy, buckets);
    }

    private List<OperationalStatisticsBucket> buildBuckets(List<Appointment> appointments, List<QueueTicket> tickets,
                                                           List<ExaminationSession> sessions, String groupBy) {
        Map<String, List<Appointment>> grouped = appointments.stream()
                .filter(appointment -> appointment.getAppointmentDate() != null)
                .collect(Collectors.groupingBy(appointment -> periodKey(appointment.getAppointmentDate(), groupBy),
                        LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Set<UUID> ids = entry.getValue().stream().map(Appointment::getId).filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    StatisticsMetrics metrics = metrics(entry.getValue(), filterTickets(tickets, ids),
                            filterSessions(sessions, ids));
                    return new OperationalStatisticsBucket(entry.getKey(), metrics.totalAppointments(),
                            metrics.checkedInAppointments(), metrics.absentAppointments(), metrics.cancelledAppointments(),
                            metrics.completedAppointments(), metrics.notPerformedAppointments(), metrics.averageWaitMinutes(),
                            metrics.averageExaminationMinutes());
                }).toList();
    }

    private String periodKey(LocalDate date, String groupBy) {
        return switch (groupBy) {
            case "WEEK" -> date.with(DayOfWeek.MONDAY).toString();
            case "MONTH" -> YearMonth.from(date).toString();
            default -> date.toString();
        };
    }

    private List<QueueTicket> filterTickets(List<QueueTicket> tickets, Set<UUID> ids) {
        return tickets.stream().filter(ticket -> ticket.getAppointment() != null
                && ticket.getAppointment().getId() != null && ids.contains(ticket.getAppointment().getId())).toList();
    }

    private List<ExaminationSession> filterSessions(List<ExaminationSession> sessions, Set<UUID> ids) {
        return sessions.stream().filter(session -> session.getAppointment() != null
                && session.getAppointment().getId() != null && ids.contains(session.getAppointment().getId())).toList();
    }

    private StatisticsMetrics metrics(List<Appointment> appointments, List<QueueTicket> tickets,
                                     List<ExaminationSession> sessions) {
        long checkedIn = appointments.stream().filter(appointment -> appointment.getStatus() == AppointmentStatus.CHECKED_IN
                || appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.NOT_PERFORMED).count();
        long absent = countStatus(appointments, AppointmentStatus.ABSENT);
        long cancelled = countStatus(appointments, AppointmentStatus.CANCELLED);
        long completed = countStatus(appointments, AppointmentStatus.COMPLETED);
        long notPerformed = countStatus(appointments, AppointmentStatus.NOT_PERFORMED);
        return new StatisticsMetrics(appointments.size(), checkedIn, absent, cancelled, completed, notPerformed,
                averageWaitMinutes(tickets, sessions), averageExaminationMinutes(sessions));
    }

    private String normalizeGrouping(String groupBy) {
        String normalized = groupBy == null || groupBy.isBlank() ? "DAY" : groupBy.trim().toUpperCase(Locale.ROOT);
        if (!GROUPINGS.contains(normalized)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STATISTICS_GROUP_INVALID",
                    "Cách nhóm thống kê chỉ gồm ngày, tuần hoặc tháng.");
        }
        return normalized;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STATISTICS_DATE_RANGE_INVALID",
                    "Khoảng thời gian phải từ 1 đến 366 ngày và ngày bắt đầu không được sau ngày kết thúc.");
        }
    }

    private long countStatus(List<Appointment> appointments, AppointmentStatus status) {
        return appointments.stream().filter(appointment -> appointment.getStatus() == status).count();
    }

    private BigDecimal averageWaitMinutes(List<QueueTicket> tickets, List<ExaminationSession> sessions) {
        Map<UUID, java.time.Instant> starts = sessions.stream().filter(session -> session.getAppointment() != null
                        && session.getAppointment().getId() != null && session.getStartedAt() != null)
                .collect(Collectors.toMap(session -> session.getAppointment().getId(), ExaminationSession::getStartedAt,
                        (first, ignored) -> first));
        List<Double> values = tickets.stream().filter(ticket -> ticket.getAppointment() != null
                        && ticket.getAppointment().getId() != null && ticket.getCheckedInAt() != null)
                .map(ticket -> {
                    var startedAt = starts.get(ticket.getAppointment().getId());
                    if (startedAt == null || startedAt.isBefore(ticket.getCheckedInAt())) return null;
                    return Duration.between(ticket.getCheckedInAt(), startedAt).toMillis() / 60000d;
                }).filter(Objects::nonNull).toList();
        return average(values);
    }

    private BigDecimal averageExaminationMinutes(List<ExaminationSession> sessions) {
        List<Double> values = sessions.stream()
                .filter(session -> session.getStartedAt() != null && session.getEndedAt() != null
                        && !session.getEndedAt().isBefore(session.getStartedAt()))
                .map(session -> Duration.between(session.getStartedAt(), session.getEndedAt()).toMillis() / 60000d)
                .toList();
        return average(values);
    }

    private BigDecimal average(List<Double> values) {
        if (values.isEmpty()) return null;
        double value = values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private record StatisticsMetrics(long totalAppointments, long checkedInAppointments, long absentAppointments,
                                     long cancelledAppointments, long completedAppointments, long notPerformedAppointments,
                                     BigDecimal averageWaitMinutes, BigDecimal averageExaminationMinutes) {
    }
}
