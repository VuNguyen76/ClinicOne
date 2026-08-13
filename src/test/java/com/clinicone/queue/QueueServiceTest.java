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
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.ExaminationSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        when(roomRepository.findByCodeAndActiveTrueForUpdate("NOI-01")).thenReturn(Optional.of(room));
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID)).thenReturn(Optional.of(appointment));
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NOI-01", TODAY)).thenReturn(4);
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(examinationSessionRepository.save(any(ExaminationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void pendingActivationPatientCannotCheckInBeforeChangingTemporaryPassword() {
        PatientAccount pending = new PatientAccount("0912345678", "hash", "Nguyen Van A",
                AccountStatus.ACTIVE, true);
        setId(pending, ACCOUNT_ID);
        appointment = Appointment.create(pending, "CL-20260806-1234", "Nội tổng quát", "BS. Nguyễn An",
                TODAY, LocalTime.of(9, 0), "Đau đầu");
        setId(appointment, APPOINTMENT_ID);
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(appointment));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals("PASSWORD_CHANGE_REQUIRED", exception.getCode());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void checkInCreatesOneNumberForTodaysAppointment() {
        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        assertEquals(QueueTicketStatus.WAITING.name(), response.status());
        assertEquals(AppointmentStatus.CHECKED_IN, appointment.getStatus());
        verify(ticketRepository).save(any(QueueTicket.class));
        var sessionCaptor = org.mockito.ArgumentCaptor.forClass(ExaminationSession.class);
        verify(examinationSessionRepository).save(sessionCaptor.capture());
        assertEquals(ExaminationSessionStatus.SCHEDULED, sessionCaptor.getValue().getStatus());
    }

    @Test
    void qrCheckInUsesDefaultSlotDurationWhenServiceSnapshotIsMissing() {
        QueueService serviceAtTwentyMinutesPastStart = new QueueService(roomRepository, ticketRepository,
                appointmentRepository, doctorProfileRepository, examinationSessionRepository,
                Clock.fixed(Instant.parse("2026-08-06T02:20:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        assertDoesNotThrow(() -> serviceAtTwentyMinutesPastStart.checkIn(
                ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));
    }

    @Test
    void checkInStoresTheIdempotencyKeyOnTheAppointment() {
        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID,
                "checkin-key-1");

        assertEquals(5, response.queueNumber());
        assertEquals("checkin-key-1", appointment.getCheckInRequestKey());
    }

    @Test
    void refusesToIssueAThousandthQueueNumber() {
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NOI-01", TODAY)).thenReturn(9999);

        AuthException exception = assertThrows(AuthException.class,
                () -> service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals("QUEUE_NUMBER_LIMIT_REACHED", exception.getCode());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void repeatedScanReturnsExistingTicketWithoutCreatingAnotherNumber() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        appointment.checkIn();
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID))
                .thenReturn(Optional.of(checkedInSession()));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void repeatedScanCanPersistTheFirstRequestKeyWithoutCreatingAnotherTicket() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        appointment.checkIn();
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID))
                .thenReturn(Optional.of(checkedInSession()));

        service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID, "checkin-key-2");

        assertEquals("checkin-key-2", appointment.getCheckInRequestKey());
        verify(appointmentRepository).save(appointment);
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void repeatedScanReturnsCompletedTicketInsteadOfCreatingAnotherQueueEntry() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        appointment.checkIn();
        appointment.complete();
        existing.call();
        existing.complete();
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        assertEquals(QueueTicketStatus.COMPLETED.name(), response.status());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void repeatedScanReturnsLeftBeforeExamOutcomeWithoutReopeningTheAppointment() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        appointment.checkIn();
        existing.leaveBeforeExam("Bệnh nhân rời cơ sở trước khi khám");
        appointment.markNotPerformed();
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        assertEquals(QueueTicketStatus.LEFT_BEFORE_EXAM.name(), response.status());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void returningPatientScanRestoresPresenceWithoutCreatingAnotherTicket() {
        QueueTicket existing = QueueTicket.create(appointment, room, TODAY, 5);
        setId(existing, UUID.randomUUID());
        existing.call();
        existing.skip("Bệnh nhân chưa có mặt");
        appointment.checkIn();
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID))
                .thenReturn(Optional.of(checkedInSession()));

        QueueTicketResponse response = service.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID);

        assertEquals(5, response.queueNumber());
        assertEquals(QueuePresenceStatus.READY.name(), response.presenceStatus());
        verify(ticketRepository).save(existing);
    }

    @Test
    void patientCanReadOwnQueueForTheSelectedDate() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        when(ticketRepository.findByAppointment_Patient_IdAndQueueDateOrderByQueueNumberAsc(ACCOUNT_ID, TODAY))
                .thenReturn(List.of(ticket));

        List<QueueTicketResponse> response = service.listForPatient(ACCOUNT_ID.toString(), TODAY);

        assertEquals(1, response.size());
        assertEquals(5, response.get(0).queueNumber());
        verify(ticketRepository).findByAppointment_Patient_IdAndQueueDateOrderByQueueNumberAsc(ACCOUNT_ID, TODAY);
    }

    @Test
    void callingTheSameTicketAgainRecordsAnotherCallWithoutChangingItsNumber() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);

        ticket.call();
        ticket.skip("Bệnh nhân chưa có mặt");

        assertEquals(5, ticket.getQueueNumber());
        assertEquals(1, ticket.getCallCount());
        org.junit.jupiter.api.Assertions.assertNotNull(ticket.getCalledAt());
        assertEquals(QueuePresenceStatus.RETURN_REQUIRED, ticket.getPresenceStatus());
        assertThrows(IllegalStateException.class, ticket::call);
    }

    @Test
    void patientReturningByQrKeepsNumberAndBecomesReadyAgain() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);

        ticket.call();
        ticket.skip("Bệnh nhân chưa có mặt");
        ticket.markReturned(Instant.parse("2026-08-06T02:20:00Z"));
        ticket.call();

        assertEquals(5, ticket.getQueueNumber());
        assertEquals(2, ticket.getCallCount());
        assertEquals(QueuePresenceStatus.READY, ticket.getPresenceStatus());
        assertEquals(Instant.parse("2026-08-06T02:20:00Z"), ticket.getReturnedAt());
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
                LocalTime.of(9, 0), "Đau đầu");
        setId(doctorsAppointment, UUID.randomUUID());
        QueueTicket first = QueueTicket.create(doctorsAppointment, room, TODAY, 1);
        setId(first, UUID.randomUUID());
        QueueTicket second = QueueTicket.create(doctorsAppointment, room, TODAY, 2);
        setId(second, UUID.randomUUID());
        second.call();
        when(doctorProfileRepository.findByStaffAccount_Id(doctorId)).thenReturn(Optional.of(profile));
        when(ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                "NOI-01", TODAY, doctorId)).thenReturn(List.of(first, second));
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
    void rejectsQrCheckInAfterLateWarningAndKeepsBookedAppointment() {
        Appointment late = appointment("Nội tổng quát", TODAY);
        setField(late, "startTime", LocalTime.of(8, 0));
        setField(late, "serviceDurationMinutes", 60);
        when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(late));
        QueueService lateService = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                doctorProfileRepository, examinationSessionRepository,
                Clock.fixed(Instant.parse("2026-08-06T03:15:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

        AuthException exception = assertThrows(AuthException.class,
                () -> lateService.checkIn(ACCOUNT_ID.toString(), "NOI-01", APPOINTMENT_ID));

        assertEquals("QUEUE_LATE_APPOINTMENT", exception.getCode());
        assertEquals(AppointmentStatus.BOOKED, late.getStatus());
        verify(ticketRepository, never()).save(any(QueueTicket.class));
    }

    @Test
    void cannotCallTicketAgainUntilThePatientScansAgain() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ticket.call();
        ticket.skip("Bệnh nhân chưa có mặt");
        assertThrows(IllegalStateException.class, ticket::call);
    }

    @Test
    void doctorCannotCallPatientAgainOutsideAnActiveShift() {
        UUID doctorId = UUID.fromString("c1e7aa0f-8dc2-4d3d-9d75-7f909e0bb1de");
        StaffAccount staff = StaffAccount.create("doctor-no-shift", "hash", "BS. Nguyễn An", StaffRole.DOCTOR);
        setId(staff, doctorId);
        DoctorProfile profile = DoctorProfile.create(staff, "Nội tổng quát", room);
        setId(profile, UUID.randomUUID());
        Appointment doctorsAppointment = Appointment.create(appointment.getPatient(), doctorId,
                "CL-20260806-NOSHIFT", "Nội tổng quát", "BS. Nguyễn An", TODAY,
                LocalTime.of(9, 0), "Đau đầu");
        QueueTicket ticket = QueueTicket.create(doctorsAppointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ticket.call();

        DoctorScheduleRepository scheduleRepository = mock(DoctorScheduleRepository.class);
        when(doctorProfileRepository.findByStaffAccount_Id(doctorId)).thenReturn(Optional.of(profile));
        when(scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                profile.getId(), TODAY.getDayOfWeek())).thenReturn(List.of());
        QueueService productionService = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                doctorProfileRepository, examinationSessionRepository, null, scheduleRepository, null,
                Clock.fixed(Instant.parse("2026-08-06T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        AuthException exception = assertThrows(AuthException.class,
                () -> productionService.skip(ticket.getId(), doctorId.toString(), "Bệnh nhân chưa có mặt"));

        assertEquals("DOCTOR_SHIFT_INACTIVE", exception.getCode());
        assertEquals(QueueTicketStatus.CALLED, ticket.getStatus());
        verify(ticketRepository, never()).save(ticket);
    }

    @Test
    void receptionCanCloseTicketWhenPatientLeavesBeforeExamination() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ExaminationSession session = ExaminationSession.create(appointment);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(examinationSessionRepository.save(any(ExaminationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QueueTicketResponse response = service.leaveBeforeExam(ticket.getId(), "Bệnh nhân bận việc");

        assertEquals(QueueTicketStatus.LEFT_BEFORE_EXAM.name(), response.status());
        assertEquals(AppointmentStatus.NOT_PERFORMED, appointment.getStatus());
        assertEquals(ExaminationSessionStatus.CANCELLED, session.getStatus());
        verify(appointmentRepository).save(appointment);
        verify(examinationSessionRepository).save(session);
    }

    @Test
    void cannotCloseTicketAfterExaminationHasStarted() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ticket.call();
        ticket.startService();
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.leaveBeforeExam(ticket.getId(), "Bệnh nhân bận việc"));

        assertEquals(409, exception.getStatus().value());
        assertEquals("QUEUE_INVALID_STATE", exception.getCode());
    }

    @Test
    void facilityUnavailableClosesWaitingTicketWithSpecificOutcome() {
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, UUID.randomUUID());
        ExaminationSession session = ExaminationSession.create(appointment);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(examinationSessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(examinationSessionRepository.save(any(ExaminationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QueueTicketResponse response = service.markFacilityUnavailable(ticket.getId(),
                "Phòng khám phải tạm ngưng phục vụ hôm nay.", "receptionist-1");

        assertEquals(QueueTicketStatus.COMPLETED.name(), response.status());
        assertEquals(QueueClosureOutcome.FACILITY_UNAVAILABLE.name(), response.closureOutcome());
        assertEquals(AppointmentStatus.NOT_PERFORMED, appointment.getStatus());
        assertEquals(ExaminationSessionStatus.CANCELLED, session.getStatus());
    }

    @Test
    void receptionCanMoveWaitingTicketToAssignedDoctorQueueAndKeepOriginalAppointment() {
        UUID ticketId = UUID.randomUUID();
        UUID targetDoctorId = UUID.randomUUID();
        StaffAccount targetStaff = StaffAccount.create("doctor-target", "hash", "BS. Nguyễn Bình", StaffRole.DOCTOR);
        setId(targetStaff, targetDoctorId);
        ClinicRoom targetRoom = ClinicRoom.create("NHI-01", "Phòng Nhi 01", "Nhi khoa");
        setId(targetRoom, UUID.randomUUID());
        DoctorProfile targetDoctor = DoctorProfile.create(targetStaff, "Nhi khoa", targetRoom);
        setId(targetDoctor, UUID.randomUUID());
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, ticketId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(doctorProfileRepository.findById(targetDoctorId)).thenReturn(Optional.of(targetDoctor));
        when(roomRepository.findByCodeAndActiveTrueForUpdate("NHI-01")).thenReturn(Optional.of(targetRoom));
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NHI-01", TODAY)).thenReturn(7);
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueTicketResponse response = service.adjust(ticketId,
                new QueueAdjustmentRequest(QueueAdjustmentAction.MOVE, targetDoctorId, "NHI-01", "Nhi khoa",
                        "Điều chuyển theo yêu cầu của phòng khám"),
                "reception-1");

        assertEquals("NHI-01", response.roomCode());
        assertEquals(8, response.queueNumber());
        assertEquals("Nhi khoa", response.specialty());
        assertEquals("BS. Nguyễn Bình", response.doctorName());
        assertEquals("Nội tổng quát", appointment.getSpecialty());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
    }

    @Test
    void receptionCanSetPriorityOnlyBeforeTicketIsCalled() {
        UUID ticketId = UUID.randomUUID();
        QueueTicket ticket = QueueTicket.create(appointment, room, TODAY, 5);
        setId(ticket, ticketId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueTicketResponse response = service.adjust(ticketId,
                new QueueAdjustmentRequest(QueueAdjustmentAction.SET_PRIORITY, null, null, null,
                        "Ưu tiên theo chỉ định vận hành"),
                "reception-1");

        assertEquals(true, response.priority());
        assertThrows(AuthException.class, () -> {
            ticket.call();
            service.adjust(ticketId,
                    new QueueAdjustmentRequest(QueueAdjustmentAction.CLEAR_PRIORITY, null, null, null,
                            "Bỏ ưu tiên sau khi đã gọi"),
                    "reception-1");
        });
    }

    @Test
    void callNextChoosesPriorityTicketBeforeEarlierNormalTicket() {
        UUID doctorId = UUID.randomUUID();
        StaffAccount staff = StaffAccount.create("doctor-priority", "hash", "BS. Nguyễn An", StaffRole.DOCTOR);
        setId(staff, doctorId);
        DoctorProfile profile = DoctorProfile.create(staff, "Nội tổng quát", room);
        Appointment doctorAppointment = Appointment.create(appointment.getPatient(), doctorId,
                "CL-20260806-PRIORITY", "Nội tổng quát", "BS. Nguyễn An", TODAY,
                LocalTime.of(9, 0), "Đau đầu");
        QueueTicket normal = QueueTicket.create(doctorAppointment, room, TODAY, 1);
        QueueTicket priority = QueueTicket.create(doctorAppointment, room, TODAY, 2);
        setId(normal, UUID.randomUUID());
        setId(priority, UUID.randomUUID());
        priority.setPriority(true);
        when(doctorProfileRepository.findByStaffAccount_Id(doctorId)).thenReturn(Optional.of(profile));
        when(ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                "NOI-01", TODAY, doctorId)).thenReturn(List.of(normal, priority));
        when(ticketRepository.findById(priority.getId())).thenReturn(Optional.of(priority));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueTicketResponse response = service.callNext(doctorId.toString(), TODAY);

        assertEquals(priority.getId(), response.id());
    }

    @Test
    void doctorQueueDisplaysPriorityTicketBeforeEarlierNormalTicket() {
        UUID doctorId = UUID.randomUUID();
        StaffAccount staff = StaffAccount.create("doctor-priority-list", "hash", "BS. Nguyễn An", StaffRole.DOCTOR);
        setId(staff, doctorId);
        DoctorProfile profile = DoctorProfile.create(staff, "Nội tổng quát", room);
        Appointment doctorAppointment = Appointment.create(appointment.getPatient(), doctorId,
                "CL-QUEUE-PRIORITY", "Nội tổng quát", "BS. Nguyễn An", TODAY,
                LocalTime.of(9, 0), "Đau đầu");
        QueueTicket normal = QueueTicket.create(doctorAppointment, room, TODAY, 1);
        QueueTicket priority = QueueTicket.create(doctorAppointment, room, TODAY, 2);
        setId(normal, UUID.randomUUID());
        setId(priority, UUID.randomUUID());
        priority.setPriority(true);
        when(doctorProfileRepository.findByStaffAccount_Id(doctorId)).thenReturn(Optional.of(profile));
        when(ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                "NOI-01", TODAY, doctorId)).thenReturn(List.of(normal, priority));

        DoctorQueueResponse response = service.doctorQueue(TODAY, doctorId.toString());

        assertEquals(List.of(priority.getId(), normal.getId()),
                response.tickets().stream().map(QueueTicketResponse::id).toList());
    }

    @Test
    void currentRoutingDoctorCanStartAReassignedTicket() {
        UUID targetDoctorId = UUID.randomUUID();
        StaffAccount targetStaff = StaffAccount.create("doctor-routed", "hash", "BS. Trần Bình", StaffRole.DOCTOR);
        setId(targetStaff, targetDoctorId);
        DoctorProfile targetProfile = DoctorProfile.create(targetStaff, "Nội tổng quát", room);
        QueueTicket routed = QueueTicket.create(appointment, room, TODAY, 6);
        UUID ticketId = UUID.randomUUID();
        setId(routed, ticketId);
        setField(routed, "routingDoctorStaffId", targetDoctorId);
        setField(routed, "routingDoctorName", "BS. Trần Bình");
        setField(routed, "routingSpecialty", "Nội tổng quát");
        routed.call();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(routed));
        when(doctorProfileRepository.findByStaffAccount_Id(targetDoctorId)).thenReturn(Optional.of(targetProfile));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueTicketResponse response = service.start(ticketId, targetDoctorId.toString());

        assertEquals(QueueTicketStatus.IN_SERVICE.name(), response.status());
    }

    private static Appointment appointment(String specialty, LocalDate date) {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        return Appointment.create(account, "CL-20260806-1234", specialty, "BS. Nguyễn An", date,
                LocalTime.of(9, 0), "Đau đầu");
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

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
