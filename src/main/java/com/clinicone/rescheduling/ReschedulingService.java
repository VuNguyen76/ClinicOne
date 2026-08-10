package com.clinicone.rescheduling;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.schedule.AppointmentAvailabilityService;
import com.clinicone.schedule.AvailableSlotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ReschedulingService {
    private static final int SEARCH_DAYS = 30;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AppointmentRepository appointmentRepository;
    private final RescheduleCaseRepository caseRepository;
    private final AppointmentAvailabilityService availabilityService;
    private final PatientNotificationService notificationService;
    private final Clock clock;
    private final BusinessLogService businessLogService;

    public ReschedulingService(AppointmentRepository appointmentRepository,
                               RescheduleCaseRepository caseRepository,
                               AppointmentAvailabilityService availabilityService,
                               PatientNotificationService notificationService,
                               Clock clock) {
        this(appointmentRepository, caseRepository, availabilityService, notificationService, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReschedulingService(AppointmentRepository appointmentRepository,
                               RescheduleCaseRepository caseRepository,
                               AppointmentAvailabilityService availabilityService,
                               PatientNotificationService notificationService,
                               Clock clock,
                               BusinessLogService businessLogService) {
        this.appointmentRepository = appointmentRepository;
        this.caseRepository = caseRepository;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.businessLogService = businessLogService;
    }

    @Transactional
    public int openForScheduleRemoval(DoctorSchedule schedule) {
        UUID doctorId = schedule.getDoctorProfile().getStaffAccount().getId();
        LocalDate from = LocalDate.now(clock.withZone(CLINIC_ZONE));
        LocalDate to = from.plusDays(SEARCH_DAYS);
        List<Appointment> appointments = appointmentRepository
                .findByDoctorStaffIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
                        doctorId, from, to, AppointmentStatus.BOOKED);
        int opened = 0;
        for (Appointment appointment : appointments) {
            if (!matchesSchedule(appointment, schedule)) {
                continue;
            }
            if (caseRepository.findByAppointmentIdAndStatus(appointment.getId(), RescheduleCaseStatus.OPEN).isPresent()) {
                continue;
            }
            RescheduleCase item = caseRepository.save(RescheduleCase.open(appointment,
                    "Giờ làm của bác sĩ đã thay đổi; cần chọn khung giờ thay thế."));
            if (notificationService != null) {
                notificationService.notifyAppointmentRescheduleRequired(appointment);
            }
            opened++;
        }
        return opened;
    }

    @Transactional(readOnly = true)
    public List<RescheduleCaseResponse> listOpen() {
        return caseRepository.findByStatusOrderByCreatedAtAsc(RescheduleCaseStatus.OPEN).stream()
                .map(RescheduleCaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> alternatives(UUID caseId, LocalDate from, LocalDate to) {
        RescheduleCase item = findCase(caseId);
        LocalDate start = from == null ? item.getOldAppointmentDate() : from;
        LocalDate end = to == null ? start.plusDays(SEARCH_DAYS) : to;
        return availabilityService.find(item.getSpecialty(), start, end);
    }

    @Transactional
    public RescheduleCaseResponse resolve(UUID caseId, ResolveRescheduleRequest request, String actor) {
        RescheduleCase item = caseRepository.findByIdForUpdate(caseId).orElseThrow(this::notFound);
        if (item.getStatus() != RescheduleCaseStatus.OPEN) {
            throw conflict("RESCHEDULE_CASE_ALREADY_RESOLVED", "Lịch cần sắp xếp lại đã được xử lý.");
        }
        Appointment appointment = item.getAppointment();
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw conflict("RESCHEDULE_APPOINTMENT_NOT_BOOKED", "Lịch hẹn không còn ở trạng thái có thể sắp xếp lại.");
        }
        availabilityService.ensureBookable(appointment.getSpecialty(), request.doctorName(), request.doctorId(),
                request.appointmentDate(), request.startTime());
        String previousDate = appointment.getAppointmentDate().toString();
        String previousTime = appointment.getStartTime().toString();
        appointment.reschedule(request.appointmentDate(), request.startTime(), request.doctorId(),
                request.doctorName().trim());
        appointmentRepository.save(appointment);
        item.resolve(request.appointmentDate(), request.startTime(), request.doctorId(), request.doctorName().trim(),
                Instant.now(clock));
        RescheduleCaseResponse response = RescheduleCaseResponse.from(caseRepository.save(item));
        if (businessLogService != null) {
            businessLogService.recordTransition(UUID.randomUUID(), "RESCHEDULE_CASE", item.getId(),
                    RescheduleCaseStatus.OPEN.name(), RescheduleCaseStatus.RESOLVED.name(),
                    "RESOLVE_RESCHEDULE_CASE", actor, "Đã xác nhận khung giờ thay thế cho lịch hẹn.");
        }
        if (notificationService != null) {
            notificationService.notifyAppointmentRescheduled(appointment, previousDate, previousTime);
        }
        return response;
    }

    private boolean matchesSchedule(Appointment appointment, DoctorSchedule schedule) {
        if (appointment.getAppointmentDate().getDayOfWeek() != schedule.getDayOfWeek()) {
            return false;
        }
        LocalTime start = appointment.getStartTime();
        return !start.isBefore(schedule.getStartTime())
                && !start.plusMinutes(schedule.getSlotDurationMinutes()).isAfter(schedule.getEndTime());
    }

    private RescheduleCase findCase(UUID id) {
        return caseRepository.findById(id).orElseThrow(this::notFound);
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESCHEDULE_CASE_NOT_FOUND",
                "Không tìm thấy lịch cần sắp xếp lại.");
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
