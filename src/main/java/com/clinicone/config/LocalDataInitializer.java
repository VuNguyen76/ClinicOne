package com.clinicone.config;

import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Safe, idempotent local-only data so the complete staff flow can be exercised
 * against a real database. Production profiles never run this initializer.
 */
@Component
@Profile("local")
public class LocalDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDataInitializer.class);
    private static final String DEFAULT_SPECIALTY = "Khám Tổng Quát";
    private static final String DEFAULT_ROOM_CODE = "TQ-01";

    private final StaffAccountRepository staffRepository;
    private final ClinicRoomRepository roomRepository;
    private final DoctorProfileRepository profileRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalDataInitializer(StaffAccountRepository staffRepository,
                                ClinicRoomRepository roomRepository,
                                DoctorProfileRepository profileRepository,
                                DoctorScheduleRepository scheduleRepository,
                                PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.roomRepository = roomRepository;
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        StaffAccount admin = ensureStaff("admin", "admin123", "Quản trị viên", StaffRole.ADMIN);
        StaffAccount receptionist = ensureStaff("reception", "reception123", "Nhân viên tiếp nhận", StaffRole.RECEPTIONIST);
        StaffAccount doctor = ensureStaff("doctor", "doctor123", "Bác sĩ Nguyễn An", StaffRole.DOCTOR);
        ClinicRoom room = ensureRoom();
        DoctorProfile profile = profileRepository.findByStaffAccount_Id(doctor.getId())
                .orElseGet(() -> profileRepository.save(DoctorProfile.create(doctor, DEFAULT_SPECIALTY, room)));
        ensureWeekdaySchedules(profile);
        log.info("Local bootstrap ready: admin={}, receptionist={}, doctor={}, room={}",
                admin.getUsername(), receptionist.getUsername(), doctor.getUsername(), room.getCode());
    }

    private StaffAccount ensureStaff(String username, String password, String fullName, StaffRole role) {
        return staffRepository.findByUsernameIgnoreCase(username)
                .orElseGet(() -> staffRepository.save(StaffAccount.create(
                        username, passwordEncoder.encode(password), fullName, role)));
    }

    private ClinicRoom ensureRoom() {
        return roomRepository.findAllByOrderByCodeAsc().stream()
                .filter(item -> item.getCode().equalsIgnoreCase(DEFAULT_ROOM_CODE))
                .findFirst()
                .orElseGet(() -> roomRepository.save(ClinicRoom.create(
                        DEFAULT_ROOM_CODE, "Phòng Khám Tổng Quát 01", DEFAULT_SPECIALTY)));
    }

    private void ensureWeekdaySchedules(DoctorProfile profile) {
        List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        for (DayOfWeek day : weekdays) {
            boolean exists = scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(profile.getId(), day)
                    .stream().findAny().isPresent();
            if (!exists) {
                scheduleRepository.save(DoctorSchedule.create(profile, day,
                        LocalTime.of(8, 0), LocalTime.of(17, 0), 30));
            }
        }
    }
}
