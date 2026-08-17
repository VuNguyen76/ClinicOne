package com.clinicone.config;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final PatientAccountRepository patientRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClinicServiceRepository clinicServiceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String adminPassword;
    private final String receptionistPassword;
    private final String doctorPassword;
    private final String patientPassword;

    public LocalDataInitializer(StaffAccountRepository staffRepository,
                                ClinicRoomRepository roomRepository,
                                DoctorProfileRepository profileRepository,
                                DoctorScheduleRepository scheduleRepository,
                                PasswordEncoder passwordEncoder,
                                PatientAccountRepository patientRepository,
                                PatientProfileRepository patientProfileRepository,
                                AppointmentRepository appointmentRepository,
                                ClinicServiceRepository clinicServiceRepository,
                                JdbcTemplate jdbcTemplate,
                                @Value("${LOCAL_ADMIN_PASSWORD}") String adminPassword,
                                @Value("${LOCAL_RECEPTION_PASSWORD}") String receptionistPassword,
                                @Value("${LOCAL_DOCTOR_PASSWORD}") String doctorPassword,
                                @Value("${LOCAL_PATIENT_PASSWORD}") String patientPassword) {
        this.staffRepository = staffRepository;
        this.roomRepository = roomRepository;
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.passwordEncoder = passwordEncoder;
        this.patientRepository = patientRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.clinicServiceRepository = clinicServiceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.adminPassword = adminPassword;
        this.receptionistPassword = receptionistPassword;
        this.doctorPassword = doctorPassword;
        this.patientPassword = patientPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureAppointmentStatusConstraint();
        StaffAccount admin = ensureStaff("admin", adminPassword, "Quản trị viên", StaffRole.ADMIN);
        StaffAccount receptionist = ensureStaff("reception", receptionistPassword, "Nhân viên tiếp nhận", StaffRole.RECEPTIONIST);
        StaffAccount doctor = ensureStaff("doctor", doctorPassword, "Bác sĩ Nguyễn An", StaffRole.DOCTOR);
        StaffAccount secondDoctor = ensureStaff("doctor2", doctorPassword, "Bác sĩ Trần Minh", StaffRole.DOCTOR);
        StaffAccount thirdDoctor = ensureStaff("doctor3", doctorPassword, "Bác sĩ Lê Thu Hà", StaffRole.DOCTOR);
        DoctorProfile profile = profileRepository.findByStaffAccount_Id(doctor.getId()).orElse(null);
        ClinicRoom room;
        if (profile == null) {
            room = ensureRoom(DEFAULT_ROOM_CODE, "Phòng Khám Tổng Quát 01");
            profile = profileRepository.save(DoctorProfile.create(doctor, DEFAULT_SPECIALTY, room));
        } else {
            // Reuse an existing assignment so local bootstrap never creates a
            // second room that is not actually used by the seeded doctor.
            room = profile.getRoom();
        }
        DoctorProfile secondProfile = profileRepository.findByStaffAccount_Id(secondDoctor.getId()).orElse(null);
        ClinicRoom secondRoom;
        if (secondProfile == null) {
            secondRoom = ensureRoom("TQ-02", "Phòng Khám Tổng Quát 02");
            secondProfile = profileRepository.save(DoctorProfile.create(secondDoctor, DEFAULT_SPECIALTY, secondRoom));
        } else {
            secondRoom = secondProfile.getRoom();
        }
        DoctorProfile thirdProfile = profileRepository.findByStaffAccount_Id(thirdDoctor.getId()).orElse(null);
        ClinicRoom thirdRoom;
        if (thirdProfile == null) {
            thirdRoom = ensureRoom("TQ-03", "Phòng Khám Tổng Quát 03");
            thirdProfile = profileRepository.save(DoctorProfile.create(thirdDoctor, DEFAULT_SPECIALTY, thirdRoom));
        } else {
            thirdRoom = thirdProfile.getRoom();
        }
        ensureWeekdaySchedules(profile);
        ensureWeekdaySchedules(secondProfile);
        ensureWeekdaySchedules(thirdProfile);
        ensureClinicService(List.of(secondProfile, thirdProfile));
        PatientAccount patient = ensurePatient();
        PatientProfile patientProfile = ensurePatientProfile(patient);
        ensureDemoAppointment(patient, patientProfile, doctor, profile);
        log.info("Local bootstrap ready: admin={}, receptionist={}, doctors=[{}, {}, {}], patient={}, rooms=[{}, {}, {}]",
                admin.getUsername(), receptionist.getUsername(), doctor.getUsername(), secondDoctor.getUsername(),
                thirdDoctor.getUsername(), patient.getPhone(), room.getCode(), secondRoom.getCode(), thirdRoom.getCode());
    }

    /**
     * The shared development database was created before CHECKED_IN became a
     * supported appointment state. Keep local startup self-healing so a fresh
     * checkout can run the real QR/check-in flow without a manual SQL step.
     */
    private void ensureAppointmentStatusConstraint() {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'appointments_status_check'
                          AND conrelid = 'appointments'::regclass
                    ) THEN
                        ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;
                    END IF;
                    ALTER TABLE appointments
                        ADD CONSTRAINT appointments_status_check
                        CHECK (status IN ('BOOKED', 'CHECKED_IN', 'CANCELLED', 'ABSENT', 'COMPLETED', 'NOT_PERFORMED'));
                END $$;
                """);
    }

    private StaffAccount ensureStaff(String username, String password, String fullName, StaffRole role) {
        return staffRepository.findByUsernameIgnoreCase(username)
                .orElseGet(() -> staffRepository.save(StaffAccount.create(
                        username, passwordEncoder.encode(password), fullName, role)));
    }

    private ClinicRoom ensureRoom(String code, String name) {
        return roomRepository.findAllByOrderByCodeAsc().stream()
                .filter(item -> item.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseGet(() -> roomRepository.save(ClinicRoom.create(
                        code, name, DEFAULT_SPECIALTY)));
    }

    private ClinicService ensureClinicService(List<DoctorProfile> doctors) {
        String serviceName = "Khám tổng quát ClinicOne";
        String visitType = "Khám thường";
        ClinicService service = clinicServiceRepository.findAllByOrderByNameAsc().stream()
                .filter(item -> item.getName().equalsIgnoreCase(serviceName)
                        && item.getSpecialty().equalsIgnoreCase(DEFAULT_SPECIALTY)
                        && item.getVisitType().equalsIgnoreCase(visitType))
                .findFirst()
                .orElse(null);
        if (service == null) {
            return clinicServiceRepository.save(ClinicService.create(
                    serviceName, DEFAULT_SPECIALTY, visitType, 30, doctors));
        }
        service.update(serviceName, DEFAULT_SPECIALTY, visitType, 30, doctors);
        service.setActive(true);
        return clinicServiceRepository.save(service);
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

    private PatientAccount ensurePatient() {
        return patientRepository.findByPhone("0900000001")
                .orElseGet(() -> patientRepository.save(new PatientAccount(
                        "0900000001", passwordEncoder.encode(patientPassword), "Nguyễn Thanh Vũ",
                        AccountStatus.ACTIVE, false)));
    }

    private PatientProfile ensurePatientProfile(PatientAccount patient) {
        return patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(patient.getId())
                .stream().findFirst()
                .orElseGet(() -> patientProfileRepository.save(PatientProfile.create(
                        patient, patient.getFullName(), "Bản thân", LocalDate.of(2000, 1, 1), "Nam",
                        patient.getPhone(), null, "Việt Nam", "Kinh", null, true)));
    }

    private void ensureDemoAppointment(PatientAccount patient, PatientProfile profile, StaffAccount doctor,
                                       DoctorProfile doctorProfile) {
        LocalDate demoDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String demoCode = "LOCAL-DEMO-" + demoDate.toString().replace("-", "");
        if (appointmentRepository.findByAppointmentCode(demoCode).isPresent()) {
            return;
        }
        appointmentRepository.save(Appointment.create(patient, doctor.getId(), profile, demoCode,
                doctorProfile.getSpecialty(), doctor.getFullName(), demoDate, LocalTime.of(9, 0),
                "Khám tổng quát mẫu cho môi trường local"));
    }
}
