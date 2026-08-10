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
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class OperationalStatisticsService {
    private static final int MAX_RANGE_DAYS = 366;

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
        validateRange(from, to);
        String normalizedSpecialty = specialty == null ? "" : specialty.trim();
        if (normalizedSpecialty.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STATISTICS_SPECIALTY_REQUIRED",
                    "Cần chọn chuyên khoa để xem thống kê.");
        }

        List<Appointment> appointments = doctorId == null
                ? appointmentRepository.findBySpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                        normalizedSpecialty, from, to)
                : appointmentRepository.findByDoctorStaffIdAndSpecialtyIgnoreCaseAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                        doctorId, normalizedSpecialty, from, to);

        long checkedIn = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CHECKED_IN
                        || appointment.getStatus() == AppointmentStatus.COMPLETED
                        || appointment.getStatus() == AppointmentStatus.NOT_PERFORMED)
                .count();
        long absent = countStatus(appointments, AppointmentStatus.ABSENT);
        long cancelled = countStatus(appointments, AppointmentStatus.CANCELLED);
        long completed = countStatus(appointments, AppointmentStatus.COMPLETED);
        long notPerformed = countStatus(appointments, AppointmentStatus.NOT_PERFORMED);

        List<UUID> appointmentIds = appointments.stream().map(Appointment::getId).toList();
        if (appointmentIds.isEmpty()) {
            return new OperationalStatisticsResponse(from, to, normalizedSpecialty, doctorId, 0, 0, 0, 0, 0, 0,
                    null, null);
        }

        List<QueueTicket> tickets = queueTicketRepository.findByAppointmentIdIn(appointmentIds);
        List<ExaminationSession> sessions = examinationSessionRepository.findByAppointment_IdIn(appointmentIds);
        return new OperationalStatisticsResponse(from, to, normalizedSpecialty, doctorId, appointments.size(), checkedIn,
                absent, cancelled, completed, notPerformed, averageWaitMinutes(tickets, sessions),
                averageExaminationMinutes(sessions));
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
        var starts = sessions.stream().filter(session -> session.getStartedAt() != null)
                .collect(java.util.stream.Collectors.toMap(session -> session.getAppointment().getId(),
                        ExaminationSession::getStartedAt, (first, ignored) -> first));
        var values = tickets.stream().map(ticket -> {
            var startedAt = starts.get(ticket.getAppointment().getId());
            if (startedAt == null || startedAt.isBefore(ticket.getCheckedInAt())) return null;
            return Duration.between(ticket.getCheckedInAt(), startedAt).toMillis() / 60000d;
        }).filter(java.util.Objects::nonNull).toList();
        return average(values);
    }

    private BigDecimal averageExaminationMinutes(List<ExaminationSession> sessions) {
        var values = sessions.stream()
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
}
