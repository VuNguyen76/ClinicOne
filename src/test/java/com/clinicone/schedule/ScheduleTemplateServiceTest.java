package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleTemplateServiceTest {
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    private ClinicServiceRepository serviceRepository;
    private DoctorProfileRepository doctorRepository;
    private ClinicRoomRepository roomRepository;
    private WorkScheduleTemplateRepository templateRepository;
    private GeneratedClinicSlotRepository slotRepository;
    private ScheduleTemplateService service;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ClinicServiceRepository.class);
        doctorRepository = mock(DoctorProfileRepository.class);
        roomRepository = mock(ClinicRoomRepository.class);
        templateRepository = mock(WorkScheduleTemplateRepository.class);
        slotRepository = mock(GeneratedClinicSlotRepository.class);
        service = new ScheduleTemplateService(templateRepository, slotRepository, serviceRepository,
                doctorRepository, roomRepository);
    }

    @Test
    void createsTemplateAndGeneratesSlotsSkippingBreaksAndExceptions() {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom room = mock(ClinicRoom.class);
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(clinicService));
        when(doctorRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(clinicService.isActive()).thenReturn(true);
        when(clinicService.getId()).thenReturn(SERVICE_ID);
        when(clinicService.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(clinicService.getName()).thenReturn("Khám tổng quát cơ bản");
        when(clinicService.getVisitType()).thenReturn("Khám thường");
        when(clinicService.getDurationMinutes()).thenReturn(30);
        when(doctor.getId()).thenReturn(UUID.randomUUID());
        when(doctor.getStaffAccount()).thenReturn(staff);
        when(staff.getId()).thenReturn(DOCTOR_ID);
        when(doctor.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(doctor.isActive()).thenReturn(true);
        when(doctor.getRoom()).thenReturn(room);
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.isActive()).thenReturn(true);
        when(room.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(room.getCode()).thenReturn("TQ-01");
        when(templateRepository.save(any(WorkScheduleTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(slotRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(
                any(), any(), any(), any())).thenReturn(List.of());
        when(slotRepository.findByRoomIdAndAppointmentDateBetweenAndStatus(
                any(), any(), any(), any())).thenReturn(List.of());
        when(slotRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleTemplateResponse response = service.create(new CreateScheduleTemplateRequest(
                SERVICE_ID, DOCTOR_ID, ROOM_ID,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 30,
                List.of(new ScheduleBreakRequest(LocalTime.of(9, 0), LocalTime.of(9, 30))),
                Set.of(LocalDate.of(2026, 8, 11))));

        assertEquals(3, response.generatedSlotCount());
        verify(slotRepository).saveAll(any());
    }

    @Test
    void rejectsTemplateLongerThan366DaysBeforeWriting() {
        AuthException exception = assertThrows(AuthException.class, () -> service.create(new CreateScheduleTemplateRequest(
                SERVICE_ID, DOCTOR_ID, ROOM_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2),
                Set.of(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(10, 0), 30,
                List.of(), Set.of())));

        assertEquals("SCHEDULE_DATE_RANGE_INVALID", exception.getCode());
        verify(templateRepository, never()).save(any(WorkScheduleTemplate.class));
    }

    @Test
    void rejectsRoomThatDoesNotMatchDoctorAssignment() {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom assignedRoom = mock(ClinicRoom.class);
        ClinicRoom requestedRoom = mock(ClinicRoom.class);
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(clinicService));
        when(doctorRepository.findByStaffAccount_Id(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(requestedRoom));
        when(clinicService.isActive()).thenReturn(true);
        when(clinicService.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(clinicService.getDurationMinutes()).thenReturn(30);
        when(doctor.isActive()).thenReturn(true);
        when(doctor.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(doctor.getStaffAccount()).thenReturn(staff);
        when(staff.getId()).thenReturn(DOCTOR_ID);
        when(doctor.getRoom()).thenReturn(assignedRoom);
        when(assignedRoom.getId()).thenReturn(UUID.randomUUID());
        when(requestedRoom.isActive()).thenReturn(true);
        when(requestedRoom.getSpecialty()).thenReturn("Khám Tổng Quát");

        AuthException exception = assertThrows(AuthException.class, () -> service.create(new CreateScheduleTemplateRequest(
                SERVICE_ID, DOCTOR_ID, ROOM_ID,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), Set.of(DayOfWeek.MONDAY),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 30, List.of(), Set.of())));

        assertEquals("SCHEDULE_ROOM_MISMATCH", exception.getCode());
        verify(templateRepository, never()).save(any(WorkScheduleTemplate.class));
    }

    @Test
    void regeneratingTheSameTemplateDoesNotCreateDuplicateSlots() throws Exception {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom room = mock(ClinicRoom.class);
        UUID templateId = UUID.randomUUID();
        WorkScheduleTemplate storedTemplate = template(clinicService, doctor, room, staff, templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(storedTemplate));
        when(slotRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(slotRepository.findByRoomIdAndAppointmentDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(List.of());
        GeneratedClinicSlot existing = mock(GeneratedClinicSlot.class);
        when(existing.getAppointmentDate()).thenReturn(LocalDate.of(2026, 8, 10));
        when(existing.getStartTime()).thenReturn(LocalTime.of(8, 0));
        when(slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(templateId))
                .thenReturn(List.of(existing));

        ScheduleTemplateResponse response = service.regenerate(templateId);

        assertEquals(1, response.generatedSlotCount());
        verify(slotRepository, never()).saveAll(any());
    }

    private WorkScheduleTemplate template(ClinicService clinicService, DoctorProfile doctor, ClinicRoom room,
                                           StaffAccount staff, UUID templateId) throws Exception {
        when(clinicService.getId()).thenReturn(SERVICE_ID);
        when(clinicService.getName()).thenReturn("Khám tổng quát cơ bản");
        when(clinicService.getSpecialty()).thenReturn("Khám Tổng Quát");
        when(clinicService.getVisitType()).thenReturn("Khám thường");
        when(doctor.getStaffAccount()).thenReturn(staff);
        when(staff.getId()).thenReturn(DOCTOR_ID);
        when(staff.getFullName()).thenReturn("Bác sĩ Nguyễn An");
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getCode()).thenReturn("TQ-01");
        WorkScheduleTemplate template = WorkScheduleTemplate.create(clinicService, doctor, room,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), LocalTime.of(8, 0), LocalTime.of(8, 30),
                30, Set.of(DayOfWeek.MONDAY), List.of(), Set.of());
        Field field = WorkScheduleTemplate.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(template, templateId);
        return template;
    }
}
