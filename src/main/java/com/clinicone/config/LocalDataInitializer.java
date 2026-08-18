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
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.examination.PrescriptionLine;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Instant;
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

    @Autowired(required = false)
    private QueueTicketRepository queueTicketRepository;

    @Autowired(required = false)
    private ExaminationSessionRepository examinationSessionRepository;

    @Autowired(required = false)
    private MedicalRecordRepository medicalRecordRepository;

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

        // 1. Seed Patients & Sub-profiles
        PatientAccount patient1 = ensurePatient("0900000001", "Nguyễn Thanh Vũ");
        PatientProfile profile1 = ensurePatientProfile(patient1, "Nguyễn Thanh Vũ", "Bản thân", LocalDate.of(2000, 1, 1), "Nam", patient1.getPhone(), true);
        PatientProfile childProfile = ensurePatientProfile(patient1, "Bé Nguyễn Bảo Nam", "Con cái", LocalDate.of(2020, 5, 15), "Nam", patient1.getPhone(), false);

        PatientAccount patient2 = ensurePatient("0900000002", "Trần Thị Mai");
        PatientProfile profile2 = ensurePatientProfile(patient2, "Trần Thị Mai", "Bản thân", LocalDate.of(1994, 8, 20), "Nữ", patient2.getPhone(), true);

        PatientAccount patient3 = ensurePatient("0900000004", "Phạm Minh Đức");
        PatientProfile profile3 = ensurePatientProfile(patient3, "Phạm Minh Đức", "Bản thân", LocalDate.of(1958, 11, 12), "Nam", patient3.getPhone(), true);

        // 2. Seed Past Signed Medical Records (for clinical history visualization)
        ensurePastSignedRecord(patient1, profile1, doctor, profile, room, 7, "PAST-REC-001",
                "Đau rát họng 3 ngày, sốt nhẹ 38°C, ho khan nhiều về đêm",
                "Niêm mạc họng đỏ, amidan sưng nhẹ độ 1, không có giả mạc, tim phổi bình thường",
                "J00 - Viêm mũi họng cấp tính (cảm thường)",
                "Viêm đường hô hấp trên thể nhẹ",
                "Nghỉ ngơi, uống nhiều nước ấm, súc họng nước muối sinh lý",
                List.of(
                        new SeedMedication("Paracetamol 500mg", "500mg", 10, "Uống 1 viên khi sốt trên 38.5°C cách 6h"),
                        new SeedMedication("Cefuroxime 500mg", "500mg", 14, "Uống 1 viên x 2 lần/ngày sau ăn sáng/tối"),
                        new SeedMedication("Vitamin C 500mg", "500mg", 10, "Uống 1 viên/ngày sau ăn sáng")
                ));

        ensurePastSignedRecord(patient3, profile3, doctor, profile, room, 3, "PAST-REC-002",
                "Kiểm tra huyết áp định kỳ, thỉnh thoảng có cảm giác hồi hộp nhẹ",
                "Huyết áp 145/90 mmHg, nhịp tim 76 ck/phút, phổi trong không ran",
                "I10 - Tăng huyết áp vô căn (nguyên phát)",
                "Tăng huyết áp độ 1 có kiểm soát",
                "Duy trì thuốc hạ áp hàng ngày, ăn nhạt, hạn chế muối",
                List.of(
                        new SeedMedication("Amlodipine 5mg", "5mg", 30, "Uống 1 viên vào 8h sáng hàng ngày"),
                        new SeedMedication("Losartan 50mg", "50mg", 30, "Uống 1 viên vào 8h sáng hàng ngày")
                ));

        // 3. Seed Today's Active Clinic Queue Tickets (for 2026-08-17)
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        ensureActiveQueueScenario(patient1, profile1, doctor, profile, room, today, 1, LocalTime.of(8, 30), "TODAY-TQ01-001", QueueScenario.IN_SERVICE);
        ensureActiveQueueScenario(patient2, profile2, doctor, profile, room, today, 2, LocalTime.of(9, 0), "TODAY-TQ01-002", QueueScenario.WAITING);
        ensureActiveQueueScenario(patient3, profile3, doctor, profile, room, today, 3, LocalTime.of(9, 30), "TODAY-TQ01-003", QueueScenario.PRIORITY_WAITING);
        ensureActiveQueueScenario(patient1, childProfile, doctor, profile, room, today, 4, LocalTime.of(8, 0), "TODAY-TQ01-004", QueueScenario.COMPLETED);

        log.info("Local bootstrap ready: admin={}, receptionist={}, doctors=[{}, {}, {}], seeded 4 live queue tickets & past clinical histories",
                admin.getUsername(), receptionist.getUsername(), doctor.getUsername(), secondDoctor.getUsername(), thirdDoctor.getUsername());
    }

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
        StaffAccount account = staffRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (account == null) {
            return staffRepository.save(StaffAccount.create(
                    username, passwordEncoder.encode(password), fullName, role));
        }
        account.unlock();
        account.changePassword(passwordEncoder.encode(password));
        return staffRepository.save(account);
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

    private PatientAccount ensurePatient(String phone, String fullName) {
        PatientAccount patient = patientRepository.findByPhone(phone).orElse(null);
        if (patient == null) {
            return patientRepository.save(new PatientAccount(
                    phone, passwordEncoder.encode(patientPassword), fullName,
                    AccountStatus.ACTIVE, false));
        }
        patient.unlock();
        patient.changePassword(passwordEncoder.encode(patientPassword));
        return patientRepository.save(patient);
    }

    private PatientProfile ensurePatientProfile(PatientAccount patient, String fullName, String relationship,
                                                LocalDate dob, String gender, String phone, boolean isPrimary) {
        return patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(patient.getId())
                .stream().filter(p -> p.getFullName().equalsIgnoreCase(fullName)).findFirst()
                .orElseGet(() -> patientProfileRepository.save(PatientProfile.create(
                        patient, fullName, relationship, dob, gender, phone, null, "Việt Nam", "Kinh", null, isPrimary)));
    }

    private void ensurePastSignedRecord(PatientAccount patient, PatientProfile profile, StaffAccount doctor,
                                        DoctorProfile doctorProfile, ClinicRoom room, int daysAgo, String code,
                                        String reason, String notes, String diagnosis, String conclusion, String plan,
                                        List<SeedMedication> meds) {
        LocalDate pastDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(daysAgo);
        LocalTime pastTime = LocalTime.of(9, 0);
        if (appointmentRepository.findByAppointmentCode(code).isPresent()
                || appointmentRepository.existsByPatientIdAndAppointmentDateAndStartTime(patient.getId(), pastDate, pastTime)) {
            return;
        }

        Appointment app = appointmentRepository.save(Appointment.create(patient, doctor.getId(), profile, code,
                doctorProfile.getSpecialty(), doctor.getFullName(), pastDate, pastTime, reason));
        app.checkIn();
        app.complete();
        appointmentRepository.save(app);

        if (queueTicketRepository != null && examinationSessionRepository != null && medicalRecordRepository != null) {
            QueueTicket ticket = QueueTicket.create(app, room, pastDate, 1);
            ticket.call();
            ticket.startService();
            ticket.complete();
            queueTicketRepository.save(ticket);

            ExaminationSession session = ExaminationSession.create(app);
            session.begin();
            session.complete();
            session = examinationSessionRepository.saveAndFlush(session);

            MedicalRecord record = MedicalRecord.draft(session);
            int lineNumber = 1;
            List<PrescriptionLine> lines = new java.util.ArrayList<>();
            for (SeedMedication m : meds) {
                lines.add(PrescriptionLine.create(record, null, m.name(), m.dosage(), m.quantity(), m.instructions(), lineNumber++));
            }
            record.sign(doctor.getFullName(), reason, notes, diagnosis, conclusion, plan, "Đơn thuốc điện tử",
                    pastDate.plusDays(30), lines, 30, "Tái khám đúng hẹn hoặc khi có triệu chứng bất thường");
            medicalRecordRepository.save(record);
        }
    }

    private enum QueueScenario { IN_SERVICE, WAITING, PRIORITY_WAITING, COMPLETED }

    private void ensureActiveQueueScenario(PatientAccount patient, PatientProfile profile, StaffAccount doctor,
                                           DoctorProfile doctorProfile, ClinicRoom room, LocalDate date,
                                           int queueNum, LocalTime time, String code, QueueScenario scenario) {
        if (appointmentRepository.findByAppointmentCode(code).isPresent()
                || appointmentRepository.existsByPatientIdAndAppointmentDateAndStartTime(patient.getId(), date, time)) {
            return;
        }

        Appointment app = appointmentRepository.save(Appointment.create(patient, doctor.getId(), profile, code,
                doctorProfile.getSpecialty(), doctor.getFullName(), date, time, "Khám tổng quát định kỳ"));
        app.checkIn();

        if (scenario == QueueScenario.COMPLETED) {
            app.complete();
        }
        appointmentRepository.save(app);

        if (queueTicketRepository != null && examinationSessionRepository != null) {
            QueueTicket ticket = QueueTicket.create(app, room, date, queueNum);
            ExaminationSession session = ExaminationSession.create(app);

            if (scenario == QueueScenario.IN_SERVICE) {
                ticket.call();
                ticket.startService();
                session.begin();
            } else if (scenario == QueueScenario.PRIORITY_WAITING) {
                ticket.setPriority(true);
            } else if (scenario == QueueScenario.COMPLETED) {
                ticket.call();
                ticket.startService();
                ticket.complete();
                session.begin();
                session.complete();
            }

            queueTicketRepository.save(ticket);
            session = examinationSessionRepository.saveAndFlush(session);

            if (scenario == QueueScenario.COMPLETED && medicalRecordRepository != null) {
                MedicalRecord record = MedicalRecord.draft(session);
                record.sign(doctor.getFullName(), "Khám định kỳ", "Thể trạng bình thường", "Z00.0 - Khám sức khỏe tổng quát",
                        "Sức khỏe tốt", "Tập thể dục đều đặn", null, null, List.of(), null, null);
                medicalRecordRepository.save(record);
            }
        }
    }

    private record SeedMedication(String name, String dosage, int quantity, String instructions) {}
}
