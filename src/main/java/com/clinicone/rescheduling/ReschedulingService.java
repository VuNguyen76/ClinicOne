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
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
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
    private final GeneratedClinicSlotRepository generatedSlotRepository;

    public ReschedulingService(AppointmentRepository appointmentRepository,
                               RescheduleCaseRepository caseRepository,
                               AppointmentAvailabilityService availabilityService,
                               PatientNotificationService notificationService,
                               Clock clock) {
        this(appointmentRepository, caseRepository, availabilityService, notificationService, clock, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReschedulingService(AppointmentRepository appointmentRepository,
                               RescheduleCaseRepository caseRepository,
                               AppointmentAvailabilityService availabilityService,
                               PatientNotificationService notificationService,
                               Clock clock,
                               BusinessLogService businessLogService) {
        this(appointmentRepository, caseRepository, availabilityService, notificationService, clock,
                businessLogService, null);
    }

    public ReschedulingService(AppointmentRepository appointmentRepository,
                               RescheduleCaseRepository caseRepository,
                               AppointmentAvailabilityService availabilityService,
                               PatientNotificationService notificationService,
                               Clock clock,
                               BusinessLogService businessLogService,
                               GeneratedClinicSlotRepository generatedSlotRepository) {
        this.appointmentRepository = appointmentRepository;
        this.caseRepository = caseRepository;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.businessLogService = businessLogService;
        this.generatedSlotRepository = generatedSlotRepository;
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

    @Transactional
    public int openForDoctorTimeOff(UUID doctorId, LocalDate from, LocalDate to, String reason) {
        List<Appointment> appointments = appointmentRepository
                .findByDoctorStaffIdAndAppointmentDateBetweenAndStatusOrderByAppointmentDateAscStartTimeAsc(
                        doctorId, from, to, AppointmentStatus.BOOKED);
        int opened = 0;
        for (Appointment appointment : appointments) {
            if (caseRepository.findByAppointmentIdAndStatus(appointment.getId(), RescheduleCaseStatus.OPEN).isPresent()) {
                continue;
            }
            RescheduleCase item = caseRepository.save(RescheduleCase.open(appointment, reason));
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
        return alternatives(item, from, to);
    }

    @Transactional(readOnly = true)
    public RescheduleCaseResponse findForPatient(String accountId, UUID appointmentId) {
        UUID patientId = parseAccountId(accountId);
        RescheduleCase item = findOpenCaseForPatient(appointmentId, patientId);
        return RescheduleCaseResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> alternativesForPatient(String accountId, UUID appointmentId,
                                                              LocalDate from, LocalDate to) {
        UUID patientId = parseAccountId(accountId);
        RescheduleCase item = findOpenCaseForPatient(appointmentId, patientId);
        return alternatives(item, from, to);
    }

    @Transactional
    public RescheduleCaseResponse resolveForPatient(String accountId, UUID appointmentId,
                                                    ResolveRescheduleRequest request) {
        UUID patientId = parseAccountId(accountId);
        RescheduleCase openCase = findOpenCaseForPatient(appointmentId, patientId);
        RescheduleCase item = caseRepository.findByIdForUpdate(openCase.getId()).orElseThrow(this::notFound);
        ensurePatientOwns(item, patientId);
        return resolveLocked(item, request, "PATIENT:" + patientId);
    }

    private List<AvailableSlotResponse> alternatives(RescheduleCase item, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? item.getOldAppointmentDate() : from;
        LocalDate end = to == null ? start.plusDays(SEARCH_DAYS) : to;
        if (item.getAppointment().getServiceId() == null) {
            return availabilityService.find(item.getSpecialty(), start, end);
        }
        return availabilityService.find(item.getSpecialty(), start, end, item.getAppointment().getServiceId());
    }

    @Transactional
    public RescheduleCaseResponse resolve(UUID caseId, ResolveRescheduleRequest request, String actor) {
        RescheduleCase item = caseRepository.findByIdForUpdate(caseId).orElseThrow(this::notFound);
        return resolveLocked(item, request, actor);
    }

    private RescheduleCaseResponse resolveLocked(RescheduleCase item, ResolveRescheduleRequest request, String actor) {
        if (item.getStatus() != RescheduleCaseStatus.OPEN) {
            throw conflict("RESCHEDULE_CASE_ALREADY_RESOLVED", "Lịch cần sắp xếp lại đã được xử lý.");
        }
        Appointment appointment = item.getAppointment();
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw conflict("RESCHEDULE_APPOINTMENT_NOT_BOOKED", "Lịch hẹn không còn ở trạng thái có thể sắp xếp lại.");
        }
        if (appointment.getServiceId() == null) {
            availabilityService.ensureBookable(appointment.getSpecialty(), request.doctorName(), request.doctorId(),
                    request.appointmentDate(), request.startTime());
        } else {
            availabilityService.ensureBookable(appointment.getSpecialty(), request.doctorName(), request.doctorId(),
                    request.appointmentDate(), request.startTime(), null, appointment.getServiceId());
        }
        markPreviousGeneratedSlotUnavailable(appointment);
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

    private void markPreviousGeneratedSlotUnavailable(Appointment appointment) {
        if (generatedSlotRepository == null || appointment.getServiceId() == null
                || appointment.getDoctorStaffId() == null) {
            return;
        }
        GeneratedClinicSlot slot = generatedSlotRepository
                .findFirstByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        appointment.getServiceId(), appointment.getDoctorStaffId(), appointment.getAppointmentDate(),
                        appointment.getStartTime(), GeneratedSlotStatus.OPEN)
                .orElseThrow(() -> conflict("APPOINTMENT_SLOT_NOT_FOUND",
                        "Khong tim thay khung gio cu de dong sau khi doi lich."));
        slot.cancel();
        generatedSlotRepository.save(slot);
    }

    private RescheduleCase findOpenCaseForPatient(UUID appointmentId, UUID patientId) {
        RescheduleCase item = caseRepository.findByAppointmentIdAndStatus(appointmentId, RescheduleCaseStatus.OPEN)
                .orElseThrow(this::notFound);
        ensurePatientOwns(item, patientId);
        return item;
    }

    private void ensurePatientOwns(RescheduleCase item, UUID patientId) {
        if (item.getAppointment().getPatient() == null
                || !patientId.equals(item.getAppointment().getPatient().getId())) {
            throw notFound();
        }
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập không hợp lệ.");
        }
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
