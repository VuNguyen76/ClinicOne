package com.clinicone.schedule;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private DoctorScheduleRepository doctorScheduleRepository;
    private ScheduleTemplateService service;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ClinicServiceRepository.class);
        doctorRepository = mock(DoctorProfileRepository.class);
        roomRepository = mock(ClinicRoomRepository.class);
        templateRepository = mock(WorkScheduleTemplateRepository.class);
        slotRepository = mock(GeneratedClinicSlotRepository.class);
        doctorScheduleRepository = mock(DoctorScheduleRepository.class);
        service = new ScheduleTemplateService(templateRepository, slotRepository, serviceRepository,
                doctorRepository, roomRepository, doctorScheduleRepository);
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
    void generatedSlotCountIsBoundedWhenDayEndCrossesMidnight() {
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
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                Set.of(DayOfWeek.MONDAY),
                LocalTime.of(21, 0), LocalTime.of(23, 30), 30,
                List.of(), Set.of()));

        assertEquals(5, response.generatedSlotCount());
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

    @Test
    void deletingSpecificWeekdayRemovesOnlyThatWeekdayFromMultiDayTemplate() {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        ClinicRoom room = mock(ClinicRoom.class);
        UUID templateId = UUID.randomUUID();
        WorkScheduleTemplate storedTemplate = WorkScheduleTemplate.create(
                clinicService, doctor, room,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 16),
                LocalTime.of(8, 0), LocalTime.of(17, 0), 30,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), List.of(), Set.of());
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(storedTemplate));

        UUID slotMondayId = UUID.randomUUID();
        GeneratedClinicSlot slotMonday = mock(GeneratedClinicSlot.class);
        when(slotMonday.getId()).thenReturn(slotMondayId);
        when(slotMonday.getStatus()).thenReturn(GeneratedSlotStatus.OPEN);
        when(slotMonday.getAppointmentDate()).thenReturn(LocalDate.of(2026, 8, 10)); // Monday

        when(slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(templateId))
                .thenReturn(List.of(slotMonday));

        service.delete(templateId, "MONDAY");

        assertEquals(Set.of(DayOfWeek.TUESDAY), storedTemplate.getWeekdays());
        verify(templateRepository).save(storedTemplate);
        verify(slotRepository).deleteAllByIdIn(List.of(slotMondayId));
    }

    @Test
    void regenerateWithChangedDayEndDeactivatesStaleScheduleAndInsertsNewOnce() throws Exception {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom room = mock(ClinicRoom.class);
        UUID templateId = UUID.randomUUID();
        // Template changed 08:00-17:00 -> 08:00-21:30 (e.g. legacy 23:00 -> new 21:30)
        WorkScheduleTemplate storedTemplate = scheduleTemplate(clinicService, doctor, room, staff, templateId,
                LocalTime.of(8, 0), LocalTime.of(21, 30), Set.of(DayOfWeek.MONDAY));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(storedTemplate));
        stubEmptySlots(templateId);

        var realProfile = realDoctorProfile();
        DoctorSchedule stale = DoctorSchedule.create(realProfile, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0), 30);
        when(doctorScheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(any(), eq(DayOfWeek.MONDAY)))
                .thenReturn(new java.util.ArrayList<>(List.of(stale)));
        when(doctorScheduleRepository.save(any(DoctorSchedule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.regenerate(templateId);

        assertEquals(false, stale.isActive()); // stale 08:00-17:00 deactivated
        var captor = org.mockito.ArgumentCaptor.forClass(DoctorSchedule.class);
        verify(doctorScheduleRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        var inserted = captor.getAllValues().stream()
                .filter(s -> s != stale).toList();
        assertEquals(1, inserted.size()); // exactly one new row, no duplicate
        assertEquals(LocalTime.of(8, 0), inserted.get(0).getStartTime());
        assertEquals(LocalTime.of(21, 30), inserted.get(0).getEndTime());
    }

    @Test
    void regenerateKeepsGapSchedulesUntouched() throws Exception {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom room = mock(ClinicRoom.class);
        UUID templateId = UUID.randomUUID();
        WorkScheduleTemplate storedTemplate = scheduleTemplate(clinicService, doctor, room, staff, templateId,
                LocalTime.of(8, 0), LocalTime.of(12, 0), Set.of(DayOfWeek.MONDAY));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(storedTemplate));
        stubEmptySlots(templateId);

        var realProfile = realDoctorProfile();
        DoctorSchedule morning = DoctorSchedule.create(realProfile, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        DoctorSchedule afternoon = DoctorSchedule.create(realProfile, DayOfWeek.MONDAY,
                LocalTime.of(13, 0), LocalTime.of(17, 0), 30);
        when(doctorScheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(any(), eq(DayOfWeek.MONDAY)))
                .thenReturn(new java.util.ArrayList<>(List.of(morning, afternoon)));

        service.regenerate(templateId);

        assertEquals(true, morning.isActive()); // gap preserved
        assertEquals(true, afternoon.isActive()); // gap preserved
        verify(doctorScheduleRepository, never()).save(any(DoctorSchedule.class)); // no deactivate, no insert
    }

    @Test
    void regenerateWithThreeOverlappingKeepsNewestTimeOnly() throws Exception {
        ClinicService clinicService = mock(ClinicService.class);
        DoctorProfile doctor = mock(DoctorProfile.class);
        StaffAccount staff = mock(StaffAccount.class);
        ClinicRoom room = mock(ClinicRoom.class);
        UUID templateId = UUID.randomUUID();
        // Newest template time 08:00-17:00 wins over 08:00-21:00 and 20:00-23:00
        WorkScheduleTemplate storedTemplate = scheduleTemplate(clinicService, doctor, room, staff, templateId,
                LocalTime.of(8, 0), LocalTime.of(17, 0), Set.of(DayOfWeek.MONDAY));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(storedTemplate));
        stubEmptySlots(templateId);

        var realProfile = realDoctorProfile();
        DoctorSchedule newest = DoctorSchedule.create(realProfile, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0), 30);
        DoctorSchedule old1 = DoctorSchedule.create(realProfile, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(21, 0), 30);
        DoctorSchedule old2 = DoctorSchedule.create(realProfile, DayOfWeek.MONDAY,
                LocalTime.of(16, 0), LocalTime.of(23, 0), 30);
        when(doctorScheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(any(), eq(DayOfWeek.MONDAY)))
                .thenReturn(new java.util.ArrayList<>(List.of(newest, old1, old2)));

        service.regenerate(templateId);

        assertEquals(true, newest.isActive());
        assertEquals(false, old1.isActive());
        assertEquals(false, old2.isActive());
        verify(doctorScheduleRepository, never()).save(argThat((DoctorSchedule s)
                -> s != newest && s != old1 && s != old2));
    }

    private void stubEmptySlots(UUID templateId) {
        when(slotRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(slotRepository.findByRoomIdAndAppointmentDateBetweenAndStatus(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(templateId))
                .thenReturn(List.of());
        when(slotRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private WorkScheduleTemplate scheduleTemplate(ClinicService clinicService, DoctorProfile doctor, ClinicRoom room,
                                                  StaffAccount staff, UUID templateId,
                                                  LocalTime dayStart, LocalTime dayEnd,
                                                  Set<DayOfWeek> weekdays) throws Exception {
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
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), dayStart, dayEnd,
                30, weekdays, List.of(), Set.of());
        Field field = WorkScheduleTemplate.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(template, templateId);
        return template;
    }

    private com.clinicone.doctor.DoctorProfile realDoctorProfile() throws Exception {
        var realRoom = com.clinicone.queue.ClinicRoom.create("TQ-01", "Phong TQ 01", "Kham Tong Quat");
        var realStaff = com.clinicone.auth.StaffAccount.create("doctor-tpl", "hash", "BS Tpl",
                com.clinicone.auth.StaffRole.DOCTOR);
        var idField = realStaff.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(realStaff, DOCTOR_ID);
        var profile = com.clinicone.doctor.DoctorProfile.create(realStaff, "Kham Tong Quat", realRoom);
        var pField = profile.getClass().getDeclaredField("id");
        pField.setAccessible(true);
        pField.set(profile, UUID.randomUUID());
        return profile;
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
