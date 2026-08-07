package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class QueueService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ClinicRoomRepository roomRepository;
    private final QueueTicketRepository ticketRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ExaminationSessionRepository examinationSessionRepository;
    private final Clock clock;

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository) {
        this(roomRepository, ticketRepository, appointmentRepository, null, null, Clock.systemUTC());
    }

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, Clock clock) {
        this(roomRepository, ticketRepository, appointmentRepository, null, null, clock);
    }

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, DoctorProfileRepository doctorProfileRepository,
                        Clock clock) {
        this(roomRepository, ticketRepository, appointmentRepository, doctorProfileRepository, null, clock);
    }

    @Autowired
    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, DoctorProfileRepository doctorProfileRepository,
                        ExaminationSessionRepository examinationSessionRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.examinationSessionRepository = examinationSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public QueueTicketResponse checkIn(String accountId, String roomCode, UUID appointmentId) {
        UUID patientId = parseAccountId(accountId);
        Appointment appointment = appointmentRepository.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        return checkInAppointment(roomCode, appointment);
    }

    @Transactional
    public QueueTicketResponse checkInByStaff(String roomCode, UUID appointmentId, String exceptionReason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
        return checkInAppointment(roomCode, appointment, normalizeExceptionReason(exceptionReason));
    }

    private QueueTicketResponse checkInAppointment(String roomCode, Appointment appointment) {
        return checkInAppointment(roomCode, appointment, null);
    }

    private QueueTicketResponse checkInAppointment(String roomCode, Appointment appointment, String exceptionReason) {
        ClinicRoom room = findRoom(roomCode);
        ensureBookable(appointment);
        LocalDate today = today();
        if (!appointment.getAppointmentDate().equals(today)) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_DATE_NOT_OPEN",
                    "Chỉ có thể lấy số cho lịch hẹn trong ngày hôm nay.");
        }
        if (!room.getSpecialty().equalsIgnoreCase(appointment.getSpecialty())) {
            throw new AuthException(HttpStatus.CONFLICT, "QUEUE_ROOM_MISMATCH",
                    "Phòng này không thuộc chuyên khoa của lịch hẹn.");
        }
        ensureDoctorRoom(appointment, room);

        UUID appointmentId = appointment.getId();
        var existing = ticketRepository.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            QueueTicket ticket = existing.get();
            if (!ticket.getRoom().getCode().equalsIgnoreCase(room.getCode())) {
                throw new AuthException(HttpStatus.CONFLICT, "QUEUE_ALREADY_CHECKED_IN",
                        "Lịch hẹn đã lấy số tại phòng khác.");
            }
            if (ticket.getStatus() == QueueTicketStatus.COMPLETED) {
                throw new AuthException(HttpStatus.CONFLICT, "QUEUE_ALREADY_COMPLETED",
                        "Lượt khám này đã hoàn tất.");
            }
            if (exceptionReason != null) {
                ticket.recordExceptionReason(exceptionReason);
                ticketRepository.save(ticket);
            }
            ensureCheckedInSession(appointment);
            return QueueTicketResponse.from(ticket);
        }

        int nextNumber = nextNumber(room.getCode(), today);
        try {
            QueueTicket ticket = ticketRepository.save(QueueTicket.create(appointment, room, today, nextNumber, exceptionReason));
            ensureCheckedInSession(appointment);
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
        return ticketRepository.findByRoomCodeAndQueueDateOrderByQueueNumberAsc(roomCode, queueDate).stream()
                .map(QueueTicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueTicketResponse> listForStaff(String roomCode, LocalDate date, String staffId, StaffRole role) {
        if (role == StaffRole.DOCTOR) {
            UUID doctorId = parseStaffId(staffId);
            DoctorProfile profile = doctorProfile(doctorId);
            if (!profile.getRoom().getCode().equalsIgnoreCase(roomCode)) {
                throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ROOM_SCOPE",
                        "Bác sĩ chỉ được xem hàng đợi của phòng được phân công.");
            }
            LocalDate queueDate = date == null ? today() : date;
            return ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                            roomCode, queueDate, doctorId).stream()
                    .map(QueueTicketResponse::from)
                    .toList();
        }
        return list(roomCode, date);
    }

    @Transactional(readOnly = true)
    public DoctorQueueResponse doctorQueue(LocalDate date, String staffId) {
        UUID doctorId = parseStaffId(staffId);
        DoctorProfile profile = doctorProfile(doctorId);
        LocalDate queueDate = date == null ? today() : date;
        List<QueueTicketResponse> tickets = ticketRepository
                .findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                        profile.getRoom().getCode(), queueDate, doctorId).stream()
                .map(QueueTicketResponse::from)
                .toList();
        return new DoctorQueueResponse(profile.getRoom().getCode(), profile.getRoom().getName(),
                profile.getSpecialty(), tickets);
    }

    @Transactional
    public QueueTicketResponse call(UUID ticketId) {
        return call(ticketId, null);
    }

    @Transactional
    public QueueTicketResponse call(UUID ticketId, String staffId) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        try {
            ticket.call();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public QueueTicketResponse callNext(String staffId, LocalDate date) {
        UUID doctorId = parseStaffId(staffId);
        DoctorProfile profile = doctorProfile(doctorId);
        LocalDate queueDate = date == null ? today() : date;
        QueueTicket next = ticketRepository
                .findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                        profile.getRoom().getCode(), queueDate, doctorId).stream()
                .filter(ticket -> ticket.getStatus() == QueueTicketStatus.WAITING
                        || ticket.getStatus() == QueueTicketStatus.SKIPPED)
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "QUEUE_NO_NEXT_PATIENT",
                        "Không còn bệnh nhân đang chờ trong hàng đợi."));
        return call(next.getId(), staffId);
    }

    @Transactional
    public QueueTicketResponse skip(UUID ticketId, String reason) {
        return skip(ticketId, null, reason);
    }

    @Transactional
    public QueueTicketResponse skip(UUID ticketId, String staffId, String reason) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        try {
            ticket.skip(reason);
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public QueueTicketResponse start(UUID ticketId) {
        return start(ticketId, null);
    }

    @Transactional
    public QueueTicketResponse start(UUID ticketId, String staffId) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        try {
            ticket.startService();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public QueueTicketResponse complete(UUID ticketId) {
        return complete(ticketId, null);
    }

    @Transactional
    public QueueTicketResponse complete(UUID ticketId, String staffId) {
        QueueTicket ticket = findTicket(ticketId);
        ensureDoctorOwnsTicket(ticket, staffId);
        try {
            ticket.complete();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        ticket.getAppointment().complete();
        appointmentRepository.save(ticket.getAppointment());
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    private int nextNumber(String roomCode, LocalDate date) {
        Integer currentMax = ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate(roomCode, date);
        return currentMax == null ? 1 : currentMax + 1;
    }

    private void ensureCheckedInSession(Appointment appointment) {
        if (examinationSessionRepository == null) {
            return;
        }
        ExaminationSession session = examinationSessionRepository.findByAppointment_Id(appointment.getId())
                .orElseGet(() -> ExaminationSession.create(appointment));
        session.checkIn();
        examinationSessionRepository.save(session);
    }

    private String normalizeExceptionReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() < 3 || normalized.length() > 250) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "RECEPTION_REASON_INVALID",
                    "Lý do hỗ trợ tại quầy phải từ 3 đến 250 ký tự.");
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

    private UUID parseStaffId(String staffId) {
        try {
            return UUID.fromString(staffId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập nhân viên không hợp lệ.");
        }
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
        UUID doctorId = parseStaffId(staffId);
        if (!doctorId.equals(ticket.getAppointment().getDoctorStaffId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_TICKET_SCOPE",
                    "Bác sĩ chỉ được thao tác trên lượt đã được phân công.");
        }
        DoctorProfile profile = doctorProfile(doctorId);
        if (!profile.getRoom().getCode().equalsIgnoreCase(ticket.getRoom().getCode())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "DOCTOR_ROOM_SCOPE",
                    "Bác sĩ chỉ được thao tác trong phòng được phân công.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(CLINIC_ZONE));
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập không hợp lệ.");
        }
    }

    private AuthException queueStateConflict(String message) {
        return new AuthException(HttpStatus.CONFLICT, "QUEUE_INVALID_STATE", message);
    }
}
