package com.clinicone.appointment;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("7d9e3fb4-1045-4ca4-86d2-7d1fca4c1a13");

    private PatientAccountRepository accountRepository;
    private AppointmentRepository appointmentRepository;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PatientAccountRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        service = new AppointmentService(accountRepository, appointmentRepository);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAppointmentForAuthenticatedPatient() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED)).thenReturn(Optional.empty());

        AppointmentResponse response = service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30),
                "Đau đầu kéo dài"));

        assertEquals("Nội khoa", response.specialty());
        assertEquals("BS. Nguyễn An", response.doctorName());
        assertEquals("Đã đặt", response.statusLabel());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void rejectsDuplicateAppointmentForSamePatientAndTime() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), AppointmentStatus.BOOKED))
                .thenReturn(Optional.of(Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                        LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu")));

        AuthException exception = assertThrows(AuthException.class, () -> service.create(ACCOUNT_ID.toString(), new CreateAppointmentRequest(
                "Nội khoa", "BS. Nguyễn An", LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu")));

        assertEquals(409, exception.getStatus().value());
        assertEquals("APPOINTMENT_DUPLICATE", exception.getCode());
    }

    @Test
    void listsOnlyAppointmentsBelongingToAuthenticatedPatient() {
        when(appointmentRepository.findByPatientIdOrderByAppointmentDateAscStartTimeAsc(ACCOUNT_ID))
                .thenReturn(List.of());

        assertEquals(List.of(), service.list(ACCOUNT_ID.toString()));
        verify(appointmentRepository).findByPatientIdOrderByAppointmentDateAscStartTimeAsc(ACCOUNT_ID);
    }

    @Test
    void returnsAppointmentDetailOnlyForItsPatient() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        setId(appointment, appointmentId);
        when(appointmentRepository.findByIdAndPatientId(appointmentId, ACCOUNT_ID)).thenReturn(Optional.of(appointment));

        assertEquals(appointmentId, service.get(ACCOUNT_ID.toString(), appointmentId.toString()).id());
    }

    @Test
    void cancelsBookedAppointment() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));

        service.cancel(ACCOUNT_ID.toString(), UUID.randomUUID().toString(), new CancelAppointmentRequest("Bận việc"));

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
    }

    @Test
    void reschedulesBookedAppointmentWhenNewSlotIsFree() {
        PatientAccount account = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
        setId(account, ACCOUNT_ID);
        Appointment appointment = Appointment.existing(account, "CL-20260810-AB12", "Nội khoa", "BS. Nguyễn An",
                LocalDate.of(2026, 8, 10), LocalTime.of(8, 30), "Đau đầu");
        when(appointmentRepository.findByIdAndPatientId(any(), eq(ACCOUNT_ID))).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                ACCOUNT_ID, LocalDate.of(2026, 8, 11), LocalTime.of(10, 0), AppointmentStatus.BOOKED)).thenReturn(Optional.empty());

        AppointmentResponse response = service.reschedule(ACCOUNT_ID.toString(), UUID.randomUUID().toString(),
                new RescheduleAppointmentRequest(LocalDate.of(2026, 8, 11), LocalTime.of(10, 0)));

        assertEquals(LocalDate.of(2026, 8, 11), response.appointmentDate());
        assertEquals(LocalTime.of(10, 0), response.startTime());
    }

    private static void setId(PatientAccount account, UUID id) {
        try {
            var field = PatientAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setId(Appointment appointment, UUID id) {
        try {
            var field = Appointment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(appointment, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
