package com.clinicone.queue;

import com.clinicone.auth.AuthenticatedIds;
import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.schedule.GeneratedClinicSlot;
import com.clinicone.schedule.GeneratedClinicSlotRepository;
import com.clinicone.schedule.GeneratedSlotStatus;
import com.clinicone.validation.IdempotencyKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueueService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_SLOT_DURATION_MINUTES = 60;
    private static final Comparator<QueueTicket> NEXT_WAITING_ORDER = Comparator
            .comparing(QueueTicket::isPriority).reversed()
            .thenComparing(ticket -> ticket.getAppointment().getStartTime(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueTicket::getCheckedInAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(QueueTicket::getQueueNumber);
    private static final Comparator<QueueTicket> DISPLAY_ORDER = Comparator
            .comparingInt(QueueService::displayGroup)
            .thenComparing(ticket -> ticket.getAppointment().getStartTime(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueTicket::getCheckedInAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(QueueTicket::getQueueNumber);

    private final ClinicRoomRepository roomRepository;
    private final QueueTicketRepository ticketRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ExaminationSessionRepository examinationSessionRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final BusinessLogService businessLogService;
    private final GeneratedClinicSlotRepository generatedSlotRepository;
    private final Clock clock;

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository) {
        this(roomRepository, ticketRepository, appointmentRepository, null, null, null, null, null, Clock.systemUTC());
    }

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, Clock clock) {
        this(roomRepository, ticketRepository, appointmentRepository, null, null, null, null, null, clock);
    }

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, DoctorProfileRepository doctorProfileRepository,
                        Clock clock) {
        this(roomRepository, ticketRepository, appointmentRepository, doctorProfileRepository, null, null, null, null, clock);
    }

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, DoctorProfileRepository doctorProfileRepository,
                        ExaminationSessionRepository examinationSessionRepository, Clock clock) {
        this(roomRepository, ticketRepository, appointmentRepository, doctorProfileRepository,
                examinationSessionRepository, null, null, null, clock);
    }

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, DoctorProfileRepository doctorProfileRepository,
                        ExaminationSessionRepository examinationSessionRepository, BusinessLogService businessLogService,
                        Clock clock) {
        this(roomRepository, ticketRepository, appointmentRepository, doctorProfileRepository,
                examinationSessionRepository, businessLogService, null, null, clock);
    }

    @Autowired
    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, DoctorProfileRepository doctorProfileRepository,
                        ExaminationSessionRepository examinationSessionRepository, BusinessLogService businessLogService,
                        DoctorScheduleRepository doctorScheduleRepository, GeneratedClinicSlotRepository generatedSlotRepository,
                        Clock clock) {
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.examinationSessionRepository = examinationSessionRepository;
        this.businessLogService = businessLogService;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.clock = clock;
        this.generatedSlotRepository = generatedSlotRepository;
    }

    @Transactional
    public QueueTicketResponse checkIn(String accountId, String roomCode, UUID appointmentId) {
        return checkIn(accountId, roomCode, appointmentId, null);
    }

    @Transactional
    public QueueTicketResponse checkIn(String accountId, String roomCode, UUID appointmentId, String requestKey) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        String normalizedRequestKey = IdempotencyKeys.optional(requestKey);
        if (normalizedRequestKey != null) {
            appointmentRepository.findByPatientIdAndCheckInRequestKey(patientId, normalizedRequestKey)
                    .filter(existing -> !existing.getId().equals(appointmentId))
                    .ifPresent(existing -> {
                        throw new AuthException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                                "Khóa chống trùng đã được dùng cho một lượt check-in khác.");
                    });
        }
        Appointment appointment = appointmentRepository.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        ensureQrCheckInNotLate(appointment);
        return checkInAppointment(roomCode, appointment, null, accountId, normalizedRequestKey);
    }

    @Transactional
    public QueueTicketResponse checkInByStaff(String roomCode, UUID appointmentId, String exceptionReason) {
        return checkInByStaff(roomCode, appointmentId, exceptionReason, "STAFF");
    }

    @Transactional
    public QueueTicketResponse checkInByStaff(String roomCode, UUID appointmentId, String exceptionReason,
                                              String actor) {
        return checkInByStaff(roomCode, appointmentId, exceptionReason, actor, null);
    }

    @Transactional
    public QueueTicketResponse checkInByStaff(String roomCode, UUID appointmentId, String exceptionReason,
                                              String actor, String requestKey) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        // Reception must move a late appointment to another suitable slot first.
        // Staff check-in is deliberately subject to the same guard as patient QR check-in.
        ensureQrCheckInNotLate(appointment);
        String normalizedActor = actor == null || actor.isBlank() ? "STAFF" : actor.trim();
        return checkInAppointment(roomCode, appointment, normalizeExceptionReason(exceptionReason), normalizedActor,
                requestKey);
    }

    private QueueTicketResponse checkInAppointment(String roomCode, Appointment appointment) {
        return checkInAppointment(roomCode, appointment, null, "SYSTEM");
    }

    private QueueTicketResponse checkInAppointment(String roomCode, Appointment appointment, String exceptionReason) {
        return checkInAppointment(roomCode, appointment, exceptionReason, "SYSTEM");
    }

    private QueueTicketResponse checkInAppointment(String roomCode, Appointment appointment, String exceptionReason,
                                                   String actor) {
        return checkInAppointment(roomCode, appointment, exceptionReason, actor, null);
    }

    private QueueTicketResponse checkInAppointment(String roomCode, Appointment appointment, String exceptionReason,
                                                   String actor, String requestKey) {
        ClinicRoom room = findRoom(roomCode);
        LocalDate today = today();
        if (!appointment.getAppointmentDate().equals(today)) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_DATE_NOT_OPEN",
                    "Chỉ có thể lấy số cho lịch hẹn trong ngày hôm nay.");
        }
        if (appointment.getPatient() != null && appointment.getPatient().isMustChangePassword()) {
            throw new AuthException(HttpStatus.CONFLICT, "PASSWORD_CHANGE_REQUIRED",
                    "Người bệnh cần đổi mật khẩu tạm trước khi check-in.");
        }
        if (!room.getSpecialty().equalsIgnoreCase(appointment.getSpecialty())) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_ROOM_MISMATCH",
                    "Phòng này không thuộc chuyên khoa của lịch hẹn.");
        }
        ensureDoctorRoom(appointment, room);

        UUID appointmentId = appointment.getId();
        UUID eventId = UUID.randomUUID();
        var existing = ticketRepository.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            QueueTicket ticket = existing.get();
            if (!ticket.getRoom().getCode().equalsIgnoreCase(room.getCode())) {
                throw new AuthException(HttpStatus.CONFLICT, "QUEUE_ALREADY_CHECKED_IN",
                        "Lịch hẹn đã lấy số tại phòng khác.");
            }
            if (ticket.getStatus() == QueueTicketStatus.COMPLETED
                    || ticket.getStatus() == QueueTicketStatus.LEFT_BEFORE_EXAM) {
                // A QR rescan is a read/recovery action for a terminal queue entry.
                // Return the original number and outcome instead of creating or
                // mutating another examination session.
                return QueueTicketResponse.from(ticket);
            }
            String previousAppointmentStatus = appointment.getStatus().name();
            boolean appointmentChanged = appointment.getStatus() != AppointmentStatus.CHECKED_IN;
            String previousCheckInKey = appointment.getCheckInRequestKey();
            appointment.checkIn();
            appointment.assignCheckInRequestKey(requestKey);
            boolean checkInKeyChanged = !Objects.equals(previousCheckInKey, appointment.getCheckInRequestKey());
            if (appointmentChanged || checkInKeyChanged) {
                appointmentRepository.save(appointment);
            }
            boolean returned = false;
            if (ticket.getPresenceStatus() == QueuePresenceStatus.RETURN_REQUIRED
                    && doctorHasActiveShift(appointment, room)) {
                ticket.markReturned(Instant.now(clock));
                returned = true;
            }
            boolean reasonChanged = exceptionReason != null && !exceptionReason.equals(ticket.getExceptionReason());
            if (exceptionReason != null) {
                ticket.recordExceptionReason(exceptionReason);
            }
            if (returned || reasonChanged) {
                ticketRepository.save(ticket);
            }
            SessionTransition sessionTransition = ensureCheckedInSession(appointment);
            if (appointmentChanged) {
                recordTransition(eventId, "APPOINTMENT", appointment.getId(), previousAppointmentStatus,
                        appointment.getStatus().name(), "CHECK_IN", actor, exceptionReason);
            }
            if (sessionTransition != null) {
                recordTransition(eventId, "EXAMINATION", sessionTransition.session().getId(),
                        sessionTransition.previousStatus(), sessionTransition.session().getStatus().name(),
                        "CHECK_IN", actor, exceptionReason);
            }
            return QueueTicketResponse.from(ticket);
        }

        ensureBookable(appointment);
        int nextNumber = nextNumber(room, today);
        String previousAppointmentStatus = appointment.getStatus().name();
        appointment.checkIn();
        appointment.assignCheckInRequestKey(requestKey);
        appointmentRepository.save(appointment);

        try {
            QueueTicket ticket = ticketRepository.save(QueueTicket.create(appointment, room, today, nextNumber, exceptionReason));
            SessionTransition sessionTransition = ensureCheckedInSession(appointment);
            recordTransition(eventId, "APPOINTMENT", appointment.getId(), previousAppointmentStatus,
                    appointment.getStatus().name(), "CHECK_IN", actor, exceptionReason);
            recordTransition(eventId, "QUEUE_TICKET", ticket.getId(), null, ticket.getStatus().name(),
                    "CHECK_IN", actor, exceptionReason);
            if (sessionTransition != null) {
                recordTransition(eventId, "EXAMINATION", sessionTransition.session().getId(),
                        sessionTransition.previousStatus(), sessionTransition.session().getStatus().name(),
                        "CHECK_IN", actor, exceptionReason);
            }
            return QueueTicketResponse.from(ticket);
        } catch (DataIntegrityViolationException exception) {
            return ticketRepository.findByAppointmentId(appointmentId)
                    .map(ticket -> {
                        ensureCheckedInSession(appointment);
                        return QueueTicketResponse.from(ticket);
                    })
                    .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "QUEUE_CHECK_IN_RETRY",
                            "Không thể cấp số lúc này, vui lòng thử lại."));
        }
    }

    @Transactional(readOnly = true)
    public List<QueueTicketResponse> list(String roomCode, LocalDate date) {
        findRoom(roomCode);
        LocalDate queueDate = date == null ? today() : date;
        return orderForDisplay(ticketRepository.findByRoomCodeAndQueueDateOrderByQueueNumberAsc(roomCode, queueDate)).stream()
                .map(QueueTicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueTicketResponse> listForPatient(String accountId, LocalDate date) {
        UUID patientId = AuthenticatedIds.patient(accountId);
        LocalDate queueDate = date == null ? today() : date;
        return ticketRepository.findByAppointment_Patient_IdAndQueueDateOrderByQueueNumberAsc(patientId, queueDate)
                .stream()
                .map(QueueTicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueTicketResponse> listForStaff(String roomCode, LocalDate date, String staffId, StaffRole role) {
        if (role == StaffRole.DOCTOR) {
            UUID doctorId = AuthenticatedIds.staff(staffId);
            DoctorProfile profile = doctorProfile(doctorId);
            if (!profile.getRoom().getCode().equalsIgnoreCase(roomCode)) {
                throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ROOM_SCOPE",
                        "Bác sĩ chỉ được xem hàng đợi của phòng được phân công.");
            }
            LocalDate queueDate = date == null ? today() : date;
            return doctorTickets(roomCode, queueDate, doctorId).stream()
                    .map(QueueTicketResponse::from)
                    .toList();
        }
        return list(roomCode, date);
    }

    @Transactional(readOnly = true)
    public DoctorQueueResponse doctorQueue(LocalDate date, String staffId) {
        UUID doctorId = AuthenticatedIds.staff(staffId);
        DoctorProfile profile = doctorProfile(doctorId);
        LocalDate queueDate = date == null ? today() : date;
        List<QueueTicketResponse> tickets = doctorTickets(profile.getRoom().getCode(), queueDate, doctorId).stream()
                .map(QueueTicketResponse::from)
                .toList();
        return new DoctorQueueResponse(profile.getRoom().getCode(), profile.getRoom().getName(),
                profile.getSpecialty(), tickets);
    }

    @Transactional
    public QueueTicketResponse call(UUID ticketId, String staffId) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        ensureDoctorCanOperateQueue(ticket);
        String previousStatus = ticket.getStatus().name();
        try {
            ticket.call();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        QueueTicketResponse response = QueueTicketResponse.from(ticketRepository.save(ticket));
        recordTransition(UUID.randomUUID(), "QUEUE_TICKET", ticket.getId(), previousStatus,
                ticket.getStatus().name(), "CALL_PATIENT", staffId);
        return response;
    }

    @Transactional
    public QueueTicketResponse callNext(String staffId, LocalDate date) {
        UUID doctorId = AuthenticatedIds.staff(staffId);
        DoctorProfile profile = doctorProfile(doctorId);
        LocalDate queueDate = date == null ? today() : date;
        if (!hasActiveShift(profile, queueDate)) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SHIFT_INACTIVE",
                    "Bác sĩ không có ca làm việc đang hiệu lực để gọi bệnh nhân.");
        }
        QueueTicket next = doctorTickets(profile.getRoom().getCode(), queueDate, doctorId).stream()
                .filter(ticket -> ticket.getStatus() == QueueTicketStatus.WAITING
                        && ticket.getPresenceStatus() == QueuePresenceStatus.READY)
                .sorted(NEXT_WAITING_ORDER)
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "QUEUE_NO_NEXT_PATIENT",
                        "Không còn bệnh nhân đang chờ trong hàng đợi."));
        return call(next.getId(), staffId);
    }

    @Transactional
    public QueueTicketResponse adjust(UUID ticketId, QueueAdjustmentRequest request, String actor) {
        QueueTicket ticket = findTicket(ticketId);
        if (ticket.getStatus() != QueueTicketStatus.WAITING) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_ADJUSTMENT_NOT_ALLOWED",
                    "Chỉ có thể điều chỉnh lượt đang chờ trước khi gọi.");
        }
        String reason = normalizeAdjustmentReason(request.reason());
        String previousStatus = ticket.getStatus().name();
        UUID eventId = UUID.randomUUID();
        switch (request.action()) {
            case SET_PRIORITY -> ticket.setPriority(true);
            case CLEAR_PRIORITY -> ticket.setPriority(false);
            case MOVE -> moveTicket(ticket, request);
        }
        QueueTicket saved = ticketRepository.save(ticket);
        if (businessLogService != null) {
            businessLogService.recordActivity(eventId, "QUEUE_TICKET", ticket.getId(), previousStatus,
                    saved.getStatus().name(), request.action() == QueueAdjustmentAction.MOVE
                            ? "QUEUE_REASSIGNED" : "QUEUE_PRIORITY_CHANGED", actor, reason);
        }
        return QueueTicketResponse.from(saved);
    }

    @Transactional
    public QueueTicketResponse skip(UUID ticketId, String staffId, String reason) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        ensureDoctorCanOperateQueue(ticket);
        String normalizedReason = normalizeSkipReason(reason);
        String previousStatus = ticket.getStatus().name();
        try {
            ticket.skip(normalizedReason);
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        QueueTicketResponse response = QueueTicketResponse.from(ticketRepository.save(ticket));
        recordTransition(UUID.randomUUID(), "QUEUE_TICKET", ticket.getId(), previousStatus,
                ticket.getStatus().name(), "RETURN_TO_QUEUE", staffId, normalizedReason);
        return response;
    }

    @Transactional
    public QueueTicketResponse start(UUID ticketId, String staffId) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        String previousStatus = ticket.getStatus().name();
        try {
            ticket.startService();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        QueueTicketResponse response = QueueTicketResponse.from(ticketRepository.save(ticket));
        recordTransition(UUID.randomUUID(), "QUEUE_TICKET", ticket.getId(), previousStatus,
                ticket.getStatus().name(), "START_EXAMINATION", staffId);
        return response;
    }

    @Transactional
    public QueueTicketResponse leaveBeforeExam(UUID ticketId, String reason) {
        return leaveBeforeExam(ticketId, reason, "SYSTEM");
    }

    @Transactional
    public QueueTicketResponse leaveBeforeExam(UUID ticketId, String reason, String actor) {
        QueueTicket ticket = findTicket(ticketId);
        String normalizedReason = normalizeLeaveReason(reason);
        String normalizedActor = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        UUID eventId = UUID.randomUUID();
        String previousTicketStatus = ticket.getStatus().name();
        String previousAppointmentStatus = ticket.getAppointment().getStatus().name();
        try {
            ticket.leaveBeforeExam(normalizedReason);
            ticket.getAppointment().markNotPerformed();
            appointmentRepository.save(ticket.getAppointment());
            if (examinationSessionRepository != null) {
                examinationSessionRepository.findByAppointment_Id(ticket.getAppointment().getId())
                        .ifPresent(session -> {
                            session.cancel();
                            examinationSessionRepository.save(session);
                        });
            }
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        QueueTicketResponse response = QueueTicketResponse.from(ticketRepository.save(ticket));
        recordTransition(eventId, "QUEUE_TICKET", ticket.getId(), previousTicketStatus,
                ticket.getStatus().name(), "LEAVE_BEFORE_EXAM", normalizedActor, normalizedReason);
        recordTransition(eventId, "APPOINTMENT", ticket.getAppointment().getId(), previousAppointmentStatus,
                ticket.getAppointment().getStatus().name(), "LEAVE_BEFORE_EXAM", normalizedActor, normalizedReason);
        return response;
    }

    @Transactional
    public QueueTicketResponse markFacilityUnavailable(UUID ticketId, String reason, String actor) {
        QueueTicket ticket = findTicket(ticketId);
        String normalizedReason = normalizeLeaveReason(reason);
        UUID eventId = UUID.randomUUID();
        String previousTicketStatus = ticket.getStatus().name();
        String previousAppointmentStatus = ticket.getAppointment().getStatus().name();
        ExaminationSession session = examinationSessionRepository == null ? null
                : examinationSessionRepository.findByAppointment_Id(ticket.getAppointment().getId()).orElse(null);
        String previousSessionStatus = session == null ? null : session.getStatus().name();
        markAppointmentSlotUnavailable(ticket.getAppointment());
        try {
            ticket.closeFacilityUnavailable(normalizedReason);
            ticket.getAppointment().markNotPerformed();
            if (session != null) {
                session.cancel();
                examinationSessionRepository.save(session);
            }
            appointmentRepository.save(ticket.getAppointment());
            QueueTicketResponse response = QueueTicketResponse.from(ticketRepository.save(ticket));
            recordTransition(eventId, "QUEUE_TICKET", ticket.getId(), previousTicketStatus,
                    ticket.getStatus().name(), "FACILITY_UNAVAILABLE", actor, normalizedReason);
            recordTransition(eventId, "APPOINTMENT", ticket.getAppointment().getId(), previousAppointmentStatus,
                    ticket.getAppointment().getStatus().name(), "FACILITY_UNAVAILABLE", actor, normalizedReason);
            if (session != null) {
                recordTransition(eventId, "EXAMINATION", session.getId(), previousSessionStatus,
                        session.getStatus().name(), "FACILITY_UNAVAILABLE", actor, normalizedReason);
            }
            return response;
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
    }

    private int nextNumber(ClinicRoom room, LocalDate date) {
        ClinicRoom lockedRoom = roomRepository.findByCodeAndActiveTrueForUpdate(room.getCode())
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "ROOM_NOT_AVAILABLE",
                        "Phòng khám không còn hoạt động để cấp số."));
        Integer currentMax = ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate(lockedRoom.getCode(), date);
        if (currentMax == null) {
            return 1;
        }
        if (currentMax >= QueueTicket.MAX_QUEUE_NUMBER) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_NUMBER_LIMIT_REACHED",
                    "Hàng đợi đã đủ 999 số trong ngày; vui lòng liên hệ quầy để được hỗ trợ.");
        }
        return currentMax + 1;
    }

    private void moveTicket(QueueTicket ticket, QueueAdjustmentRequest request) {
        String requestedSpecialty = request.targetSpecialty() == null ? "" : request.targetSpecialty().trim();
        String requestedRoom = request.targetRoomCode() == null ? "" : request.targetRoomCode().trim();
        DoctorProfile targetDoctor = findTargetDoctor(request, requestedSpecialty, requestedRoom);
        if (targetDoctor == null) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_TARGET_DOCTOR_INVALID",
                    "Bác sĩ đích chưa được phân công hoặc đã ngừng hoạt động.");
        }
        if (!requestedSpecialty.isBlank() && !requestedSpecialty.equalsIgnoreCase(targetDoctor.getSpecialty())) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_TARGET_SPECIALTY_MISMATCH",
                    "Chuyên khoa đích không khớp với phân công của bác sĩ.");
        }
        if (!requestedRoom.isBlank() && !requestedRoom.equalsIgnoreCase(targetDoctor.getRoom().getCode())) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_TARGET_ROOM_MISMATCH",
                    "Phòng đích không khớp với phân công của bác sĩ.");
        }
        ClinicRoom targetRoom = targetDoctor.getRoom();
        // A reassignment always receives a fresh number in the destination queue,
        // including when the destination room is unchanged. The old number remains
        // visible through the business journal rather than being reused.
        int targetNumber = nextNumber(targetRoom, ticket.getQueueDate());
        ticket.moveTo(targetRoom, targetDoctor.getStaffAccount().getId(), targetDoctor.getStaffAccount().getFullName(),
                targetDoctor.getSpecialty(), targetNumber);
    }

    private void markAppointmentSlotUnavailable(Appointment appointment) {
        if (appointment.getServiceId() == null || generatedSlotRepository == null) {
            return;
        }
        GeneratedClinicSlot slot = generatedSlotRepository
                .findFirstByClinicServiceIdAndDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                        appointment.getServiceId(), appointment.getDoctorStaffId(), appointment.getAppointmentDate(),
                        appointment.getStartTime(), GeneratedSlotStatus.OPEN)
                .orElseThrow(() -> queueStateConflict("Không tìm thấy khung giờ đang hoạt động của lịch hẹn."));
        slot.cancel();
        generatedSlotRepository.save(slot);
    }

    private DoctorProfile findTargetDoctor(QueueAdjustmentRequest request, String specialty, String roomCode) {
        if (doctorProfileRepository == null) return null;
        if (request.targetDoctorId() != null) {
            return doctorProfileRepository.findById(request.targetDoctorId())
                    .filter(DoctorProfile::isActive)
                    .orElse(null);
        }
        if (specialty.isBlank() || roomCode.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_TARGET_DOCTOR_REQUIRED",
                    "Cần chọn bác sĩ đích hoặc chỉ rõ chuyên khoa và phòng đích.");
        }
        List<DoctorProfile> candidates = doctorProfileRepository.findBySpecialtyIgnoreCaseAndActiveTrue(specialty)
                .stream().filter(profile -> profile.getRoom().getCode().equalsIgnoreCase(roomCode)).toList();
        if (candidates.size() > 1) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_TARGET_DOCTOR_REQUIRED",
                    "Có nhiều bác sĩ trong hàng đợi đích; cần chọn đúng bác sĩ.");
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<QueueTicket> doctorTickets(String roomCode, LocalDate date, UUID doctorId) {
        Map<UUID, QueueTicket> distinct = new LinkedHashMap<>();
        addTickets(distinct, ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                roomCode, date, doctorId));
        addTickets(distinct, ticketRepository.findByRoomCodeAndQueueDateAndRoutingDoctorStaffIdOrderByQueueNumberAsc(
                roomCode, date, doctorId));
        return distinct.values().stream()
                .filter(ticket -> Objects.equals(ticket.getEffectiveDoctorStaffId(), doctorId))
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    private static List<QueueTicket> orderForDisplay(List<QueueTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of();
        }
        return tickets.stream().filter(Objects::nonNull).sorted(DISPLAY_ORDER).toList();
    }

    private static int displayGroup(QueueTicket ticket) {
        return switch (ticket.getStatus()) {
            case IN_SERVICE -> 0;
            case CALLED -> 1;
            case WAITING -> ticket.getPresenceStatus() == QueuePresenceStatus.READY
                    ? (ticket.isPriority() ? 2 : 3) : 4;
            case SKIPPED -> 4;
            case LEFT_BEFORE_EXAM, COMPLETED -> 5;
        };
    }

    private void addTickets(Map<UUID, QueueTicket> target, List<QueueTicket> tickets) {
        if (tickets == null) return;
        for (QueueTicket ticket : tickets) {
            if (ticket != null && ticket.getId() != null) target.put(ticket.getId(), ticket);
        }
    }

    private SessionTransition ensureCheckedInSession(Appointment appointment) {
        if (examinationSessionRepository == null) {
            return null;
        }
        var existing = examinationSessionRepository.findByAppointment_Id(appointment.getId());
        ExaminationSession session = existing.orElseGet(() -> ExaminationSession.create(appointment));
        String previousStatus = existing.map(value -> value.getStatus().name()).orElse(null);
        session.checkIn();
        ExaminationSession saved = examinationSessionRepository.save(session);
        return previousStatus != null && previousStatus.equals(saved.getStatus().name())
                ? null
                : new SessionTransition(saved, previousStatus);
    }

    private boolean doctorHasActiveShift(Appointment appointment, ClinicRoom room) {
        // Legacy/unit-test services may not wire schedules; the production bean
        // always has the repository and performs the real shift check.
        if (doctorScheduleRepository == null || appointment.getDoctorStaffId() == null) {
            return true;
        }
        if (doctorProfileRepository == null) {
            return false;
        }
        DoctorProfile profile = doctorProfileRepository.findByStaffAccount_Id(appointment.getDoctorStaffId())
                .filter(DoctorProfile::isActive)
                .filter(value -> value.getRoom().getCode().equalsIgnoreCase(room.getCode()))
                .orElse(null);
        if (profile == null) {
            return false;
        }
        return hasActiveShift(profile, today());
    }

    private boolean hasActiveShift(DoctorProfile profile, LocalDate date) {
        if (doctorScheduleRepository == null) {
            return true;
        }
        if (!date.equals(today())) {
            return false;
        }
        var now = LocalTime.now(clock.withZone(CLINIC_ZONE));
        long activeSchedules = doctorScheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                        profile.getId(), date.getDayOfWeek()).stream()
                .filter(schedule -> !now.isBefore(schedule.getStartTime())
                        && now.isBefore(schedule.getEndTime()))
                .count();
        return activeSchedules == 1;
    }

    private void recordTransition(UUID eventId, String entityType, UUID entityId, String previousStatus,
                                  String nextStatus, String eventType, String actor) {
        recordTransition(eventId, entityType, entityId, previousStatus, nextStatus, eventType, actor, null);
    }

    private void recordTransition(UUID eventId, String entityType, UUID entityId, String previousStatus,
                                  String nextStatus, String eventType, String actor, String reason) {
        if (businessLogService != null && entityId != null) {
            businessLogService.recordTransition(eventId, entityType, entityId, previousStatus, nextStatus,
                    eventType, actor, reason);
        }
    }

    private String normalizeExceptionReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() < 10 || normalized.length() > 250) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "RECEPTION_REASON_INVALID",
                    "Lý do hỗ trợ tại quầy phải từ 10 đến 250 ký tự.");
        }
        return normalized;
    }

    private String normalizeSkipReason(String reason) {
        if (reason == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_SKIP_REASON_REQUIRED",
                    "Cần ghi lý do khi gọi lại sau.");
        }
        String normalized = reason.trim();
        if (normalized.length() < 10 || normalized.length() > 250) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_SKIP_REASON_INVALID",
                    "Lý do gọi lại sau phải từ 10 đến 250 ký tự.");
        }
        return normalized;
    }

    private String normalizeLeaveReason(String reason) {
        if (reason == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_LEAVE_REASON_REQUIRED",
                    "Cần ghi lý do bệnh nhân rời trước khi khám.");
        }
        String normalized = reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_LEAVE_REASON_INVALID",
                    "Lý do phải từ 10 đến 500 ký tự.");
        }
        return normalized;
    }

    private String normalizeAdjustmentReason(String reason) {
        if (reason == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_ADJUSTMENT_REASON_REQUIRED",
                    "Cần ghi lý do điều chỉnh hàng đợi.");
        }
        String normalized = reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "QUEUE_ADJUSTMENT_REASON_INVALID",
                    "Lý do điều chỉnh phải từ 10 đến 500 ký tự.");
        }
        return normalized;
    }

    private ClinicRoom findRoom(String roomCode) {
        return roomRepository.findByCodeAndActiveTrue(roomCode)
                .or(() -> roomRepository.findByQrTokenAndActiveTrue(roomCode))
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND",
                        "Không tìm thấy phòng khám đang hoạt động."));
    }

    private DoctorProfile doctorProfile(UUID staffId) {
        if (doctorProfileRepository == null) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_ASSIGNMENT_REQUIRED",
                    "Bác sĩ chưa được gán chuyên khoa và phòng khám.");
        }
        return doctorProfileRepository.findByStaffAccount_Id(staffId)
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "DOCTOR_ASSIGNMENT_REQUIRED",
                        "Bác sĩ chưa được gán chuyên khoa và phòng khám."));
    }

    private QueueTicket findTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "QUEUE_TICKET_NOT_FOUND",
                        "Không tìm thấy lượt trong hàng đợi."));
    }

    private void ensureBookable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_ACTIONABLE",
                    "Lịch hẹn không còn cho phép lấy số.");
        }
    }

    private void ensureQrCheckInNotLate(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED
                || !appointment.getAppointmentDate().equals(today())) {
            return;
        }
        Instant scheduledEnd = ZonedDateTime.of(
                appointment.getAppointmentDate(), appointment.getStartTime(), CLINIC_ZONE).toInstant();
        Integer duration = appointment.getServiceDurationMinutes();
        long effectiveDuration = duration == null || duration <= 0
                ? DEFAULT_SLOT_DURATION_MINUTES
                : duration;
        scheduledEnd = scheduledEnd.plusSeconds(effectiveDuration * 60L);
        if (!Instant.now(clock).isBefore(scheduledEnd.plusSeconds(15 * 60L))) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_LATE_APPOINTMENT",
                    "Lịch hẹn đã quá giờ check-in; vui lòng đến quầy để được hỗ trợ đổi khung giờ.");
        }
    }

    private void ensureDoctorRoom(Appointment appointment, ClinicRoom room) {
        if (doctorProfileRepository == null || appointment.getDoctorStaffId() == null) return;
        doctorProfileRepository.findByStaffAccount_Id(appointment.getDoctorStaffId())
                .filter(profile -> profile.isActive()
                        && profile.getRoom().getCode().equalsIgnoreCase(room.getCode()))
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "QUEUE_ROOM_MISMATCH",
                        "Vui lòng quét mã tại đúng phòng của bác sĩ trong lịch hẹn."));
    }

    private void ensureDoctorOwnsTicket(QueueTicket ticket, String staffId) {
        if (staffId == null) return;
        UUID doctorId = AuthenticatedIds.staff(staffId);
        // A reception reassignment changes the active queue owner while keeping
        // the original appointment snapshot intact.
        if (!doctorId.equals(ticket.getEffectiveDoctorStaffId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_TICKET_SCOPE",
                    "Bác sĩ chỉ được thao tác trên lượt đã được phân công.");
        }
        DoctorProfile profile = doctorProfile(doctorId);
        if (!profile.getRoom().getCode().equalsIgnoreCase(ticket.getRoom().getCode())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ROOM_SCOPE",
                    "Bác sĩ chỉ được thao tác trong phòng được phân công.");
        }
    }

    private void ensureDoctorCanOperateQueue(QueueTicket ticket) {
        if (!ticket.getQueueDate().equals(today()) || !doctorHasActiveShift(ticket.getAppointment(), ticket.getRoom())) {
            throw new AuthException(HttpStatus.CONFLICT, "DOCTOR_SHIFT_INACTIVE",
                    "Bác sĩ không có ca làm việc đang hiệu lực để gọi bệnh nhân.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }

    private AuthException queueStateConflict(String message) {
        return new AuthException(HttpStatus.CONFLICT, "QUEUE_INVALID_STATE", message);
    }

    private record SessionTransition(ExaminationSession session, String previousStatus) {
    }
}
