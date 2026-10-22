package com.clinicone.doctor;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import lombok.Builder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import com.clinicone.schedule.SpecialtyCatalogService;
import com.clinicone.rescheduling.ReschedulingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
public class DoctorManagementService {
    private final StaffAccountRepository staffRepository;
    private final DoctorProfileRepository profileRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final ClinicRoomRepository roomRepository;
    private final SpecialtyCatalogService specialtyCatalog;
    private final ClinicServiceRepository clinicServiceRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReschedulingService reschedulingService;

    @Builder
    public DoctorManagementService(StaffAccountRepository staffRepository,
                                   DoctorProfileRepository profileRepository,
                                   DoctorScheduleRepository scheduleRepository,
                                   ClinicRoomRepository roomRepository,
                                   SpecialtyCatalogService specialtyCatalog,
                                   ClinicServiceRepository clinicServiceRepository,
                                   PasswordEncoder passwordEncoder,
                                   ReschedulingService reschedulingService) {
        this.staffRepository = staffRepository;
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.roomRepository = roomRepository;
        this.specialtyCatalog = specialtyCatalog;
        this.clinicServiceRepository = clinicServiceRepository;
        this.passwordEncoder = passwordEncoder;
        this.reschedulingService = reschedulingService;
    }

    @Transactional(readOnly = true)
    public List<DoctorAccountResponse> list() {
        List<StaffAccount> doctors = staffRepository.findAllByOrderByFullNameAsc().stream()
                .filter(staff -> staff.hasRole(StaffRole.DOCTOR))
                .toList();
        if (doctors.isEmpty()) {
            return List.of();
        }
        List<DoctorProfile> allProfiles = profileRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, DoctorProfile> profileMap = allProfiles.stream()
                .filter(p -> p.getStaffAccount() != null && p.getStaffAccount().getId() != null)
                .collect(Collectors.toMap(p -> p.getStaffAccount().getId(), p -> p, (a, b) -> a));

        return doctors.stream()
                .map(staff -> {
                    DoctorProfile profile = profileMap.get(staff.getId());
                    return profile != null
                            ? DoctorAccountResponse.from(staff, profile)
                            : DoctorAccountResponse.unassigned(staff);
                })
                .toList();
    }

    @Transactional
    public DoctorAccountResponse createDoctor(DoctorCreateRequest request) {
        String username = request.username().trim();
        String fullName = request.fullName().trim();
        if (staffRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw conflict("STAFF_USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng.");
        }
        StaffAccount doctor = staffRepository.save(StaffAccount.create(
                username, passwordEncoder.encode(request.password()), fullName, StaffRole.DOCTOR));
        return DoctorAccountResponse.unassigned(doctor);
    }

    @Transactional
    public DoctorProfileResponse assign(UUID staffId, DoctorAssignmentRequest request) {
        StaffAccount staff = findDoctor(staffId);
        String specialty = specialtyCatalog.require(request.specialty()).name();
        ClinicRoom room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Không tìm thấy phòng khám."));
        if (!room.isActive()) {
            throw conflict("ROOM_INACTIVE", "Chỉ được gán phòng đang hoạt động.");
        }
        if (!room.getSpecialty().equalsIgnoreCase(specialty)) {
            throw conflict("ROOM_SPECIALTY_MISMATCH", "Phòng khám không thuộc chuyên khoa đã chọn.");
        }
        DoctorProfile profile = profileRepository.findByStaffAccount_Id(staffId)
                .orElseGet(() -> DoctorProfile.create(staff, specialty, room, request.avatarUrl()));
        String cleanAvatarUrl = StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null;
        profile.updateAssignment(specialty, room, cleanAvatarUrl);
        DoctorProfile saved = profileRepository.save(profile);
        linkOrphanDoctorToSameSpecialtyServices(saved);
        return DoctorProfileResponse.from(saved);
    }

    @Transactional
    public DoctorAccountResponse updateProfile(UUID staffId, UpdateDoctorProfileRequest request) {
        if (request == null || request.fullName() == null || request.fullName().trim().length() < 2
                || request.fullName().trim().length() > 200) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_NAME_INVALID",
                    "Họ tên bác sĩ phải có từ 2 đến 200 ký tự.");
        }
        StaffAccount staff = findDoctor(staffId);
        staff.updateProfile(request.fullName().trim(),
                staff.getUnitName() == null ? "ClinicOne" : staff.getUnitName(),
                staff.getDepartmentName() == null ? "Chưa phân ban" : staff.getDepartmentName());
        StaffAccount saved = staffRepository.save(staff);
        return profileRepository.findByStaffAccount_Id(staffId)
                .map(profile -> DoctorAccountResponse.from(saved, profile))
                .orElseGet(() -> DoctorAccountResponse.unassigned(saved));
    }

    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> schedules(UUID staffId) {
        DoctorProfile profile = profile(staffId);
        return scheduleRepository.findByDoctorProfile_IdOrderByDayOfWeekAscStartTimeAsc(profile.getId()).stream()
                .map(DoctorScheduleResponse::from)
                .toList();
    }

    @Transactional
    public DoctorScheduleResponse addSchedule(UUID staffId, DoctorScheduleRequest request) {
        DoctorProfile profile = profile(staffId);
        validateTimeRange(request.startTime(), request.endTime(), request.slotDurationMinutes());
        List<DoctorSchedule> existing = scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(
                profile.getId(), request.dayOfWeek());
        boolean overlaps = existing.stream().anyMatch(item -> overlaps(item.getStartTime(), item.getEndTime(),
                request.startTime(), request.endTime()));
        if (overlaps) {
            throw conflict("DOCTOR_SCHEDULE_OVERLAP", "Giờ làm mới bị trùng với giờ làm đã có.");
        }
        DoctorSchedule schedule = DoctorSchedule.create(profile, request.dayOfWeek(), request.startTime(),
                request.endTime(), request.slotDurationMinutes());
        return DoctorScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public void removeSchedule(UUID staffId, UUID scheduleId) {
        DoctorProfile profile = profile(staffId);
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_SCHEDULE_NOT_FOUND",
                        "Không tìm thấy giờ làm."));
        if (!schedule.getDoctorProfile().getId().equals(profile.getId())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_SCHEDULE_NOT_FOUND", "Không tìm thấy giờ làm.");
        }
        if (!schedule.isActive()) {
            return;
        }
        schedule.setActive(false);
        scheduleRepository.save(schedule);
        if (reschedulingService != null) {
            reschedulingService.openForScheduleRemoval(schedule);
        }
    }

    /**
     * A freshly assigned doctor used to stay invisible for scheduling and booking,
     * because {@code clinic_service_doctors} was only ever written by the service
     * management screen. Link an orphan doctor (one linked to no service at all) to
     * every active service of the same specialty that already keeps an explicit
     * doctor list. Services with an empty list already accept every doctor of the
     * specialty, so they need no row.
     */
    private void linkOrphanDoctorToSameSpecialtyServices(DoctorProfile profile) {
        if (clinicServiceRepository == null || profile == null) {
            return;
        }
        String specialty = profile.getSpecialty();
        if (specialty == null || specialty.isBlank()) {
            return;
        }
        List<ClinicService> services = clinicServiceRepository.findByActiveTrueOrderByNameAsc();
        boolean alreadyLinked = services.stream()
                .anyMatch(service -> service.getEligibleDoctors().stream()
                        .anyMatch(doctor -> doctor == profile || (doctor != null
                                && profile.getId() != null && profile.getId().equals(doctor.getId()))));
        if (alreadyLinked) {
            return;
        }
        for (ClinicService service : services) {
            if (service.getSpecialty() == null || !service.getSpecialty().equalsIgnoreCase(specialty)) {
                continue;
            }
            if (service.getEligibleDoctors().isEmpty()) {
                continue;
            }
            service.getEligibleDoctors().add(profile);
            clinicServiceRepository.save(service);
            log.info("Linked doctor {} to service {} because its explicit doctor list did not include them",
                    profile.getStaffAccount() == null ? profile.getId() : profile.getStaffAccount().getFullName(),
                    service.getName());
        }
    }

    private StaffAccount findDoctor(UUID staffId) {
        StaffAccount staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND",
                        "Không tìm thấy tài khoản bác sĩ."));
        if (!staff.hasRole(StaffRole.DOCTOR)) {
            throw new AuthException(HttpStatus.CONFLICT, "STAFF_NOT_DOCTOR", "Tài khoản này không có vai trò bác sĩ.");
        }
        return staff;
    }

    private DoctorProfile profile(UUID staffId) {
        findDoctor(staffId);
        return profileRepository.findByStaffAccount_Id(staffId)
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "DOCTOR_ASSIGNMENT_REQUIRED",
                        "Cần gán chuyên khoa và phòng trước khi đăng ký giờ làm."));
    }

    private void validateTimeRange(LocalTime start, LocalTime end, int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "SLOT_DURATION_INVALID",
                    "Thời lượng khung giờ phải lớn hơn 0.");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_SCHEDULE_TIME_INVALID",
                    "Giờ bắt đầu phải trước giờ kết thúc.");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes % durationMinutes != 0) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_SCHEDULE_DURATION_INVALID",
                    "Khoảng giờ làm phải chia hết cho thời lượng mỗi lượt.");
        }
    }

    private boolean overlaps(LocalTime firstStart, LocalTime firstEnd, LocalTime secondStart, LocalTime secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
