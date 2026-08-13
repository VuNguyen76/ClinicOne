package com.clinicone.schedule;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.notification.PatientNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Applies the fixed lifecycle rules for a booked appointment. The job is
 * deliberately idempotent: notification event keys and the BOOKED guard make
 * reruns safe, while only the 24-hour rule changes appointment state.
 */
@Component
public class AppointmentLifecycleJob {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration LATE_THRESHOLD = Duration.ofMinutes(15);
    private static final Duration ABSENT_THRESHOLD = Duration.ofHours(24);
    private static final int DEFAULT_SLOT_DURATION_MINUTES = 60;

    private final AppointmentRepository appointmentRepository;
    private final PatientNotificationService notificationService;
    private final BusinessLogService businessLogService;
    private final Clock clock;
    private final AppointmentHoldService holdService;
    private final GeneratedClinicSlotRepository generatedSlotRepository;

    public AppointmentLifecycleJob(AppointmentRepository appointmentRepository,
                                   PatientNotificationService notificationService,
                                   BusinessLogService businessLogService, Clock clock) {
        this(appointmentRepository, notificationService, businessLogService, clock, null, null);
    }

    public AppointmentLifecycleJob(AppointmentRepository appointmentRepository,
                                   PatientNotificationService notificationService,
                                   BusinessLogService businessLogService, Clock clock,
                                   AppointmentHoldService holdService) {
        this(appointmentRepository, notificationService, businessLogService, clock, holdService, null);
    }

    @Autowired
    public AppointmentLifecycleJob(AppointmentRepository appointmentRepository,
                                   PatientNotificationService notificationService,
                                   BusinessLogService businessLogService, Clock clock,
                                   AppointmentHoldService holdService,
                                   GeneratedClinicSlotRepository generatedSlotRepository) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
        this.businessLogService = businessLogService;
        this.clock = clock;
        this.holdService = holdService;
        this.generatedSlotRepository = generatedSlotRepository;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.appointments.lifecycle-job-delay-ms:60000}")
    public void runScheduled() {
        runOnce();
    }

    @Transactional
    public LifecycleJobResult runOnce() {
        if (holdService != null) {
            holdService.releaseExpired();
        }
        Instant now = Instant.now(clock);
        LocalDate today = now.atZone(CLINIC_ZONE).toLocalDate();
        List<Appointment> appointments = appointmentRepository
                .findByStatusAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                        AppointmentStatus.BOOKED, today.minusDays(31), today.plusDays(31));
        int reminders = 0;
        int lateWarnings = 0;
        int absent = 0;
        for (Appointment appointment : appointments) {
            Instant appointmentAt = appointmentAt(appointment);
            Instant scheduledEndAt = scheduledEndAt(appointmentAt, appointment);
            if (shouldSendReminder(appointment, appointmentAt, now, 24)) {
                notificationService.notifyAppointmentReminder(appointment, 24);
                reminders++;
            }
            if (shouldSendReminder(appointment, appointmentAt, now, 2)) {
                notificationService.notifyAppointmentReminder(appointment, 2);
                reminders++;
            }
            if (!now.isBefore(scheduledEndAt.plus(LATE_THRESHOLD))) {
                notificationService.notifyAppointmentLate(appointment);
                businessLogService.recordActivity(UUID.nameUUIDFromBytes(
                                ("LATE_WARNING:" + appointment.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "APPOINTMENT", appointment.getId(), appointment.getStatus().name(),
                        appointment.getStatus().name(), "LATE_WARNING", "SYSTEM",
                        "Đã gửi cảnh báo người bệnh đến muộn");
                lateWarnings++;
            }
            if (!now.isBefore(scheduledEndAt.plus(ABSENT_THRESHOLD))
                    && appointment.getStatus() == AppointmentStatus.BOOKED) {
                if (markAbsent(appointment.getId())) {
                    absent++;
                }
            }
        }
        return new LifecycleJobResult(appointments.size(), reminders, lateWarnings, absent);
    }

    private boolean markAbsent(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElse(null);
        if (appointment == null || appointment.getStatus() != AppointmentStatus.BOOKED) {
            return false;
        }
        String previousStatus = appointment.getStatus().name();
        UUID eventId = UUID.randomUUID();
        appointment.markAbsent();
        markAppointmentSlotUnavailable(appointment);
        appointmentRepository.save(appointment);
        businessLogService.recordTransition(eventId, "APPOINTMENT", appointment.getId(), previousStatus,
                appointment.getStatus().name(), "MARK_ABSENT", "SYSTEM", "Tự động sau quá thời hạn vắng mặt");
        notificationService.notifyAppointmentAbsent(appointment);
        return true;
    }

    private void markAppointmentSlotUnavailable(Appointment appointment) {
        if (generatedSlotRepository == null || appointment.getDoctorStaffId() == null) {
            return;
        }
        var openSlot = appointment.getServiceId() == null
                ? generatedSlotRepository.findFirstByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        appointment.getDoctorStaffId(), appointment.getAppointmentDate(), appointment.getStartTime(),
                        GeneratedSlotStatus.OPEN)
                : generatedSlotRepository.findFirstByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        appointment.getServiceId(), appointment.getDoctorStaffId(), appointment.getAppointmentDate(),
                        appointment.getStartTime(), GeneratedSlotStatus.OPEN);
        openSlot
                .ifPresent(slot -> {
                    slot.cancel();
                    generatedSlotRepository.save(slot);
                });
    }

    private boolean shouldSendReminder(Appointment appointment, Instant appointmentAt, Instant now, int hours) {
        Duration remaining = Duration.between(now, appointmentAt);
        Duration threshold = Duration.ofHours(hours);
        if (remaining.isNegative() || remaining.compareTo(threshold) > 0
                || appointment.getStatus() != AppointmentStatus.BOOKED) {
            return false;
        }
        Instant createdAt = appointment.getCreatedAt();
        return createdAt == null || !createdAt.isAfter(appointmentAt.minus(threshold));
    }

    private Instant appointmentAt(Appointment appointment) {
        return ZonedDateTime.of(appointment.getAppointmentDate(), appointment.getStartTime(), CLINIC_ZONE)
                .toInstant();
    }

    private Instant scheduledEndAt(Instant appointmentAt, Appointment appointment) {
        Integer durationMinutes = appointment.getServiceDurationMinutes();
        int effectiveDuration = durationMinutes == null || durationMinutes <= 0
                ? DEFAULT_SLOT_DURATION_MINUTES
                : durationMinutes;
        return appointmentAt.plus(Duration.ofMinutes(effectiveDuration));
    }

    public record LifecycleJobResult(int inspected, int reminderCandidates, int lateWarningCandidates,
                                     int absentTransitions) {
    }
}
