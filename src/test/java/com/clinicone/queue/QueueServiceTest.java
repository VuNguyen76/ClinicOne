package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.ExaminationSessionStatus;
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
    private DoctorProfileRepository doctorProfileRepository;
    private ExaminationSessionRepository examinationSessionRepository;
    private QueueService service;
    private ClinicRoom room;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        roomRepository = mock(ClinicRoomRepository.class);
        ticketRepository = mock(QueueTicketRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        doctorProfileRepository = mock(DoctorProfileRepository.class);
        examinationSessionRepository = mock(ExaminationSessionRepository.class);
        service = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                doctorProfileRepository, examinationSessionRepository,
                Clock.fixed(Instant.parse("2026-08-06T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        room = ClinicRoom.create("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát");
        appointment = appointment("Nội tổng quát", TODAY);
        setId(appointment, APPOINTMENT_ID);
        when(roomRepository.findByCodeAndActiveTrue("NOI-01")).thenReturn(Optional.of(room));
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID)).thenReturn(Optional.of(appointment));
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NOI-01", TODAY)).thenReturn(4);
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(examinationSessionRepository.save(any(ExaminationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void checkInCreatesOneNumberForTodaysAppointment() {
        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        assertEquals(QueueTicketStatus.WAITING.name(), response.status());
        verify(ticketRepository).save(any(QueueTicket.class));
        var sessionCaptor = org.mockito.ArgumentCaptor.forClass(ExaminationSession.class);
        verify(examinationSessionRepository).save(sessionCaptor.capture());
        assertEquals(ExaminationSessionStatus.CHECKED_IN, sessionCaptor.getValue().getStatus());
    }

    @Test
    void repeatedScanReturnsExistingTicketWithoutCreatingAnotherNumber() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID))
                .thenReturn(Optional.of(checkedInSession()));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void createsCheckedInSessionWhenExistingQueueTicketHasNoSession() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        verify(examinationSessionRepository).save(any(ExaminationSession.class));
    }

    @Test
    void recordsReceptionReasonOnExceptionCheckIn() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        service.checkInByStaff("NOI-01", APPOINTMENT_ID, "QR phòng bị lỗi");

        var ticketCaptor = org.mockito.ArgumentCaptor.forClass(QueueTicket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals("QR phòng bị lỗi", ticketCaptor.getValue().getExceptionReason());
    }

    @Test
    void callNextUsesFirstWaitingTicketFromDoctorsOwnRoom() {
        UUID doctorId = UUID.fromString("c1e7aa0f-8dc2-4d3d-9d75-7f909e0bb1de");
        StaffAccount staff = StaffAccount.create("doctor", "hash", "BS. Nguyễn An", StaffRole.DOCTOR);
        setId(staff, doctorId);
        DoctorProfile profile = DoctorProfile.create(staff, "Nội tổng quát", room);
        Appointment doctorsAppointment = Appointment.create(appointment.getPatient(), doctorId,
                "CL-20260806-DOCTOR", "Nội tổng quát", "BS. Nguyễn An", TODAY,
                java.time.LocalTime.of(9, 0), "Đau đầu");
        setId(doctorsAppointment, UUID.randomUUID());
        QueueTicket first = QueueTicket.create(doctorsAppointment, room, TODAY, 1);
        setId(first, UUID.randomUUID());
        QueueTicket second = QueueTicket.create(doctorsAppointment, room, TODAY, 2);
        setId(second, UUID.randomUUID());
        second.call();
        when(doctorProfileRepository.findByStaffAccount_Id(doctorId)).thenReturn(Optional.of(profile));
        when(ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                "NOI-01", TODAY, doctorId)).thenReturn(java.util.List.of(first, second));
        when(ticketRepository.findById(first.getId())).thenReturn(Optional.of(first));

        QueueTicketResponse response = service.callNext(doctorId.toString(), TODAY);

        assertEquals(first.getId(), response.id());
        assertEquals(QueueTicketStatus.CALLED.name(), response.status());
        verify(ticketRepository).save(first);
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

    private ExaminationSession checkedInSession() {
        ExaminationSession session = ExaminationSession.create(appointment);
        session.checkIn();
        return session;
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
