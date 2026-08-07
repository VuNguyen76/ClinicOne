package com.clinicone.reception;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentResponse;
import com.clinicone.appointment.AppointmentService;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.QueueService;
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
    private ReceptionService service;

    @BeforeEach
    void setUp() {
        patientAccountRepository = mock(PatientAccountRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        doctorProfileRepository = mock(DoctorProfileRepository.class);
        ticketRepository = mock(QueueTicketRepository.class);
        queueService = mock(QueueService.class);
        appointmentService = mock(AppointmentService.class);
        PatientProfileRepository patientProfileRepository = mock(PatientProfileRepository.class);
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
                .hasMessageContaining("Chưa tìm thấy tài khoản");
    }
}
