package com.clinicone.config;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.diagnosis.DiagnosisCatalog;
import com.clinicone.diagnosis.DiagnosisCatalogRepository;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.examination.DoctorExaminationService;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.examination.MedicalRecordTemplate;
import com.clinicone.examination.MedicalRecordTemplateRepository;
import com.clinicone.examination.PrescriptionLine;
import com.clinicone.medication.Medication;
import com.clinicone.medication.MedicationRepository;
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

    @Autowired(required = false)
    private MedicationRepository medicationRepository;

    @Autowired(required = false)
    private DiagnosisCatalogRepository diagnosisCatalogRepository;

    @Autowired(required = false)
    private MedicalRecordTemplateRepository medicalRecordTemplateRepository;

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
        StaffAccount coordinator = ensureStaff("coordinator", adminPassword, "Điều phối viên Trần Hoàng", StaffRole.COORDINATOR);
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

        // 1. Seed Essential Medications, ICD-10 Diagnoses, and Medical Record Templates
        ensureMedications();
        ensureDiagnoses();
        ensureTemplates();

        // 2. Seed Patients & Sub-profiles
        PatientAccount patient1 = ensurePatient("0900000001", "Nguyễn Thanh Vũ");
        PatientProfile profile1 = ensurePatientProfile(patient1, "Nguyễn Thanh Vũ", "Bản thân", LocalDate.of(2000, 1, 1), "Nam", patient1.getPhone(), true);
        PatientProfile childProfile = ensurePatientProfile(patient1, "Bé Nguyễn Bảo Nam", "Con cái", LocalDate.of(2020, 5, 15), "Nam", patient1.getPhone(), false);

        PatientAccount patient2 = ensurePatient("0900000002", "Trần Thị Mai");
        PatientProfile profile2 = ensurePatientProfile(patient2, "Trần Thị Mai", "Bản thân", LocalDate.of(1994, 8, 20), "Nữ", patient2.getPhone(), true);

        PatientAccount patient3 = ensurePatient("0900000004", "Phạm Minh Đức");
        PatientProfile profile3 = ensurePatientProfile(patient3, "Phạm Minh Đức", "Bản thân", LocalDate.of(1958, 11, 12), "Nam", patient3.getPhone(), true);

        // 3. Seed Past Signed Medical Records (for clinical history visualization)
        ensurePastSignedRecord(patient1, profile1, doctor, profile, room, 7, "PAST-REC-001",
                "Đau rát họng 3 ngày, sốt nhẹ 38°C, ho khan nhiều về đêm",
                "Niêm mạc họng đỏ, amidan sưng nhẹ độ 1, không có giả mạc, tim phổi bình thường",
                "J00 - Viêm mũi họng cấp tính (cảm thường)",
                "Viêm đường hô hấp trên thể nhẹ",
                "Nghỉ ngơi, uống nhiều nước ấm, súc họng nước muối sinh lý",
                List.of(
                        new SeedMedication("Paracetamol 500mg (Hạ sốt, giảm đau)", "500mg", 10, "Uống 1 viên khi sốt trên 38.5°C cách 6h"),
                        new SeedMedication("Cefuroxime 500mg / Zinnat (Kháng sinh Cephalosporin)", "500mg", 14, "Uống 1 viên x 2 lần/ngày sau ăn sáng/tối"),
                        new SeedMedication("Vitamin C 500mg (Tăng cường đề kháng)", "500mg", 10, "Uống 1 viên/ngày sau ăn sáng")
                ));

        ensurePastSignedRecord(patient3, profile3, doctor, profile, room, 3, "PAST-REC-002",
                "Kiểm tra huyết áp định kỳ, thỉnh thoảng có cảm giác hồi hộp nhẹ",
                "Huyết áp 145/90 mmHg, nhịp tim 76 ck/phút, phổi trong không ran",
                "I10 - Tăng huyết áp vô căn (nguyên phát)",
                "Tăng huyết áp độ 1 có kiểm soát",
                "Duy trì thuốc hạ áp hàng ngày, ăn nhạt, hạn chế muối",
                List.of(
                        new SeedMedication("Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)", "5mg", 30, "Uống 1 viên vào 8h sáng hàng ngày"),
                        new SeedMedication("Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)", "50mg", 30, "Uống 1 viên vào 8h sáng hàng ngày")
                ));

        // 4. Seed Today's Active Clinic Queue Tickets
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        ensureActiveQueueScenario(patient1, profile1, doctor, profile, room, today, 1, LocalTime.of(8, 30), "TODAY-TQ01-001", QueueScenario.IN_SERVICE);
        ensureActiveQueueScenario(patient2, profile2, doctor, profile, room, today, 2, LocalTime.of(9, 0), "TODAY-TQ01-002", QueueScenario.WAITING);
        ensureActiveQueueScenario(patient3, profile3, doctor, profile, room, today, 3, LocalTime.of(9, 30), "TODAY-TQ01-003", QueueScenario.PRIORITY_WAITING);
        ensureActiveQueueScenario(patient1, childProfile, doctor, profile, room, today, 4, LocalTime.of(8, 0), "TODAY-TQ01-004", QueueScenario.COMPLETED);

        log.info("Local bootstrap ready: admin={}, receptionist={}, doctors=[{}, {}, {}], seeded clinical catalogs, templates, 4 queue tickets & histories",
                admin.getUsername(), receptionist.getUsername(), doctor.getUsername(), secondDoctor.getUsername(), thirdDoctor.getUsername());
    }

    private void ensureMedications() {
        if (medicationRepository == null) return;
        List<String[]> meds = List.of(
                new String[]{"MED-PARA-500", "Paracetamol 500mg (Hạ sốt, giảm đau)"},
                new String[]{"MED-AMOX-500", "Amoxicillin 500mg (Kháng sinh nhóm Penicillin)"},
                new String[]{"MED-CEFU-500", "Cefuroxime 500mg / Zinnat (Kháng sinh Cephalosporin)"},
                new String[]{"MED-IBUP-400", "Ibuprofen 400mg (Kháng viêm, giảm đau NSAID)"},
                new String[]{"MED-OMEP-20", "Omeprazole 20mg (Ức chế bơm Proton, dạ dày)"},
                new String[]{"MED-ESOM-40", "Esomeprazole 40mg / Nexium (Trào ngược dạ dày GERD)"},
                new String[]{"MED-AMLO-5", "Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)"},
                new String[]{"MED-LOSA-50", "Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)"},
                new String[]{"MED-METF-500", "Metformin 500mg (Hạ đường huyết đái tháo đường)"},
                new String[]{"MED-ATOR-20", "Atorvastatin 20mg (Hạ mỡ máu, giảm Cholesterol)"},
                new String[]{"MED-BERB-100", "Berberin 100mg (Kháng khuẩn tiêu hóa, tiêu chảy)"},
                new String[]{"MED-LORA-10", "Loratadine 10mg (Kháng Histamin, chống dị ứng)"},
                new String[]{"MED-CETI-10", "Cetirizine 10mg (Chống dị ứng, viêm mũi dị ứng)"},
                new String[]{"MED-SALB-2", "Salbutamol 2mg (Giãn phế quản, hen suyễn)"},
                new String[]{"MED-VITC-500", "Vitamin C 500mg (Tăng cường đề kháng)"},
                new String[]{"MED-GLUC-1500", "Glucosamine 1500mg (Hỗ trợ khớp, sụn khớp)"},
                new String[]{"MED-TOBR-03", "Tobramycin 0.3% / Tobrex (Thuốc nhỏ mắt kháng khuẩn)"}
        );
        for (String[] item : meds) {
            if (!medicationRepository.existsByCodeIgnoreCase(item[0])) {
                medicationRepository.save(Medication.create(item[0], item[1]));
            }
        }
    }

    private void ensureDiagnoses() {
        if (diagnosisCatalogRepository == null) return;
        List<String[]> list = List.of(
                new String[]{"J00", "J00 - Viêm mũi họng cấp tính (cảm thường)"},
                new String[]{"J02", "J02 - Viêm họng cấp"},
                new String[]{"J03", "J03 - Viêm amiđan cấp"},
                new String[]{"J20", "J20 - Viêm phế quản cấp"},
                new String[]{"K21", "K21 - Bệnh trào ngược dạ dày - thực quản (GERD)"},
                new String[]{"K29", "K29 - Viêm dạ dày và tá tràng"},
                new String[]{"I10", "I10 - Tăng huyết áp vô căn (nguyên phát)"},
                new String[]{"E11", "E11 - Đái tháo đường không phụ thuộc insulin (Type 2)"},
                new String[]{"E78", "E78 - Rối loạn chuyển hóa lipoprotein và tăng lipid máu"},
                new String[]{"M17", "M17 - Thoái hóa khớp gối"},
                new String[]{"M54.5", "M54.5 - Đau thắt lưng"},
                new String[]{"H10", "H10 - Viêm kết mạc (đau mắt đỏ)"},
                new String[]{"L20", "L20 - Viêm da cơ địa (chàm)"},
                new String[]{"Z00.0", "Z00.0 - Khám sức khỏe tổng quát định kỳ"}
        );
        for (String[] item : list) {
            if (!diagnosisCatalogRepository.existsByCodeIgnoreCase(item[0])) {
                diagnosisCatalogRepository.save(DiagnosisCatalog.create(item[0], item[1]));
            }
        }
    }

    private void ensureTemplates() {
        if (medicalRecordTemplateRepository == null) return;
        ensureOneTemplate("TMPL-TQ-01", "Mẫu khám tổng quát định kỳ", "Khám Tổng Quát",
                "Mẫu chuẩn khám sức khỏe tổng quát định kỳ",
                """
                {"reason":"Khám sức khỏe tổng quát định kỳ theo dõi thể trạng","examinationNotes":"Thể trạng chung tốt, huyết áp 120/80 mmHg, nhịp tim đều 75 ck/phút, phổi trong, tim T1 T2 rõ không âm thổi, bụng mềm không điểm đau khu trú.","diagnosis":"Z00.0 - Khám sức khỏe tổng quát định kỳ","conclusion":"Tình trạng sức khỏe hiện tại ổn định","treatmentPlan":"Duy trì chế độ ăn uống khoa học, tập thể dục ít nhất 30 phút/ngày, uống đủ 2 lít nước.","followUpDays":180,"followUpNote":"Khám định kỳ sau 6 tháng hoặc khi có dấu hiệu bất thường"}
                """);

        ensureOneTemplate("TMPL-HH-01", "Mẫu khám viêm đường hô hấp / Cảm cúm", "Khám Hô Hấp",
                "Mẫu chuẩn viêm mũi họng, cảm cúm, ho khan sốt nhẹ",
                """
                {"reason":"Đau rát họng, ho húng hắng, sốt nhẹ 38°C, nghẹt mũi","examinationNotes":"Niêm mạc họng đỏ, amidan sung huyết nhẹ không giả mạc, mũi xuất tiết dịch trong, phổi thông khí tốt không rale.","diagnosis":"J00 - Viêm mũi họng cấp tính (cảm thường)","conclusion":"Viêm đường hô hấp trên cấp tính thể nhẹ","treatmentPlan":"Nghỉ ngơi, súc họng nước muối sinh lý 3 lần/ngày, giữ ấm cổ ngực, uống nhiều nước ấm.","followUpDays":7,"followUpNote":"Tái khám sau 7 ngày nếu còn sốt cao hoặc ho kéo dài"}
                """);

        ensureOneTemplate("TMPL-TM-01", "Mẫu khám Tăng huyết áp", "Khám Tim Mạch",
                "Mẫu chuẩn theo dõi và điều trị tăng huyết áp nguyên phát",
                """
                {"reason":"Đo kiểm tra huyết áp định kỳ, thỉnh thoảng hơi căng tức thái dương","examinationNotes":"Huyết áp đo tại phòng khám 140/85 mmHg, mạch 76 lần/phút, tim T1 T2 đều rõ, không phù chi dưới, không ran phổi.","diagnosis":"I10 - Tăng huyết áp vô căn (nguyên phát)","conclusion":"Tăng huyết áp độ 1 giai đoạn ổn định","treatmentPlan":"Duy trì thuốc hạ áp hàng ngày vào buổi sáng, chế độ ăn giảm muối, hạn chế dầu mỡ và thức uống có cồn.","followUpDays":30,"followUpNote":"Tái khám đo lại huyết áp và đánh giá chức năng sau 1 tháng"}
                """);

        ensureOneTemplate("TMPL-TH-01", "Mẫu khám Dạ dày - GERD", "Khám Tiêu Hoá - Gan Mật",
                "Mẫu chuẩn viêm dạ dày, trào ngược dạ dày thực quản",
                """
                {"reason":"Đau âm ỉ vùng thượng vị sau ăn, ợ hơi ợ chua, cồn cào","examinationNotes":"Bụng mềm, ấn tức nhẹ vùng thượng vị, không đề kháng thành bụng, gan lách không to.","diagnosis":"K21 - Bệnh trào ngược dạ dày - thực quản (GERD)","conclusion":"Viêm dạ dày kết hợp trào ngược thực quản","treatmentPlan":"Uống thuốc bảo vệ niêm mạc trước ăn sáng 30 phút, ăn đúng giờ, không ăn no sát giờ ngủ, tránh thức ăn cay nóng, cà phê.","followUpDays":14,"followUpNote":"Tái khám sau 2 tuần để đánh giá đáp ứng điều trị"}
                """);
    }

    private void ensureOneTemplate(String code, String name, String specialty, String description, String fieldDef) {
        boolean exists = medicalRecordTemplateRepository.findAll().stream().anyMatch(t -> t.getCode().equalsIgnoreCase(code));
        if (!exists) {
            medicalRecordTemplateRepository.save(MedicalRecordTemplate.create(code, name, specialty, null, description, fieldDef.trim(), "admin"));
        }
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
