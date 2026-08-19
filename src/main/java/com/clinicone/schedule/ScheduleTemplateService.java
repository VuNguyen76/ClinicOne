package com.clinicone.schedule;

import lombok.RequiredArgsConstructor;

import com.clinicone.auth.AuthException;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScheduleTemplateService {
    private final WorkScheduleTemplateRepository templateRepository;
    private final GeneratedClinicSlotRepository slotRepository;
    private final ClinicServiceRepository clinicServiceRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ClinicRoomRepository roomRepository;

    @Transactional(readOnly = true)
    public List<ScheduleTemplateResponse> list() {
        return templateRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScheduleTemplateResponse create(CreateScheduleTemplateRequest request) {
        ValidatedInput input = validateAndResolve(request);
        WorkScheduleTemplate template = WorkScheduleTemplate.create(input.clinicService(), input.doctor(), input.room(),
                request.startDate(), request.endDate(), request.dayStart(), request.dayEnd(), request.durationMinutes(),
                request.weekdays(), toBreaks(request.breaks()), safeDates(request.exceptionDates()));
        WorkScheduleTemplate saved = templateRepository.save(template);
        List<GeneratedClinicSlot> generated = generate(saved, false);
        return toResponse(saved, generated.size());
    }

    @Transactional
    public ScheduleTemplateResponse regenerate(UUID templateId) {
        WorkScheduleTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SCHEDULE_TEMPLATE_NOT_FOUND",
                        "Không tìm thấy lịch làm việc."));
        List<GeneratedClinicSlot> generated = generate(template, true);
        return toResponse(template, generated.size());
    }

    @Transactional
    public void delete(UUID templateId) {
        WorkScheduleTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SCHEDULE_TEMPLATE_NOT_FOUND",
                        "Không tìm thấy lịch làm việc."));
        template.setActive(false);
        templateRepository.save(template);
        List<GeneratedClinicSlot> slots = slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(templateId);
        List<GeneratedClinicSlot> unbooked = slots.stream()
                .filter(s -> s.getStatus() == GeneratedSlotStatus.OPEN)
                .toList();
        if (!unbooked.isEmpty()) {
            slotRepository.deleteAll(unbooked);
        }
    }

    private List<GeneratedClinicSlot> generate(WorkScheduleTemplate template, boolean idempotent) {
        List<SlotSeed> seeds = buildSeeds(template);
        checkConflicts(template, seeds);
        Set<String> existingKeys = idempotent
                ? new HashSet<>(slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(template.getId())
                .stream().map(this::key).toList())
                : Set.of();
        List<GeneratedClinicSlot> toCreate = seeds.stream()
                .filter(seed -> !existingKeys.contains(seed.key()))
                .map(seed -> GeneratedClinicSlot.create(template, seed.date(), seed.start(), seed.end()))
                .toList();
        if (!toCreate.isEmpty()) {
            slotRepository.saveAll(toCreate);
        }
        if (template.getId() == null) {
            return toCreate;
        }
        return slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(template.getId());
    }

    private List<SlotSeed> buildSeeds(WorkScheduleTemplate template) {
        List<SlotSeed> result = new ArrayList<>();
        for (LocalDate date = template.getStartDate(); !date.isAfter(template.getEndDate()); date = date.plusDays(1)) {
            if (!template.getWeekdays().contains(date.getDayOfWeek())
                    || template.getExceptionDates().contains(date)) {
                continue;
            }
            LocalTime start = template.getDayStart();
            while (!start.plusMinutes(template.getDurationMinutes()).isAfter(template.getDayEnd())) {
                LocalTime end = start.plusMinutes(template.getDurationMinutes());
                LocalTime slotStart = start;
                LocalTime slotEnd = end;
                ScheduleBreak overlappingBreak = template.getBreaks().stream()
                        .filter(item -> slotStart.isBefore(item.getEndTime()) && item.getStartTime().isBefore(slotEnd))
                        .findFirst().orElse(null);
                if (overlappingBreak != null) {
                    start = overlappingBreak.getEndTime();
                    continue;
                }
                result.add(new SlotSeed(date, start, end));
                start = end;
            }
        }
        return result;
    }

    private void checkConflicts(WorkScheduleTemplate template, List<SlotSeed> seeds) {
        List<GeneratedClinicSlot> doctorSlots = slotRepository.findByDoctorStaffIdAndAppointmentDateBetweenAndStatus(
                template.getDoctorProfile().getStaffAccount().getId(), template.getStartDate(), template.getEndDate(),
                GeneratedSlotStatus.OPEN);
        List<GeneratedClinicSlot> roomSlots = slotRepository.findByRoomIdAndAppointmentDateBetweenAndStatus(
                template.getRoom().getId(), template.getStartDate(), template.getEndDate(), GeneratedSlotStatus.OPEN);
        UUID currentTemplateId = template.getId();
        List<String> conflicts = new ArrayList<>();
        for (SlotSeed seed : seeds) {
            if (overlapsExisting(seed, doctorSlots, currentTemplateId)
                    || overlapsExisting(seed, roomSlots, currentTemplateId)) {
                conflicts.add(seed.date() + " " + seed.start());
                if (conflicts.size() == 100) break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw conflict("SCHEDULE_CONFLICT", "Khung giờ bị trùng: " + String.join(", ", conflicts));
        }
    }

    private boolean overlapsExisting(SlotSeed seed, List<GeneratedClinicSlot> existing, UUID currentTemplateId) {
        return existing.stream()
                .filter(slot -> currentTemplateId == null || !currentTemplateId.equals(slot.getTemplate().getId()))
                .anyMatch(slot -> slot.getAppointmentDate().equals(seed.date())
                        && seed.start().isBefore(slot.getEndTime())
                        && slot.getStartTime().isBefore(seed.end()));
    }

    private ValidatedInput validateAndResolve(CreateScheduleTemplateRequest request) {
        if (request == null) throw badRequest("SCHEDULE_REQUEST_REQUIRED", "Mẫu lịch làm việc không được để trống.");
        if (request.startDate() == null || request.endDate() == null || request.startDate().isAfter(request.endDate())
                || ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 > 366) {
            throw badRequest("SCHEDULE_DATE_RANGE_INVALID", "Khoảng hiệu lực phải từ ngày bắt đầu đến ngày kết thúc và không quá 366 ngày.");
        }
        if (request.weekdays() == null || request.weekdays().isEmpty()) {
            throw badRequest("SCHEDULE_WEEKDAY_REQUIRED", "Mẫu lịch phải có ít nhất một ngày trong tuần.");
        }
        if (request.dayStart() == null || request.dayEnd() == null || !request.dayStart().isBefore(request.dayEnd())) {
            throw badRequest("SCHEDULE_TIME_INVALID", "Giờ bắt đầu phải trước giờ kết thúc.");
        }
        if (request.durationMinutes() == null || request.durationMinutes() < 5 || request.durationMinutes() > 120) {
            throw badRequest("SCHEDULE_DURATION_INVALID", "Thời lượng mỗi lượt phải từ 5 đến 120 phút.");
        }
        if (request.dayStart().plusMinutes(request.durationMinutes()).isAfter(request.dayEnd())) {
            throw badRequest("SCHEDULE_TIME_INVALID", "Khoảng làm việc không đủ một lượt khám.");
        }

        if (request.clinicServiceId() == null || request.doctorId() == null || request.roomId() == null) {
            throw badRequest("SCHEDULE_REFERENCE_REQUIRED", "Dịch vụ, bác sĩ và phòng là bắt buộc.");
        }
        ClinicService clinicService = clinicServiceRepository.findById(request.clinicServiceId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CLINIC_SERVICE_NOT_FOUND",
                        "Không tìm thấy dịch vụ khám."));
        if (!clinicService.isActive()) {
            throw conflict("CLINIC_SERVICE_INACTIVE", "Dịch vụ khám đã tạm ngưng nhận lịch.");
        }
        if (clinicService.getDurationMinutes() != request.durationMinutes()) {
            throw conflict("SCHEDULE_DURATION_SERVICE_MISMATCH",
                    "Thời lượng mẫu lịch phải khớp thời lượng của dịch vụ khám.");
        }

        DoctorProfile doctor = doctorProfileRepository.findByStaffAccount_Id(request.doctorId())
                .filter(DoctorProfile::isActive)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_AVAILABLE",
                        "Không tìm thấy bác sĩ đang được phân công."));
        if (!doctor.getSpecialty().equalsIgnoreCase(clinicService.getSpecialty())) {
            throw conflict("SCHEDULE_SPECIALTY_MISMATCH", "Bác sĩ không thuộc chuyên khoa của dịch vụ.");
        }
        var eligibleDoctors = clinicService.getEligibleDoctors();
        if (eligibleDoctors != null && !eligibleDoctors.isEmpty()
                && eligibleDoctors.stream().noneMatch(item -> item.getId() != null
                && item.getId().equals(doctor.getId()))) {
            throw conflict("SCHEDULE_DOCTOR_NOT_ELIGIBLE", "Bác sĩ chưa được cấu hình cho dịch vụ này.");
        }
        ClinicRoom room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND",
                        "Không tìm thấy phòng khám."));
        if (!room.isActive()) throw conflict("ROOM_INACTIVE", "Phòng khám đã tạm ngưng hoạt động.");
        if (!room.getSpecialty().equalsIgnoreCase(clinicService.getSpecialty())) {
            throw conflict("SCHEDULE_ROOM_SPECIALTY_MISMATCH", "Phòng khám không thuộc chuyên khoa của dịch vụ.");
        }
        if (doctor.getRoom() == null || doctor.getRoom().getId() == null
                || !doctor.getRoom().getId().equals(room.getId())) {
            throw conflict("SCHEDULE_ROOM_MISMATCH", "Phòng mẫu lịch phải là phòng đang được gán cho bác sĩ.");
        }

        List<ScheduleBreakRequest> requestedBreaks = request.breaks() == null ? List.of() : request.breaks();
        if (requestedBreaks.size() > 5) throw badRequest("SCHEDULE_BREAK_LIMIT", "Mỗi ngày chỉ được có tối đa 5 khoảng nghỉ.");
        List<ScheduleBreak> breaks = new ArrayList<>();
        for (ScheduleBreakRequest item : requestedBreaks) {
            if (item == null || item.startTime() == null || item.endTime() == null
                    || !item.startTime().isBefore(item.endTime())
                    || item.startTime().isBefore(request.dayStart()) || item.endTime().isAfter(request.dayEnd())) {
                throw badRequest("SCHEDULE_BREAK_INVALID", "Khoảng nghỉ phải nằm trong giờ làm và có thời gian hợp lệ.");
            }
            if (breaks.stream().anyMatch(existing -> overlaps(existing.getStartTime(), existing.getEndTime(),
                    item.startTime(), item.endTime()))) {
                throw badRequest("SCHEDULE_BREAK_OVERLAP", "Các khoảng nghỉ không được giao nhau.");
            }
            breaks.add(ScheduleBreak.create(item.startTime(), item.endTime()));
        }
        Set<LocalDate> exceptionDates = safeDates(request.exceptionDates());
        if (exceptionDates.size() > 100 || exceptionDates.stream().anyMatch(date -> date.isBefore(request.startDate())
                || date.isAfter(request.endDate()))) {
            throw badRequest("SCHEDULE_EXCEPTION_INVALID", "Ngày ngoại lệ phải nằm trong khoảng hiệu lực và tối đa 100 ngày.");
        }
        return new ValidatedInput(clinicService, doctor, room, breaks, exceptionDates);
    }

    private Set<LocalDate> safeDates(Collection<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) return Set.of();
        if (dates.stream().anyMatch(Objects::isNull)) {
            throw badRequest("SCHEDULE_EXCEPTION_INVALID", "Ngày ngoại lệ không được để trống.");
        }
        return Set.copyOf(dates);
    }

    private List<ScheduleBreak> toBreaks(List<ScheduleBreakRequest> requests) {
        return requests == null ? List.of() : requests.stream()
                .map(item -> ScheduleBreak.create(item.startTime(), item.endTime())).toList();
    }

    private ScheduleTemplateResponse toResponse(WorkScheduleTemplate template) {
        int count = template.getId() == null ? 0
                : slotRepository.findByTemplateIdOrderByAppointmentDateAscStartTimeAsc(template.getId()).size();
        return toResponse(template, count);
    }

    private ScheduleTemplateResponse toResponse(WorkScheduleTemplate template, int generatedCount) {
        return new ScheduleTemplateResponse(template.getId(), template.getClinicService().getId(),
                template.getClinicService().getName(), template.getSpecialty(), template.getVisitType(),
                template.getDurationMinutes(), template.getDoctorProfile().getStaffAccount().getId(),
                template.getDoctorProfile().getStaffAccount().getFullName(),
                template.getDoctorProfile().getAvatarUrl(),
                template.getRoom().getId(),
                template.getRoom().getCode(), template.getStartDate(), template.getEndDate(), template.getWeekdays(),
                template.getDayStart(), template.getDayEnd(), template.getBreaks().stream()
                .map(ScheduleBreakResponse::from).toList(), template.getExceptionDates(), generatedCount,
                template.isActive());
    }

    private String key(GeneratedClinicSlot slot) {
        return slot.getAppointmentDate() + "|" + slot.getStartTime();
    }

    private boolean overlaps(LocalTime firstStart, LocalTime firstEnd, LocalTime secondStart, LocalTime secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private AuthException badRequest(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private record ValidatedInput(ClinicService clinicService, DoctorProfile doctor, ClinicRoom room,
                                  List<ScheduleBreak> breaks, Set<LocalDate> exceptionDates) {
    }

    private record SlotSeed(LocalDate date, LocalTime start, LocalTime end) {
        String key() { return date + "|" + start; }
    }
}
