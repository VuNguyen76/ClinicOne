package com.clinicone.reception;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentResponse;
import com.clinicone.appointment.AppointmentService;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.appointment.CreateAppointmentRequest;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.QueueService;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.queue.QueueTicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReceptionServiceTest {
    private static final UUID PATIENT_ID = UUID.fromString("4a5e0c84-8b19-4ec4-a2ce-2280bd7dbf10");
    private static final UUID APPOINTMENT_ID = UUID.fromString("ad9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final UUID DOCTOR_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 7);

    private PatientAccountRepository patientAccountRepository;
    private AppointmentRepository appointmentRepository;
    private DoctorProfileRepository doctorProfileRepository;
    private QueueTicketRepository ticketRepository;
    private QueueService queueService;
    private AppointmentService appointmentService;
    private PatientProfileRepository patientProfileRepository;
    private ReceptionService service;

    @BeforeEach
    void setUp() {
        patientAccountRepository = mock(PatientAccountRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        doctorProfileRepository = mock(DoctorProfileRepository.class);
        ticketRepository = mock(QueueTicketRepository.class);
        queueService = mock(QueueService.class);
        appointmentService = mock(AppointmentService.class);
        patientProfileRepository = mock(PatientProfileRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneOffset.UTC);
        service = new ReceptionService(appointmentRepository, doctorProfileRepository, ticketRepository,
                queueService, clock, patientAccountRepository, appointmentService, patientProfileRepository);
    }

    @Test
    void createsAppointmentAndQueueTicketForExistingPatient() {
        PatientAccount patient = mock(PatientAccount.class);
        when(patient.getId()).thenReturn(PATIENT_ID);
        when(patient.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(patient.getFullName()).thenReturn("Nguyễn Thanh Vũ");
        when(patient.getPhone()).thenReturn("0912345678");
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.of(patient));

        StaffAccount staff = mock(StaffAccount.class);
        when(staff.getFullName()).thenReturn("BS. Nguyễn An");
        ClinicRoom room = ClinicRoom.create("NOI-01", "Phòng Nội 01", "Nội tổng quát");
        DoctorProfile doctor = mock(DoctorProfile.class);
        when(doctor.isActive()).thenReturn(true);
        when(doctor.getSpecialty()).thenReturn("Nội tổng quát");
        when(doctor.getStaffAccount()).thenReturn(staff);
        when(doctor.getRoom()).thenReturn(room);
        when(doctorProfileRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));

        AppointmentResponse created = new AppointmentResponse(APPOINTMENT_ID, "CL-20260807-1234",
                "Nội tổng quát", "BS. Nguyễn An", TODAY, LocalTime.of(9, 0), "Đau đầu từ sáng",
                "BOOKED", "Đã đặt", null, null, DOCTOR_ID);
        when(appointmentService.create(anyString(), any())).thenReturn(created);
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(APPOINTMENT_ID);
        when(appointment.getAppointmentCode()).thenReturn("CL-20260807-1234");
        when(appointment.getAppointmentDate()).thenReturn(TODAY);
        when(appointment.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(appointment.getSpecialty()).thenReturn("Nội tổng quát");
        when(appointment.getDoctorName()).thenReturn("BS. Nguyễn An");
        when(appointment.getDoctorStaffId()).thenReturn(DOCTOR_ID);
        when(appointment.getPatient()).thenReturn(patient);
        when(appointment.getStatus()).thenReturn(com.clinicone.appointment.AppointmentStatus.CHECKED_IN);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(doctorProfileRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        QueueTicketResponse ticket = new QueueTicketResponse(UUID.randomUUID(), 8, "NOI-01", "Phòng Nội 01",
                TODAY, LocalTime.of(9, 0), "WAITING", "Đang chờ", "CL-20260807-1234", "Nội tổng quát", "BS. Nguyễn An");
        when(queueService.checkInByStaff("NOI-01", APPOINTMENT_ID, "Người bệnh đến quầy không có lịch"))
                .thenReturn(ticket);

        ReceptionAppointmentResponse response = service.createWalkIn(new ReceptionWalkInRequest(
                "0912345678", null, DOCTOR_ID, TODAY, LocalTime.of(9, 0), "Đau đầu từ sáng",
                "Người bệnh đến quầy không có lịch"));

        assertThat(response.queueNumber()).isEqualTo(8);
        assertThat(response.patientName()).isEqualTo("Nguyễn Thanh Vũ");
        verify(queueService).checkInByStaff("NOI-01", APPOINTMENT_ID, "Người bệnh đến quầy không có lịch");
    }

    @Test
    void rejectsWalkInWhenPhoneHasNoPatientAccount() {
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWalkIn(new ReceptionWalkInRequest(
                "0912345678", null, DOCTOR_ID, TODAY, LocalTime.of(9, 0), "Đau đầu từ sáng",
                "Người bệnh đến quầy không có lịch")))
                .hasMessageContaining("Chưa có tài khoản");
    }

    @Test
    void createsWalkInForTemporaryProfileWhenPhoneHasNoAccount() {
        UUID profileId = UUID.randomUUID();
        PatientProfile temporaryProfile = PatientProfile.createTemporary(
                "Nguyen Van Tam", LocalDate.of(1990, 1, 1), "Nam", "0912345678", null, null, null, null);
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(patientProfileRepository.findById(profileId)).thenReturn(Optional.of(temporaryProfile));

        StaffAccount staff = mock(StaffAccount.class);
        when(staff.getFullName()).thenReturn("BS. Nguyen An");
        ClinicRoom room = ClinicRoom.create("NOI-01", "Phong Noi 01", "Noi tong quat");
        DoctorProfile doctor = mock(DoctorProfile.class);
        when(doctor.isActive()).thenReturn(true);
        when(doctor.getSpecialty()).thenReturn("Noi tong quat");
        when(doctor.getStaffAccount()).thenReturn(staff);
        when(doctor.getRoom()).thenReturn(room);
        when(doctorProfileRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));

        AppointmentResponse created = new AppointmentResponse(APPOINTMENT_ID, "CL-20260807-9999",
                "Noi tong quat", "BS. Nguyen An", TODAY, LocalTime.of(9, 0), "Dau dau",
                "BOOKED", "Da dat", profileId, "Nguyen Van Tam", DOCTOR_ID);
        when(appointmentService.createTemporary(any(PatientProfile.class), any())).thenReturn(created);
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(APPOINTMENT_ID);
        when(appointment.getAppointmentCode()).thenReturn("CL-20260807-9999");
        when(appointment.getAppointmentDate()).thenReturn(TODAY);
        when(appointment.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(appointment.getSpecialty()).thenReturn("Noi tong quat");
        when(appointment.getDoctorName()).thenReturn("BS. Nguyen An");
        when(appointment.getDoctorStaffId()).thenReturn(DOCTOR_ID);
        when(appointment.getPatient()).thenReturn(null);
        when(appointment.getPatientProfile()).thenReturn(temporaryProfile);
        when(appointment.getStatus()).thenReturn(AppointmentStatus.CHECKED_IN);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(doctorProfileRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        QueueTicketResponse ticket = new QueueTicketResponse(UUID.randomUUID(), 9, "NOI-01", "Phong Noi 01",
                TODAY, LocalTime.of(9, 0), "WAITING", "Dang cho", "CL-20260807-9999", "Noi tong quat",
                "BS. Nguyen An");
        when(queueService.checkInByStaff("NOI-01", APPOINTMENT_ID, "Khach chua co tai khoan"))
                .thenReturn(ticket);

        ReceptionAppointmentResponse response = service.createWalkIn(new ReceptionWalkInRequest(
                "0912345678", profileId, DOCTOR_ID, TODAY, LocalTime.of(9, 0), "Dau dau",
                "Khach chua co tai khoan"));

        assertThat(response.patientName()).isEqualTo("Nguyen Van Tam");
        assertThat(response.queueNumber()).isEqualTo(9);
        verify(appointmentService).createTemporary(temporaryProfile, new CreateAppointmentRequest(
                "Noi tong quat", "BS. Nguyen An", TODAY, LocalTime.of(9, 0), "Dau dau", profileId, DOCTOR_ID));
        verify(queueService).checkInByStaff("NOI-01", APPOINTMENT_ID, "Khach chua co tai khoan");
    }

    @Test
    void rejectsTemporaryWalkInWhenVerificationReasonIsTooShort() {
        UUID profileId = UUID.randomUUID();
        PatientProfile temporaryProfile = PatientProfile.createTemporary(
                "Nguyen Van Tam", LocalDate.of(1990, 1, 1), "Nam", "0912345678", null, null, null, null);
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(patientProfileRepository.findById(profileId)).thenReturn(Optional.of(temporaryProfile));

        assertThatThrownBy(() -> service.createWalkIn(new ReceptionWalkInRequest(
                "0912345678", profileId, DOCTOR_ID, TODAY, LocalTime.of(9, 0), "Dau dau", "Khong ro")))
                .isInstanceOf(com.clinicone.auth.AuthException.class)
                .hasMessageContaining("Lý do không thể xác thực")
                .extracting("code").isEqualTo("TEMPORARY_EXCEPTION_REASON_INVALID");

        verifyNoInteractions(doctorProfileRepository, appointmentService, queueService);
    }

    @Test
    void createsAReusableTemporaryProfileAtReception() {
        when(patientAccountRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(patientProfileRepository.findFirstByTemporaryProfileTrueAndOwnerIsNullAndPhone("0912345678"))
                .thenReturn(Optional.empty());
        when(patientProfileRepository.save(any(PatientProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceptionPatientProfileResponse response = service.createTemporaryProfile(
                new ReceptionTemporaryProfileRequest("0912345678", "Nguyen Van Tam",
                        LocalDate.of(1990, 1, 1), "Nam", null, "Viet Nam", "Kinh", "12 Nguyen Trai"));

        assertThat(response.fullName()).isEqualTo("Nguyen Van Tam");
        assertThat(response.accountStatus()).isNull();
        assertThat(response.mustChangePassword()).isFalse();
        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    @Test
    void receptionistCanRecordCheckedInPatientLeftBeforeExam() {
        UUID ticketId = UUID.randomUUID();
        PatientAccount patient = mock(PatientAccount.class);
        when(patient.getFullName()).thenReturn("Nguyễn Thanh Vũ");
        when(patient.getPhone()).thenReturn("0912345678");
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(APPOINTMENT_ID);
        when(appointment.getAppointmentCode()).thenReturn("CL-20260807-1234");
        when(appointment.getAppointmentDate()).thenReturn(TODAY);
        when(appointment.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(appointment.getSpecialty()).thenReturn("Nội tổng quát");
        when(appointment.getDoctorName()).thenReturn("BS. Nguyễn An");
        when(appointment.getStatus()).thenReturn(AppointmentStatus.CHECKED_IN);
        when(appointment.getPatient()).thenReturn(patient);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        QueueTicket ticket = mock(QueueTicket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketRepository.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(ticket));
        QueueTicketResponse updated = new QueueTicketResponse(ticketId, 8, "NOI-01", "Phòng Nội 01",
                TODAY, LocalTime.of(9, 0), "LEFT_BEFORE_EXAM", "Rời trước khám", "CL-20260807-1234",
                "Nội tổng quát", "BS. Nguyễn An");
        when(queueService.leaveBeforeExam(ticketId, "Người bệnh bận việc đột xuất")).thenReturn(updated);

        ReceptionAppointmentResponse response = service.leaveBeforeExam(APPOINTMENT_ID,
                "Người bệnh bận việc đột xuất");

        assertThat(response.queueStatus()).isEqualTo("LEFT_BEFORE_EXAM");
        assertThat(response.queueNumber()).isEqualTo(8);
        verify(queueService).leaveBeforeExam(ticketId, "Người bệnh bận việc đột xuất");
    }

    @Test
    void rejectsCheckInBeforePatientCompletesPasswordActivation() {
        PatientAccount patient = mock(PatientAccount.class);
        when(patient.isMustChangePassword()).thenReturn(true);
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(APPOINTMENT_ID);
        when(appointment.getPatient()).thenReturn(patient);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.checkIn(APPOINTMENT_ID,
                new ReceptionCheckInRequest("NOI-01", "Người bệnh chưa kích hoạt tài khoản")))
                .hasMessageContaining("đổi mật khẩu");
        verifyNoInteractions(queueService);
    }

    @Test
    void rejectsCheckInForLockedPatientAccount() {
        PatientAccount patient = mock(PatientAccount.class);
        when(patient.getStatus()).thenReturn(AccountStatus.LOCKED);
        Appointment appointment = mock(Appointment.class);
        when(appointment.getPatient()).thenReturn(patient);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.checkIn(APPOINTMENT_ID,
                new ReceptionCheckInRequest("NOI-01", "locked account")))
                .isInstanceOf(com.clinicone.auth.AuthException.class);
        verifyNoInteractions(queueService);
    }
}
