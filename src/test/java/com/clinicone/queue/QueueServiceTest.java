package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
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
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        roomRepository = mock(ClinicRoomRepository.class);
        ticketRepository = mock(QueueTicketRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        service = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                Clock.fixed(Instant.parse("2026-08-06T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        room = ClinicRoom.create("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát");
        appointment = appointment("Nội tổng quát", TODAY);
        setId(appointment, APPOINTMENT_ID);
        when(roomRepository.findByCodeAndActiveTrue("NOI-01")).thenReturn(Optional.of(room));
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID)).thenReturn(Optional.of(appointment));
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
    }

    @Test
    void repeatedScanReturnsExistingTicketWithoutCreatingAnotherNumber() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void rejectsCheckInForAnotherSpecialty() {
        Appointment otherSpecialty = appointment("Nhi khoa", TODAY);
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID)).thenReturn(Optional.of(otherSpecialty));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals(409, exception.getStatus().value());
        assertEquals("QUEUE_ROOM_MISMATCH", exception.getCode());
    }

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
