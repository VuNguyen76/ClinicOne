package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentService;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.examination.ExaminationSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QueueServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID APPOINTMENT_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 6);

    private ClinicRoomRepository roomRepository;
    private QueueTicketRepository ticketRepository;
    private AppointmentRepository appointmentRepository;
    private QueueService service;
    private ClinicRoom room;
    private Appointment appointment;
    private ExaminationSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        roomRepository = mock(ClinicRoomRepository.class);
        ticketRepository = mock(QueueTicketRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        sessionRepository = mock(ExaminationSessionRepository.class);
        AppointmentService appointmentService = mock(AppointmentService.class);
        service = new QueueService(roomRepository, ticketRepository, appointmentService, appointmentRepository, sessionRepository,
                Clock.fixed(Instant.parse("2026-08-06T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        room = ClinicRoom.create("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát");
        appointment = appointment("Nội tổng quát", TODAY);
        setId(appointment, APPOINTMENT_ID);
        when(roomRepository.findByCodeAndActiveTrue("NOI-01")).thenReturn(Optional.of(room));
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(appointment));
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NOI-01", TODAY)).thenReturn(4);
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void checkInCreatesOneNumberForTodaysAppointment() {
        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        assertEquals(QueueTicketStatus.WAITING.name(), response.status());
        verify(ticketRepository).save(any(QueueTicket.class));
    }// AC-REC-01-01

    @Test
    void rejectsCheckInWhenQueueIsFull() {
        // Giả lập hàng đợi hôm nay tại phòng này đã đạt 999 số
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NOI-01", TODAY)).thenReturn(999);

        AuthException exception = assertThrows(AuthException.class,
                () -> service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals(409, exception.getStatus().value());
        assertEquals("QUEUE_MAX_CAPACITY", exception.getCode());
    }// AC-REC-01-02

    @Test
    void checkInUpdatesAppointmentAndCreatesExaminationSession() {
        service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        // 1. Kiểm tra Lịch hẹn được đổi sang CHECKED_IN và lưu lại
        assertEquals(AppointmentStatus.CHECKED_IN, appointment.getStatus());
        verify(appointmentRepository).save(appointment);

        // 2. Kiểm tra Lượt khám ExaminationSession được tạo và lưu
        verify(sessionRepository).save(any(com.clinicone.examination.ExaminationSession.class));
    }// AC-REC-01-03

    @Test
    void repeatedScanReturnsExistingTicketWithoutCreatingAnotherNumber() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }// AC-REC-01-04

    @Test
    void rejectsCheckInForAnotherSpecialty() {
        Appointment otherSpecialty = appointment("Nhi khoa", TODAY);
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(otherSpecialty));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals(409, exception.getStatus().value());
        assertEquals("QUEUE_ROOM_MISMATCH", exception.getCode());
    }// AC-REC-01-05

    @Test
    void printBackendDataAfterCheckIn() {
        // 1. Thuc hien check-in (Chuyen trang thai -> Tao luot kham -> Add vao hang doi)
        QueueTicketResponse result = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        // 2. In du lieu ra Console kiem tra 3 bang
        System.out.println("\n========== KET QUA CHECK-IN THANH CONG ==========");
        
        // Bang 1: Lich hen (Appointment)
        System.out.println("[BANG 1 - APPOINTMENT]");
        System.out.println(" - ID Lich hen   : " + appointment.getId());
        System.out.println(" - Trang thai moi: " + appointment.getStatus() + " (" + appointment.getStatus().label() + ")");

        // Bang 2: Hang doi (QueueTicket) - ADD BENH NHAN VAO HANG DOI PHONG
        System.out.println("\n[BANG 2 - QUEUE_TICKET (ADD BENH NHAN VAO HANG DOI)]");
        System.out.println(" - So thu tu (STT): " + result.queueNumber());
        System.out.println(" - Ma phong       : " + result.roomCode());
        System.out.println(" - Trang thai ve  : " + result.status() + " (" + result.statusLabel() + ")");

        // Bang 3: Luot kham (ExaminationSession)
        System.out.println("\n[BANG 3 - EXAMINATION_SESSION]");
        System.out.println(" - Kiem tra tao luot kham: Da tao thanh cong cho Lich hen ID " + APPOINTMENT_ID);
        
        System.out.println("==================================================\n");

        // 3. Khang dinh du lieu bang Assert
        assertEquals(AppointmentStatus.CHECKED_IN, appointment.getStatus());
        assertEquals(5, result.queueNumber());
        verify(sessionRepository).save(any(com.clinicone.examination.ExaminationSession.class));
    }// luồng chuyển trạng thái

    @Test
    void rejectsCheckInOutsideAppointmentDate() {
        Appointment tomorrow = appointment("Nội tổng quát", TODAY.plusDays(1));
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID)).thenReturn(Optional.of(tomorrow));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals(409, exception.getStatus().value());
        assertEquals("QUEUE_DATE_NOT_OPEN", exception.getCode());
    }

    @Test
    void canCallTicketAgainAfterItWasSkipped() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ticket.call();
        ticket.skip("Bệnh nhân chưa có mặt");
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        QueueTicketResponse response = service.call(ticket.getId());

        assertEquals(QueueTicketStatus.CALLED.name(), response.status());
        assertEquals(QueueTicketStatus.CALLED.label(), response.statusLabel());
    }

    @Test
    void completingTicketCompletesAppointment() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ticket.call();
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.complete(ticket.getId());

        assertEquals(QueueTicketStatus.COMPLETED, ticket.getStatus());
        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void walkInRejectsWhenDoctorExceedsOverCapacityLimit() {
        // AC-REC-03-03: Chặn nếu bác sĩ đã có >= 3 ca ngoại lệ trong ngày
        UUID doctorId = UUID.randomUUID();
        WalkInCheckInRequest request = new WalkInCheckInRequest(
                "0912345678", "Nội tổng quát", "Đau bụng dữ dội", doctorId, "Bệnh nhân cấp cứu cần khám gấp"
        );

        // Giả lập DB trả về số lượng ca ngoại lệ đã >= 3 
        // (Sử dụng đúng biến appointmentRepository của bạn)
        when(appointmentRepository.countByDoctorStaffIdAndAppointmentDateAndStartTimeAndStatus(
                eq(doctorId), 
                any(LocalDate.class), 
                any(), 
                any()
        )).thenReturn(3L);

        // Gọi hàm từ biến service của bạn và kiểm tra xem hệ thống có văng lỗi chặn lại không
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.processWalkInCheckIn(request, "receptionist-id");
        });

        assertTrue(exception.getMessage().contains("OVER_CAPACITY_LIMIT_REACHED"));
    }

    private static Appointment appointment(String specialty, LocalDate date) {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        return Appointment.create(account, "CL-20260806-1234", specialty, "BS. Nguyễn An", date,
                java.time.LocalTime.of(9, 0), "Đau đầu");
    }

    private static void setId(Object target, UUID id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
