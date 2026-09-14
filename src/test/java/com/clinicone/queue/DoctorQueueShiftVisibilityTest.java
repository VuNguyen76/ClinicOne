package com.clinicone.queue;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorQueueShiftVisibilityTest {

    private static final UUID DOCTOR_ID = UUID.fromString("c1e7aa0f-8dc2-4d3d-9d75-7f909e0bb1de");
    private static final UUID PATIENT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 6);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock AT_16_10 = Clock.fixed(Instant.parse("2026-08-06T09:10:00Z"), CLINIC_ZONE);
    private static final Clock AT_15_59 = Clock.fixed(Instant.parse("2026-08-06T08:59:00Z"), CLINIC_ZONE);

    private ClinicRoomRepository roomRepository;
    private QueueTicketRepository ticketRepository;
    private AppointmentRepository appointmentRepository;
    private DoctorProfileRepository doctorProfileRepository;
    private ExaminationSessionRepository examinationSessionRepository;
    private DoctorScheduleRepository scheduleRepository;
    private List<QueueTicket> doctorTickets;

    private ClinicRoom room;
    private PatientAccount patient;
    private DoctorProfile profile;

    @BeforeEach
    void setUp() {
        roomRepository = mock(ClinicRoomRepository.class);
        ticketRepository = mock(QueueTicketRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        doctorProfileRepository = mock(DoctorProfileRepository.class);
        examinationSessionRepository = mock(ExaminationSessionRepository.class);
        scheduleRepository = mock(DoctorScheduleRepository.class);
        doctorTickets = new ArrayList<>();

        room = ClinicRoom.create("NOI-01", "Phòng Nội tổng quát 01", "Nội tổng quát");
        patient = new PatientAccount("0912345678", "hash", "Nguyễn Văn A", AccountStatus.ACTIVE, false);
        setId(patient, PATIENT_ID);
        StaffAccount staff = StaffAccount.create("doctor-a", "hash", "BS. Nguyễn An", StaffRole.DOCTOR);
        setId(staff, DOCTOR_ID);
        profile = DoctorProfile.create(staff, "Nội tổng quát", room);
        setId(profile, UUID.randomUUID());

        when(roomRepository.findByCodeAndActiveTrue("NOI-01")).thenReturn(Optional.of(room));
        when(roomRepository.findByCodeAndActiveTrueForUpdate("NOI-01")).thenReturn(Optional.of(room));
        when(roomRepository.findByQrTokenAndActiveTrue(anyString())).thenReturn(Optional.empty());
        when(doctorProfileRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(profile));
        when(ticketRepository.findByAppointmentId(any(UUID.class))).thenReturn(Optional.empty());
        when(ticketRepository.findMaxQueueNumberByRoomCodeAndQueueDate("NOI-01", TODAY)).thenReturn(null);
        when(ticketRepository.save(any(QueueTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                "NOI-01", TODAY, DOCTOR_ID)).thenAnswer(inv -> new ArrayList<>(doctorTickets));
        when(ticketRepository.findByRoomCodeAndQueueDateAndRoutingDoctorStaffIdOrderByQueueNumberAsc(
                "NOI-01", TODAY, DOCTOR_ID)).thenReturn(List.of());
        when(examinationSessionRepository.findByAppointment_Id(any(UUID.class))).thenReturn(Optional.empty());
        when(examinationSessionRepository.save(any(ExaminationSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void bothQrAndReceptionCheckedInButDoctorSeesNoShiftWhenStoredTimeIsShifted() {
        DoctorSchedule shifted = DoctorSchedule.create(profile, DayOfWeek.THURSDAY,
                LocalTime.of(8, 0), LocalTime.of(9, 0), 60);
        when(scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                profile.getId(), DayOfWeek.THURSDAY)).thenReturn(List.of(shifted));
        QueueService service = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                doctorProfileRepository, examinationSessionRepository, null, scheduleRepository, null,
                AT_16_10);

        Appointment qrAppointment = appointment("CL-QR-1600");
        Appointment receptionAppointment = appointment("CL-RCP-1600");
        when(appointmentRepository.findByIdAndPatientId(qrAppointment.getId(), PATIENT_ID))
                .thenReturn(Optional.of(qrAppointment));
        when(appointmentRepository.findById(receptionAppointment.getId())).thenReturn(Optional.of(receptionAppointment));

        service.checkIn(PATIENT_ID.toString(), "NOI-01", qrAppointment.getId());
        service.checkInByStaff("NOI-01", receptionAppointment.getId(), "Bệnh nhân đến đúng giờ", "reception-1");

        ArgumentCaptor<QueueTicket> captor = ArgumentCaptor.forClass(QueueTicket.class);
        verify(ticketRepository, times(2)).save(captor.capture());
        doctorTickets.addAll(assignIds(captor.getAllValues()));

        assertEquals(AppointmentStatus.CHECKED_IN, qrAppointment.getStatus());
        assertEquals(AppointmentStatus.CHECKED_IN, receptionAppointment.getStatus());
        assertEquals(2, doctorTickets.size());

        DoctorQueueResponse response = service.doctorQueue(TODAY, DOCTOR_ID.toString());

        assertEquals("NONE", response.shiftStatus());
        assertEquals(List.of(), response.tickets());
    }

    @Test
    void bothFlowsVisibleToDoctorWhenStoredTimeIsCorrect() {
        DoctorSchedule correct = DoctorSchedule.create(profile, DayOfWeek.THURSDAY,
                LocalTime.of(16, 0), LocalTime.of(17, 0), 60);
        when(scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                profile.getId(), DayOfWeek.THURSDAY)).thenReturn(List.of(correct));
        QueueService service = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                doctorProfileRepository, examinationSessionRepository, null, scheduleRepository, null,
                AT_16_10);

        Appointment qrAppointment = appointment("CL-QR-CORRECT");
        Appointment receptionAppointment = appointment("CL-RCP-CORRECT");
        when(appointmentRepository.findByIdAndPatientId(qrAppointment.getId(), PATIENT_ID))
                .thenReturn(Optional.of(qrAppointment));
        when(appointmentRepository.findById(receptionAppointment.getId())).thenReturn(Optional.of(receptionAppointment));

        service.checkIn(PATIENT_ID.toString(), "NOI-01", qrAppointment.getId());
        service.checkInByStaff("NOI-01", receptionAppointment.getId(), "Bệnh nhân đến đúng giờ", "reception-1");

        ArgumentCaptor<QueueTicket> captor = ArgumentCaptor.forClass(QueueTicket.class);
        verify(ticketRepository, times(2)).save(captor.capture());
        doctorTickets.addAll(assignIds(captor.getAllValues()));

        DoctorQueueResponse response = service.doctorQueue(TODAY, DOCTOR_ID.toString());

        assertEquals("ACTIVE", response.shiftStatus());
        assertEquals(2, response.tickets().size());
        assertEquals(List.of("CL-QR-CORRECT", "CL-RCP-CORRECT"),
                response.tickets().stream().map(QueueTicketResponse::appointmentCode).toList());
    }

    @Test
    void doctorQueueStaysEmptyWhenCheckedBeforeShiftWindowEvenIfStorageIsCorrect() {
        DoctorSchedule correct = DoctorSchedule.create(profile, DayOfWeek.THURSDAY,
                LocalTime.of(16, 0), LocalTime.of(17, 0), 60);
        when(scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                profile.getId(), DayOfWeek.THURSDAY)).thenReturn(List.of(correct));
        QueueService service = new QueueService(roomRepository, ticketRepository, appointmentRepository,
                doctorProfileRepository, examinationSessionRepository, null, scheduleRepository, null,
                AT_15_59);

        Appointment qrAppointment = appointment("CL-QR-EARLY");
        when(appointmentRepository.findByIdAndPatientId(qrAppointment.getId(), PATIENT_ID))
                .thenReturn(Optional.of(qrAppointment));

        service.checkIn(PATIENT_ID.toString(), "NOI-01", qrAppointment.getId());

        ArgumentCaptor<QueueTicket> captor = ArgumentCaptor.forClass(QueueTicket.class);
        verify(ticketRepository).save(captor.capture());
        doctorTickets.addAll(assignIds(captor.getAllValues()));

        DoctorQueueResponse response = service.doctorQueue(TODAY, DOCTOR_ID.toString());

        assertEquals("NONE", response.shiftStatus());
        assertEquals(List.of(), response.tickets());
        verify(ticketRepository, never())
                .findByRoomCodeAndQueueDateAndAppointment_DoctorStaffIdOrderByQueueNumberAsc(
                        anyString(), any(LocalDate.class), any(UUID.class));
    }

    private Appointment appointment(String code) {
        Appointment appointment = Appointment.create(patient, DOCTOR_ID, code, "Nội tổng quát",
                "BS. Nguyễn An", TODAY, LocalTime.of(16, 0), "Đau đầu");
        setId(appointment, UUID.randomUUID());
        return appointment;
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

    private static List<QueueTicket> assignIds(List<QueueTicket> tickets) {
        for (QueueTicket ticket : tickets) {
            if (ticket.getId() == null) {
                setId(ticket, UUID.randomUUID());
            }
        }
        return tickets;
    }
}