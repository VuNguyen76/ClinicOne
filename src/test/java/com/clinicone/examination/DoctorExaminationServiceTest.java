package com.clinicone.examination;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.notification.PatientNotificationService;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorExaminationServiceTest {
    private static final UUID DOCTOR_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID OTHER_DOCTOR_ID = UUID.fromString("8d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID APPOINTMENT_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID TICKET_ID = UUID.fromString("bd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID SESSION_ID = UUID.fromString("cd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID RECORD_ID = UUID.fromString("dd9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    private QueueTicketRepository ticketRepository;
    private ExaminationSessionRepository sessionRepository;
    private MedicalRecordRepository recordRepository;
    private StaffAccountRepository staffRepository;
    private AppointmentRepository appointmentRepository;
    private DoctorProfileRepository profileRepository;
    private PatientNotificationService notificationService;
    private DoctorExaminationService service;
    private QueueTicket ticket;
    private ExaminationSession session;
    private MedicalRecord record;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(QueueTicketRepository.class);
        sessionRepository = mock(ExaminationSessionRepository.class);
        recordRepository = mock(MedicalRecordRepository.class);
        staffRepository = mock(StaffAccountRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        profileRepository = mock(DoctorProfileRepository.class);
        notificationService = mock(PatientNotificationService.class);
        service = new DoctorExaminationService(ticketRepository, sessionRepository, recordRepository,
                staffRepository, appointmentRepository, profileRepository, notificationService);

        ClinicRoom room = ClinicRoom.create("NOI-01", "Phòng Nội 01", "Nội tổng quát");
        StaffAccount doctor = StaffAccount.create("bs.an", "hash", "Bác sĩ Nguyễn An", StaffRole.DOCTOR);
        setId(doctor, DOCTOR_ID);
        DoctorProfile profile = DoctorProfile.create(doctor, "Nội tổng quát", room);
        PatientAccount patient = new PatientAccount("0912345678", "hash", "Nguyễn Thanh Vũ",
                AccountStatus.ACTIVE, false);
        setId(patient, UUID.fromString("ed9e3fb4-1045-4ca4-86d2-7d1fca4c1a13"));
        appointment = Appointment.create(patient, DOCTOR_ID, "CL-E2E-001", "Nội tổng quát",
                "Bác sĩ Nguyễn An", LocalDate.of(2026, 8, 9), LocalTime.of(9, 0), "Đau đầu");
        setId(appointment, APPOINTMENT_ID);
        ticket = QueueTicket.create(appointment, room, appointment.getAppointmentDate(), 5);
        setId(ticket, TICKET_ID);
        ticket.call();
        ticket.startService();
        session = ExaminationSession.create(appointment);
        setId(session, SESSION_ID);
        session.begin();
        record = MedicalRecord.draft(session);
        setId(record, RECORD_ID);
        setField(record, "version", 0L);

        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(sessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(recordRepository.findBySession_Id(SESSION_ID)).thenReturn(Optional.of(record));
        when(staffRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(profileRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.findByStaffAccount_IdForUpdate(DOCTOR_ID)).thenReturn(Optional.of(profile));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(ExaminationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void signingCompletesTheWholeExaminationAndNotifiesPatient() {
        DoctorExaminationResponse response = service.sign(TICKET_ID, DOCTOR_ID.toString(), request());

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.signedAt()).isNotNull();
        assertThat(ticket.getStatus()).isEqualTo(com.clinicone.queue.QueueTicketStatus.COMPLETED);
        assertThat(appointment.getStatus()).isEqualTo(com.clinicone.appointment.AppointmentStatus.COMPLETED);
        assertThat(session.getStatus()).isEqualTo(ExaminationSessionStatus.COMPLETED);
        assertThat(record.getSignedAt()).isNotNull();
        verify(appointmentRepository).save(appointment);
        verify(ticketRepository).save(ticket);
        verify(sessionRepository).save(session);
        verify(recordRepository).saveAndFlush(record);
        verify(notificationService).notifyMedicalRecordSigned(any(), any(), any(), any(), any());
    }

    @Test
    void repeatingSignReturnsTheExistingSignedRecordWithoutChangingItOrNotifyingAgain() {
        DoctorExaminationResponse first = service.sign(TICKET_ID, DOCTOR_ID.toString(), request());

        DoctorExaminationRequest retriedRequest = new DoctorExaminationRequest(
                "Nội dung không được ghi đè", "Ghi nhận khác", "Chẩn đoán khác", "Kết luận khác",
                null, null, null, 0L);
        DoctorExaminationResponse retried = service.sign(TICKET_ID, DOCTOR_ID.toString(), retriedRequest);

        assertThat(retried.status()).isEqualTo("COMPLETED");
        assertThat(retried.signedAt()).isEqualTo(first.signedAt());
        assertThat(retried.reason()).isEqualTo("Đau đầu");
        verify(notificationService, times(1)).notifyMedicalRecordSigned(any(), any(), any(), any(), any());
        verify(recordRepository, times(1)).saveAndFlush(record);
    }

    @Test
    void endingVisitWithoutMedicalRecordCompletesOnlyAfterDoctorAction() {
        appointment.applyServiceSnapshot(UUID.randomUUID(), "Tiếp nhận nhanh", "Tư vấn", 15, false);

        DoctorExaminationResponse response = service.sign(TICKET_ID, DOCTOR_ID.toString(),
                new DoctorExaminationRequest(null, null, null, null, null, null, null, null));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.requiresMedicalRecord()).isFalse();
        assertThat(response.reason()).isNull();
        assertThat(ticket.getStatus()).isEqualTo(com.clinicone.queue.QueueTicketStatus.COMPLETED);
        assertThat(appointment.getStatus()).isEqualTo(com.clinicone.appointment.AppointmentStatus.COMPLETED);
        assertThat(session.getStatus()).isEqualTo(ExaminationSessionStatus.COMPLETED);
        verify(recordRepository, never()).findBySession_Id(SESSION_ID);
        verify(recordRepository, never()).save(any(MedicalRecord.class));
        verify(notificationService, never()).notifyMedicalRecordSigned(any(), any(), any(), any(), any());
    }

    @Test
    void signingSnapshotsEachPrescriptionLine() {
        DoctorExaminationRequest request = new DoctorExaminationRequest(
                "Đau đầu", "Mạch ổn", "Đau đầu căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, null, 0L,
                List.of(new PrescriptionLineRequest(null, "Paracetamol 500 mg", "500 mg", 10, "Uống sau ăn")));

        DoctorExaminationResponse response = service.sign(TICKET_ID, DOCTOR_ID.toString(), request);

        assertThat(response.prescriptionLines()).singleElement().satisfies(line -> {
            assertThat(line.medicationName()).isEqualTo("Paracetamol 500 mg");
            assertThat(line.dosage()).isEqualTo("500 mg");
            assertThat(line.quantity()).isEqualTo(10);
            assertThat(line.instructions()).isEqualTo("Uống sau ăn");
        });
    }

    @Test
    void signingStoresTheFollowUpIntervalAndNote() {
        DoctorExaminationRequest request = new DoctorExaminationRequest(
                "Đau đầu", "Mạch ổn", "Đau đầu căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, null, 0L,
                List.of(), 14, "Tái khám nếu triệu chứng còn kéo dài");

        DoctorExaminationResponse response = service.sign(TICKET_ID, DOCTOR_ID.toString(), request);

        assertThat(response.followUpDays()).isEqualTo(14);
        assertThat(response.followUpNote()).isEqualTo("Tái khám nếu triệu chứng còn kéo dài");
    }

    @Test
    void signingRejectsFollowUpNoteWithoutAValidInterval() {
        DoctorExaminationRequest request = new DoctorExaminationRequest(
                "Đau đầu", "Mạch ổn", "Đau đầu căng thẳng", "Theo dõi thêm", "Nghỉ ngơi", null, null, 0L,
                List.of(), null, "Tái khám nếu còn đau");

        assertThatThrownBy(() -> service.sign(TICKET_ID, DOCTOR_ID.toString(), request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Số ngày tái khám");
    }

    @Test
    void signingRejectsPrescriptionLineMissingRequiredDetails() {
        DoctorExaminationRequest request = new DoctorExaminationRequest(
                "Đau đầu", "Mạch ổn", "Đau đầu căng thẳng", "Theo dõi thêm", null, null, null, 0L,
                List.of(new PrescriptionLineRequest(null, "Paracetamol 500 mg", "", 10, "Uống sau ăn")));

        assertThatThrownBy(() -> service.sign(TICKET_ID, DOCTOR_ID.toString(), request))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getCode()).isEqualTo("PRESCRIPTION_LINE_INVALID"));
        assertThat(record.getSignedAt()).isNull();
    }

    @Test
    void repeatingCompletionWithoutMedicalRecordReturnsTheCompletedVisit() {
        appointment.applyServiceSnapshot(UUID.randomUUID(), "Tiếp nhận nhanh", "Tư vấn", 15, false);
        DoctorExaminationRequest emptyRequest = new DoctorExaminationRequest(null, null, null, null, null, null, null, null);

        DoctorExaminationResponse first = service.sign(TICKET_ID, DOCTOR_ID.toString(), emptyRequest);
        DoctorExaminationResponse retried = service.sign(TICKET_ID, DOCTOR_ID.toString(), emptyRequest);

        assertThat(retried.status()).isEqualTo("COMPLETED");
        assertThat(retried.examinationId()).isEqualTo(first.examinationId());
        verify(notificationService, never()).notifyMedicalRecordSigned(any(), any(), any(), any(), any());
    }

    @Test
    void openingVisitWithoutMedicalRecordDoesNotCreateDraft() {
        appointment.applyServiceSnapshot(UUID.randomUUID(), "Tư vấn nhanh", "Tư vấn", 15, false);

        DoctorExaminationResponse response = service.open(TICKET_ID, DOCTOR_ID.toString());

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.requiresMedicalRecord()).isFalse();
        assertThat(response.reason()).isNull();
        verify(recordRepository, never()).findBySession_Id(SESSION_ID);
        verify(recordRepository, never()).save(any(MedicalRecord.class));
    }

    @Test
    void repeatedStartWithTheSameKeyKeepsOneStartTimeAndOneDraft() {
        ticket = QueueTicket.create(appointment, ticket.getRoom(), appointment.getAppointmentDate(), 5);
        setId(ticket, TICKET_ID);
        ticket.call();
        session = ExaminationSession.create(appointment);
        setId(session, SESSION_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(sessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByAppointment_IdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByStartRequestKey("start-visit-1")).thenReturn(Optional.empty());
        when(recordRepository.findBySession_Id(SESSION_ID)).thenReturn(Optional.empty());

        DoctorExaminationResponse started = service.start(TICKET_ID, DOCTOR_ID.toString(), "start-visit-1");
        DoctorExaminationResponse retried = service.start(TICKET_ID, DOCTOR_ID.toString(), "start-visit-1");

        assertThat(started.status()).isEqualTo("IN_PROGRESS");
        assertThat(retried.status()).isEqualTo("IN_PROGRESS");
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getStartRequestKey()).isEqualTo("start-visit-1");
        verify(ticketRepository, times(1)).save(ticket);
        verify(sessionRepository, times(1)).save(session);
        verify(recordRepository, times(1)).save(any(MedicalRecord.class));
    }

    @Test
    void startingRequiresAnIdempotencyKey() {
        assertThatThrownBy(() -> service.start(TICKET_ID, DOCTOR_ID.toString(), null))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void openingACalledTicketCannotStartTheVisitByItself() {
        ticket = QueueTicket.create(appointment, ticket.getRoom(), appointment.getAppointmentDate(), 5);
        setId(ticket, TICKET_ID);
        ticket.call();
        session = ExaminationSession.create(appointment);
        setId(session, SESSION_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(sessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByAppointment_IdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.open(TICKET_ID, DOCTOR_ID.toString()))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getCode()).isEqualTo("QUEUE_INVALID_STATE"));
        assertThat(session.getStatus()).isEqualTo(ExaminationSessionStatus.SCHEDULED);
        verify(ticketRepository, never()).save(ticket);
        verify(sessionRepository, never()).save(session);
    }

    @Test
    void startingASecondVisitForTheSameDoctorIsRejected() {
        ticket = QueueTicket.create(appointment, ticket.getRoom(), appointment.getAppointmentDate(), 5);
        setId(ticket, TICKET_ID);
        ticket.call();
        session = ExaminationSession.create(appointment);
        setId(session, SESSION_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(sessionRepository.findByAppointment_Id(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByAppointment_IdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByStartRequestKey("start-visit-2")).thenReturn(Optional.empty());
        when(ticketRepository.countInServiceForDoctorExcludingTicket(DOCTOR_ID, TICKET_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.start(TICKET_ID, DOCTOR_ID.toString(), "start-visit-2"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getCode())
                        .isEqualTo("DOCTOR_ACTIVE_EXAMINATION"));
        verify(ticketRepository, never()).save(ticket);
        verify(sessionRepository, never()).save(session);
    }

    @Test
    void returnsAtMostTenMostRecentSignedVisitsForThePatientInTheDoctorWorkspace() {
        ExaminationSession previousSession = ExaminationSession.create(appointment);
        MedicalRecord previousRecord = MedicalRecord.draft(previousSession);
        previousRecord.sign("Bác sĩ cũ", "Đau đầu", "Đã khám", "Đau đầu căng thẳng",
                "Theo dõi", "Nghỉ ngơi", null, null);
        when(recordRepository.findTop10BySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(
                appointment.getPatient().getId())).thenReturn(List.of(previousRecord));

        DoctorExaminationResponse response = service.open(TICKET_ID, DOCTOR_ID.toString());

        assertThat(response.history()).singleElement().satisfies(item -> {
            assertThat(item.doctorName()).isEqualTo("Bác sĩ cũ");
            assertThat(item.diagnosis()).isEqualTo("Đau đầu căng thẳng");
        });
        verify(recordRepository).findTop10BySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDesc(
                appointment.getPatient().getId());
    }

    @Test
    void anotherDoctorCannotOpenOrSignTheTicket() {
        assertThatThrownBy(() -> service.sign(TICKET_ID, OTHER_DOCTOR_ID.toString(), request()))
                .isInstanceOf(AuthException.class)
                .hasMessage("Bác sĩ chỉ được mở phiếu của lượt đã được phân công.");
        assertThat(record.getSignedAt()).isNull();
    }

    @Test
    void rejectsAStaleDraftInsteadOfOverwritingNewerClinicalNotes() {
        setField(record, "version", 3L);
        DoctorExaminationRequest staleDraft = new DoctorExaminationRequest(
                "Đau đầu", "Ghi nhận từ tab cũ", "Đau đầu căng thẳng", "Theo dõi thêm",
                "Nghỉ ngơi", null, null, 2L);

        assertThatThrownBy(() -> service.saveDraft(TICKET_ID, DOCTOR_ID.toString(), staleDraft))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> {
                    AuthException exception = (AuthException) error;
                    assertThat(exception.getCode()).isEqualTo("MEDICAL_RECORD_VERSION_CONFLICT");
                });
        assertThat(record.getExaminationNotes()).isNull();
        verify(recordRepository, never()).save(record);
    }

    @Test
    void reassignedTicketCanBeOpenedByItsCurrentRoutingDoctor() {
        StaffAccount reassignedDoctor = StaffAccount.create("bs.binh", "hash", "Bác sĩ Trần Bình", StaffRole.DOCTOR);
        setId(reassignedDoctor, OTHER_DOCTOR_ID);
        DoctorProfile reassignedProfile = DoctorProfile.create(reassignedDoctor, "Nội tổng quát", ticket.getRoom());
        setField(ticket, "routingDoctorStaffId", OTHER_DOCTOR_ID);
        setField(ticket, "routingDoctorName", "Bác sĩ Trần Bình");
        setField(ticket, "routingSpecialty", "Nội tổng quát");
        when(staffRepository.findById(OTHER_DOCTOR_ID)).thenReturn(Optional.of(reassignedDoctor));
        when(profileRepository.findByStaffAccount_Id(OTHER_DOCTOR_ID)).thenReturn(Optional.of(reassignedProfile));

        DoctorExaminationResponse response = service.open(TICKET_ID, OTHER_DOCTOR_ID.toString());

        assertThat(response.doctorName()).isEqualTo("Bác sĩ Trần Bình");
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
    }

    private DoctorExaminationRequest request() {
        return new DoctorExaminationRequest("Đau đầu", "Mạch ổn", "Đau đầu căng thẳng",
                "Theo dõi thêm", "Nghỉ ngơi", null, null, 0L,
                List.of(new PrescriptionLineRequest(null, "Paracetamol", "500 mg", 10, "Uống khi đau")));
    }

    private static void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
