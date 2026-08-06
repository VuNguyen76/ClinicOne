package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AuthException;
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
    private final Clock clock;

    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository) {
        this(roomRepository, ticketRepository, appointmentRepository, Clock.systemUTC());
    }

    @Autowired
    public QueueService(ClinicRoomRepository roomRepository, QueueTicketRepository ticketRepository,
                        AppointmentRepository appointmentRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional
    public QueueTicketResponse checkIn(String accountId, String roomCode, UUID appointmentId) {
        UUID patientId = parseAccountId(accountId);
        ClinicRoom room = findRoom(roomCode);
        Appointment appointment = appointmentRepository.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                        "Không tìm thấy lịch hẹn."));
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
            return QueueTicketResponse.from(ticket);
        }

        int nextNumber = nextNumber(room.getCode(), today);
        try {
            QueueTicket ticket = ticketRepository.save(QueueTicket.create(appointment, room, today, nextNumber));
            return QueueTicketResponse.from(ticket);
        } catch (DataIntegrityViolationException exception) {
            return ticketRepository.findByAppointmentId(appointmentId)
                    .map(QueueTicketResponse::from)
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

    @Transactional
    public QueueTicketResponse call(UUID ticketId) {
        QueueTicket ticket = findTicket(ticketId);
        try {
            ticket.call();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public QueueTicketResponse skip(UUID ticketId, String reason) {
        QueueTicket ticket = findTicket(ticketId);
        try {
            ticket.skip(reason);
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public QueueTicketResponse start(UUID ticketId) {
        QueueTicket ticket = findTicket(ticketId);
        try {
            ticket.startService();
        } catch (IllegalStateException exception) {
            throw queueStateConflict(exception.getMessage());
        }
        return QueueTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public QueueTicketResponse complete(UUID ticketId) {
        QueueTicket ticket = findTicket(ticketId);
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

    private ClinicRoom findRoom(String roomCode) {
        return roomRepository.findByCodeAndActiveTrue(roomCode)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND",
                        "Không tìm thấy phòng khám đang hoạt động."));
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
