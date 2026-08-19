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
import com.clinicone.notification.PatientNotification;
import com.clinicone.notification.PatientNotificationRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.reason.ReasonCatalog;
import com.clinicone.reason.ReasonCatalogRepository;
import com.clinicone.reason.ReasonCatalogType;
import com.clinicone.reconciliation.ReconciliationAction;
import com.clinicone.reconciliation.ReconciliationIncident;
import com.clinicone.reconciliation.ReconciliationIncidentRepository;
import com.clinicone.reconciliation.ReconciliationReferenceType;
import com.clinicone.rescheduling.DoctorTimeOff;
import com.clinicone.rescheduling.DoctorTimeOffRepository;
import com.clinicone.rescheduling.RescheduleCase;
import com.clinicone.rescheduling.RescheduleCaseRepository;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import com.clinicone.schedule.CreateScheduleTemplateRequest;
import com.clinicone.schedule.ScheduleBreakRequest;
import com.clinicone.schedule.ScheduleTemplateService;
import com.clinicone.schedule.SpecialtyCatalogEntry;
import com.clinicone.schedule.SpecialtyCatalogRepository;
import com.clinicone.schedule.WorkScheduleTemplateRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Safe, idempotent local-only data so the complete staff and patient flows
 * can be exercised against a real database with rich, realistic Vietnamese clinical data.
 */
@Component
@Profile("local")
public class LocalDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDataInitializer.class);
    private static final String DEFAULT_SPECIALTY = "Khám Tổng Quát";
    private static final String DEFAULT_ROOM_CODE = "TQ-01";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    @Autowired(required = false)
    private SpecialtyCatalogRepository specialtyCatalogRepository;

    @Autowired(required = false)
    private ReasonCatalogRepository reasonCatalogRepository;

    @Autowired(required = false)
    private WorkScheduleTemplateRepository workScheduleTemplateRepository;

    @Autowired(required = false)
    private ScheduleTemplateService scheduleTemplateService;

    @Autowired(required = false)
    private DoctorTimeOffRepository doctorTimeOffRepository;

    @Autowired(required = false)
    private RescheduleCaseRepository rescheduleCaseRepository;

    @Autowired(required = false)
    private ReconciliationIncidentRepository reconciliationIncidentRepository;

    @Autowired(required = false)
    private PatientNotificationRepository patientNotificationRepository;

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

        // 1. Staff Accounts & Operational Roles
        StaffAccount admin = ensureStaff("admin", adminPassword, "Quản trị viên Hệ thống", StaffRole.ADMIN);
        StaffAccount coordinator = ensureStaff("coordinator", adminPassword, "Điều phối viên Trần Hoàng", StaffRole.COORDINATOR);
        StaffAccount receptionist = ensureStaff("reception", receptionistPassword, "Tiếp đón Lê Thu Thảo", StaffRole.RECEPTIONIST);

        // 2. Specialty Catalogs, Reason Catalogs, Medications, ICD-10, and Medical Record Templates
        ensureSpecialties();
        ensureReasonCatalog();
        ensureMedications();
        ensureDiagnoses();
        ensureTemplates();

        // 3. Rooms & 7 Specialty Doctors
        DoctorProfile docTongQuat = ensureDoctorProfile("doctor", "BS. CKII Nguyễn An", "Khám Tổng Quát", "TQ-01", "Phòng Khám Tổng Quát 01");
        DoctorProfile docTimMach = ensureDoctorProfile("doctor2", "BS. CKI Trần Minh", "Khám Tim Mạch", "TM-01", "Phòng Khám Tim Mạch 01");
        DoctorProfile docHoHap = ensureDoctorProfile("doctor3", "ThS. BS Lê Thu Hà", "Khám Hô Hấp", "HH-01", "Phòng Khám Hô Hấp 01");
        DoctorProfile docTieuHoa = ensureDoctorProfile("doctor4", "BS. CKI Phạm Quốc Dũng", "Khám Tiêu Hoá - Gan Mật", "TH-01", "Phòng Khám Tiêu Hóa 01");
        DoctorProfile docNhiKhoa = ensureDoctorProfile("doctor5", "BS. CKI Hoàng Thanh Nga", "Khám Nhi Khoa", "NK-01", "Phòng Khám Nhi Khoa 01");
        DoctorProfile docTMH = ensureDoctorProfile("doctor6", "BS. CKI Vũ Đình Toàn", "Khám Tai Mũi Họng", "TMH-01", "Phòng Khám Tai Mũi Họng 01");
        DoctorProfile docMat = ensureDoctorProfile("doctor7", "BS. CKI Đặng Mai Lan", "Khám Mắt", "MAT-01", "Phòng Khám Mắt 01");

        // Extra Clinical Support Rooms
        ensureRoom("CAP-CUU-01", "Phòng Cấp cứu & Xử trí ban đầu", "Cấp cứu");
        ensureRoom("X-QUANG-01", "Phòng Chẩn đoán hình ảnh & X-Quang", "Chẩn đoán hình ảnh");
        ensureRoom("XET-NGHIEM-01", "Phòng Xét nghiệm Hóa sinh - Huyết học", "Xét nghiệm");

        // 4. Clinic Services for each specialty
        ClinicService srvTQ = ensureOneService("Khám tổng quát ClinicOne", "Khám Tổng Quát", "Khám thường", 30, List.of(docTongQuat));
        ClinicService srvTM = ensureOneService("Khám chuyên sâu Tim mạch", "Khám Tim Mạch", "Khám chuyên khoa", 30, List.of(docTimMach));
        ClinicService srvHH = ensureOneService("Khám chuyên khoa Hô hấp", "Khám Hô Hấp", "Khám chuyên khoa", 30, List.of(docHoHap));
        ClinicService srvTH = ensureOneService("Khám Tiêu hóa & Gan mật", "Khám Tiêu Hoá - Gan Mật", "Khám chuyên khoa", 30, List.of(docTieuHoa));
        ClinicService srvNK = ensureOneService("Khám & Tư vấn Nhi khoa", "Khám Nhi Khoa", "Khám chuyên khoa", 30, List.of(docNhiKhoa));
        ClinicService srvTMH = ensureOneService("Khám & Nội soi Tai Mũi Họng", "Khám Tai Mũi Họng", "Khám chuyên khoa", 30, List.of(docTMH));
        ClinicService srvMAT = ensureOneService("Khám khúc xạ & Mắt chuyên sâu", "Khám Mắt", "Khám chuyên khoa", 30, List.of(docMat));

        // 5. Work Schedule Templates & Slot Generation for all 7 Specialty Doctors
        List<ScheduleBreakRequest> standardLunch = List.of(new ScheduleBreakRequest(LocalTime.of(12, 0), LocalTime.of(13, 0)));

        ensureScheduleTemplates(srvTQ, docTongQuat, docTongQuat.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(17, 0), standardLunch);

        ensureScheduleTemplates(srvTM, docTimMach, docTimMach.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(17, 0), standardLunch);

        ensureScheduleTemplates(srvHH, docHoHap, docHoHap.getRoom(),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                LocalTime.of(8, 0), LocalTime.of(17, 0), standardLunch);

        ensureScheduleTemplates(srvTH, docTieuHoa, docTieuHoa.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                LocalTime.of(8, 0), LocalTime.of(17, 0), standardLunch);

        ensureScheduleTemplates(srvNK, docNhiKhoa, docNhiKhoa.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(12, 0), List.of());

        ensureScheduleTemplates(srvTMH, docTMH, docTMH.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(13, 0), LocalTime.of(17, 0), List.of());

        ensureScheduleTemplates(srvMAT, docMat, docMat.getRoom(),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(17, 0), standardLunch);

        // 6. Patients & Comprehensive Family Sub-Profiles
        PatientAccount patient1 = ensurePatient("0900000001", "Nguyễn Thanh Vũ");
        PatientProfile profile1 = ensureDetailedProfile(patient1, "Nguyễn Thanh Vũ", "Bản thân", LocalDate.of(2000, 1, 1), "Nam",
                patient1.getPhone(), "079200012345", "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", true);
        PatientProfile childProfile = ensureDetailedProfile(patient1, "Bé Nguyễn Bảo Nam", "Con cái", LocalDate.of(2020, 5, 15), "Nam",
                patient1.getPhone(), null, "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);
        PatientProfile motherProfile = ensureDetailedProfile(patient1, "Nguyễn Thị Mai Lan", "Mẹ", LocalDate.of(1965, 3, 10), "Nữ",
                "0903112233", "079165009876", "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);

        PatientAccount patient2 = ensurePatient("0900000002", "Trần Thị Mai");
        PatientProfile profile2 = ensureDetailedProfile(patient2, "Trần Thị Mai", "Bản thân", LocalDate.of(1994, 8, 20), "Nữ",
                patient2.getPhone(), "079194005432", "456 Đường Lê Duẩn", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", true);
        PatientProfile fatherProfile = ensureDetailedProfile(patient2, "Trần Văn Hùng", "Bố", LocalDate.of(1955, 12, 4), "Nam",
                "0908776655", "079155001122", "456 Đường Lê Duẩn", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);

        PatientAccount patient3 = ensurePatient("0900000004", "Phạm Minh Đức");
        PatientProfile profile3 = ensureDetailedProfile(patient3, "Phạm Minh Đức", "Bản thân", LocalDate.of(1958, 11, 12), "Nam",
                patient3.getPhone(), "079158003344", "789 Đường Hai Bà Trưng", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26740", "Phường Tân Định", true);
        PatientProfile spouseProfile = ensureDetailedProfile(patient3, "Lê Thị Ngọc", "Vợ", LocalDate.of(1962, 7, 25), "Nữ",
                "0909334455", "079162007788", "789 Đường Hai Bà Trưng", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26740", "Phường Tân Định", false);

        PatientAccount patient4 = ensurePatient("0912345678", "Hoàng Văn Hải");
        PatientProfile profile4 = ensureDetailedProfile(patient4, "Hoàng Văn Hải", "Bản thân", LocalDate.of(1988, 9, 14), "Nam",
                patient4.getPhone(), "079188009988", "12 Đường Nam Kỳ Khởi Nghĩa", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26737", "Phường Nguyễn Thái Bình", true);

        // 7. Seed Past Signed Medical Records (Full Circular 33/2025/TT-BYT compliance)
        ensurePastSignedRecord(patient1, profile1, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), 14, "PAST-REC-001",
                "Đau rát họng 3 ngày, sốt nhẹ 38°C, ho khan nhiều về đêm",
                "Niêm mạc họng đỏ, amidan sung huyết nhẹ độ 1, không có giả mạc, tim phổi bình thường",
                "J00 - Viêm mũi họng cấp tính (cảm thường)",
                "Viêm đường hô hấp trên thể nhẹ",
                "Nghỉ ngơi, uống nhiều nước ấm, súc họng nước muối sinh lý",
                List.of(
                        new SeedMedication("Paracetamol 500mg (Hạ sốt, giảm đau nhanh)", "500mg", 10, "Uống 1 viên khi sốt trên 38.5°C cách 6h"),
                        new SeedMedication("Cefuroxime 500mg / Zinnat (Kháng sinh Cephalosporin thế hệ 2)", "500mg", 14, "Uống 1 viên x 2 lần/ngày sau ăn sáng/tối"),
                        new SeedMedication("Vitamin C 500mg (Chống oxy hóa, tăng sức đề kháng)", "500mg", 10, "Uống 1 viên/ngày sau ăn sáng")
                ));

        ensurePastSignedRecord(patient3, profile3, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), 7, "PAST-REC-002",
                "Kiểm tra huyết áp định kỳ, thỉnh thoảng có cảm giác hồi hộp nhẹ",
                "Huyết áp 145/90 mmHg, nhịp tim 76 ck/phút, phổi trong không ran",
                "I10 - Tăng huyết áp vô căn (nguyên phát)",
                "Tăng huyết áp độ 1 có kiểm soát",
                "Duy trì thuốc hạ áp hàng ngày, ăn nhạt, hạn chế muối",
                List.of(
                        new SeedMedication("Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)", "5mg", 30, "Uống 1 viên vào 8h sáng hàng ngày"),
                        new SeedMedication("Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)", "50mg", 30, "Uống 1 viên vào 8h sáng hàng ngày")
                ));

        ensurePastSignedRecord(patient2, profile2, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), 5, "PAST-REC-003",
                "Đau âm ỉ vùng thượng vị sau ăn, ợ hơi, cồn cào",
                "Bụng mềm, ấn tức nhẹ thượng vị, không đề kháng thành bụng",
                "K21 - Bệnh trào ngược dạ dày - thực quản (GERD)",
                "Viêm dạ dày kết hợp trào ngược thực quản độ A",
                "Uống thuốc trước ăn sáng 30 phút, kiêng đồ chua cay, cà phê, không nằm ngay sau ăn",
                List.of(
                        new SeedMedication("Nexium 40mg / Esomeprazole (Ức chế tiết acid dạ dày, trào ngược GERD)", "40mg", 28, "Uống 1 viên trước ăn sáng 30 phút"),
                        new SeedMedication("Phosphalugel (Gel chữ P trung hòa acid dạ dày, giảm ợ chua)", "Gói gel 20g", 20, "Uống 1 gói khi đau hoặc sau ăn 2 giờ")
                ));

        ensurePastSignedRecord(patient1, childProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), 3, "PAST-REC-004",
                "Bé ho húng hắng có đờm, chảy mũi trong, không sốt, ăn uống bình thường",
                "Họng đỏ nhẹ, không rale phổi, tai hai bên sạch",
                "J20 - Viêm phế quản cấp tính",
                "Viêm phế quản cấp thể nhẹ ở trẻ em",
                "Rửa mũi bằng nước muối sinh lý ấm, giữ ấm cổ ngực, uống nhiều nước ấm",
                List.of(
                        new SeedMedication("Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)", "200mg", 10, "Pha 1 gói với 50ml nước ấm x 2 lần/ngày"),
                        new SeedMedication("Natri Clorid 0.9% 500ml (Nước muối sinh lý đẳng trương súc họng, rửa mũi)", "500ml", 2, "Nhỏ và rửa mũi 3 lần/ngày")
                ));

        // 8. Seed Today's Active Clinic Queue Tickets Across Multiple Rooms
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        ensureActiveQueueScenario(patient1, profile1, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), today, 1, LocalTime.of(8, 30), "TODAY-TQ01-001", QueueScenario.IN_SERVICE, "Khám sức khỏe tổng quát định kỳ");
        ensureActiveQueueScenario(patient2, profile2, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), today, 2, LocalTime.of(9, 0), "TODAY-TQ01-002", QueueScenario.WAITING, "Kiểm tra sức khỏe tổng quát");
        ensureActiveQueueScenario(patient3, profile3, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), today, 3, LocalTime.of(9, 30), "TODAY-TQ01-003", QueueScenario.PRIORITY_WAITING, "Khám kiểm tra định kỳ người cao tuổi");
        ensureActiveQueueScenario(patient1, childProfile, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), today, 4, LocalTime.of(8, 0), "TODAY-TQ01-004", QueueScenario.COMPLETED, "Khám tổng quát học đường");

        ensureActiveQueueScenario(patient2, fatherProfile, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), today, 1, LocalTime.of(8, 30), "TODAY-TM01-001", QueueScenario.IN_SERVICE, "Tái khám theo dõi tăng huyết áp và đau ngực");
        ensureActiveQueueScenario(patient1, motherProfile, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), today, 1, LocalTime.of(9, 0), "TODAY-HH01-001", QueueScenario.WAITING, "Ho khan kéo dài và tức ngực khi thay đổi thời tiết");
        ensureActiveQueueScenario(patient4, profile4, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), today, 1, LocalTime.of(9, 30), "TODAY-TMH01-001", QueueScenario.WAITING, "Nội soi kiểm tra viêm xoang và nghẹt mũi");

        // 9. Seed Future Booked Appointments (For patient appointment lists and booking tests)
        ensureFutureAppointment(patient1, profile1, docTimMach, today.plusDays(2), LocalTime.of(9, 0), "FUT-2026-TM01", "Tư vấn và kiểm tra tim mạch chuyên sâu");
        ensureFutureAppointment(patient1, motherProfile, docHoHap, today.plusDays(3), LocalTime.of(10, 0), "FUT-2026-HH01", "Tái khám kiểm tra chức năng hô hấp");
        ensureFutureAppointment(patient2, profile2, docMat, today.plusDays(4), LocalTime.of(14, 0), "FUT-2026-MAT01", "Khám kiểm tra thị lực và đo khúc xạ mắt");
        ensureFutureAppointment(patient4, profile4, docTieuHoa, today.plusDays(5), LocalTime.of(15, 0), "FUT-2026-TH01", "Khám tầm soát chức năng gan mật và dạ dày");

        // 10. Seed Doctor Time Off & Open Reschedule Case
        ensureRescheduleScenario(patient1, profile1, docHoHap, today.plusDays(1), LocalTime.of(8, 30), "RES-CASE-001");

        // 11. Seed Reconciliation Incidents (For Coordinator & Admin Reconciliation Workspace)
        ensureReconciliationIncidents();

        // 12. Seed Patient Notifications
        ensureNotifications(patient1);

        log.info("Local bootstrap complete: 10 staff accounts, 7 specialty doctors, 10 rooms, 7 services, 8 patient profiles, active queue tickets, templates, catalogs, and notifications successfully initialized");
    }

    private void ensureSpecialties() {
        if (specialtyCatalogRepository == null) return;
        List<String[]> list = List.of(
                new String[]{"NOI", "Khám Tổng Quát", "Khám sức khỏe tổng quát, tầm soát và điều trị các bệnh nội khoa phổ biến."},
                new String[]{"TIM", "Khám Tim Mạch", "Chẩn đoán và điều trị tăng huyết áp, bệnh mạch vành, rối loạn nhịp tim."},
                new String[]{"HO_HAP", "Khám Hô Hấp", "Khám và điều trị viêm phế quản, hen suyễn, viêm phổi, COPD."},
                new String[]{"TIEU_HOA", "Khám Tiêu Hoá - Gan Mật", "Khám dạ dày, trào ngược thực quản GERD, viêm gan, đại tràng."},
                new String[]{"NHI", "Khám Nhi Khoa", "Khám và tư vấn sức khỏe dinh dưỡng, tiêm chủng cho trẻ em."},
                new String[]{"TMH", "Khám Tai Mũi Họng", "Nội soi chẩn đoán viêm xoang, viêm tai giữa, viêm amidan."},
                new String[]{"MAT", "Khám Mắt", "Đo khúc xạ, khám tật khúc xạ và các bệnh lý về mắt."}
        );
        for (String[] item : list) {
            if (!specialtyCatalogRepository.existsByCodeIgnoreCase(item[0])) {
                specialtyCatalogRepository.save(SpecialtyCatalogEntry.create(item[0], item[1], item[2]));
            }
        }
    }

    private void ensureReasonCatalog() {
        if (reasonCatalogRepository == null) return;
        List<Object[]> list = List.of(
                new Object[]{ReasonCatalogType.APPOINTMENT_CANCELLATION, "RSN-CAN-01", "Thay đổi kế hoạch công tác / Bận việc đột xuất"},
                new Object[]{ReasonCatalogType.APPOINTMENT_CANCELLATION, "RSN-CAN-02", "Đã khỏi bệnh hoặc tự chăm sóc theo dõi tại nhà"},
                new Object[]{ReasonCatalogType.APPOINTMENT_CANCELLATION, "RSN-CAN-03", "Muốn đăng ký chuyển sang chuyên khoa khám khác"},
                new Object[]{ReasonCatalogType.APPOINTMENT_CANCELLATION, "RSN-CAN-04", "Khung giờ bận, sẽ đặt lại vào dịp thuận tiện hơn"},
                new Object[]{ReasonCatalogType.APPOINTMENT_CANCELLATION, "RSN-CAN-05", "Lý do cá nhân / Gia đình có việc riêng"},

                new Object[]{ReasonCatalogType.RECEPTION_EXCEPTION, "RSN-REC-01", "Người bệnh đến muộn quá giờ hẹn khám"},
                new Object[]{ReasonCatalogType.RECEPTION_EXCEPTION, "RSN-REC-02", "Bác sĩ phụ trách có ca mổ khẩn cấp hoặc bận đột xuất"},
                new Object[]{ReasonCatalogType.RECEPTION_EXCEPTION, "RSN-REC-03", "Bảo trì nâng cấp trang thiết bị phòng khám"},

                new Object[]{ReasonCatalogType.QUEUE_EXCEPTION, "RSN-QUE-01", "Người bệnh chưa có mặt khi gọi số lượt khám"},
                new Object[]{ReasonCatalogType.QUEUE_EXCEPTION, "RSN-QUE-02", "Chuyển tuyến điều trị / Cấp cứu khẩn cấp"},
                new Object[]{ReasonCatalogType.QUEUE_EXCEPTION, "RSN-QUE-03", "Người bệnh xin tạm hoãn chờ người nhà hỗ trợ"},

                new Object[]{ReasonCatalogType.RECONCILIATION, "RSN-REC-01", "Đối soát và điều chỉnh lệch trạng thái lịch hẹn"},
                new Object[]{ReasonCatalogType.RECONCILIATION, "RSN-REC-02", "Phát hiện trùng lịch do ngoại lệ mạng hoặc đồng bộ chậm"},
                new Object[]{ReasonCatalogType.RECONCILIATION, "RSN-REC-03", "Báo nhầm hồ sơ và hoàn tác phiên khám"}
        );
        for (Object[] item : list) {
            ReasonCatalogType type = (ReasonCatalogType) item[0];
            String code = (String) item[1];
            String label = (String) item[2];
            if (!reasonCatalogRepository.existsByTypeAndCodeIgnoreCase(type, code)) {
                reasonCatalogRepository.save(ReasonCatalog.create(type, code, label));
            }
        }
    }

    private void ensureMedications() {
        if (medicationRepository == null) return;
        List<String[]> meds = List.of(
                // 1. Giảm đau, Hạ sốt, Kháng viêm NSAID
                new String[]{"MED-PARA-500", "Paracetamol 500mg (Hạ sốt, giảm đau nhanh)"},
                new String[]{"MED-PARA-650", "Hapacol 650mg / Paracetamol 650mg (Hạ sốt, giảm đau liều cao)"},
                new String[]{"MED-EFFER-500", "Efferalgan 500mg (Viên sủi hạ sốt, giảm đau)"},
                new String[]{"MED-IBUP-400", "Ibuprofen 400mg (Kháng viêm, giảm đau NSAID)"},
                new String[]{"MED-MELO-15", "Meloxicam 15mg (Kháng viêm giảm đau thoái hóa khớp)"},
                new String[]{"MED-CELE-200", "Celecoxib 200mg (Kháng viêm chọn lọc COX-2, êm dạ dày)"},
                new String[]{"MED-DICLO-50", "Diclofenac 50mg / Voltaren (Giảm đau kháng viêm cơ xương khớp)"},
                new String[]{"MED-ULTRA-375", "Ultracet / Tramadol 37.5mg + Paracetamol 325mg (Giảm đau mức độ vừa-nặng)"},

                // 2. Kháng sinh & Kháng khuẩn
                new String[]{"MED-AUGM-1000", "Augmentin 1g / Amoxicillin + Acid Clavulanic 1000mg (Kháng sinh phổ rộng)"},
                new String[]{"MED-AMOX-500", "Amoxicillin 500mg (Kháng sinh nhóm Penicillin)"},
                new String[]{"MED-CEFU-500", "Cefuroxime 500mg / Zinnat (Kháng sinh Cephalosporin thế hệ 2)"},
                new String[]{"MED-CEFI-200", "Cefixime 200mg (Kháng sinh Cephalosporin thế hệ 3 đường uống)"},
                new String[]{"MED-AZIT-500", "Azithromycin 500mg (Kháng sinh Macrolide trị nhiễm khuẩn hô hấp)"},
                new String[]{"MED-KLAC-500", "Klacid 500mg / Clarithromycin 500mg (Kháng sinh Macrolide hô hấp & HP)"},
                new String[]{"MED-CIPRO-500", "Ciprofloxacin 500mg (Kháng sinh Quinolone tiết niệu & tiêu hóa)"},
                new String[]{"MED-LEVO-500", "Levofloxacin 500mg / Cravit (Kháng sinh Fluoroquinolone hô hấp nặng)"},
                new String[]{"MED-METRO-250", "Metronidazol 250mg (Kháng khuẩn kỵ khí & ký sinh trùng tiêu hóa)"},

                // 3. Kháng viêm Corticoid & Chống dị ứng
                new String[]{"MED-MEDROL-16", "Medrol 16mg / Methylprednisolone (Kháng viêm Corticoid mạnh)"},
                new String[]{"MED-MEDROL-4", "Medrol 4mg / Methylprednisolone (Kháng viêm Corticoid)"},
                new String[]{"MED-PRED-5", "Prednisolone 5mg (Kháng viêm dị ứng, miễn dịch)"},
                new String[]{"MED-LORA-10", "Loratadine 10mg (Kháng Histamin H1, chống dị ứng thế hệ 2)"},
                new String[]{"MED-CETI-10", "Cetirizine 10mg (Chống dị ứng, viêm mũi dị ứng, ngứa)"},
                new String[]{"MED-TELFAST-180", "Telfast 180mg / Fexofenadine 180mg (Chống dị ứng mề đay, không gây buồn ngủ)"},

                // 4. Tiêu hóa, Dạ dày & Gan mật
                new String[]{"MED-NEXIUM-40", "Nexium 40mg / Esomeprazole (Ức chế tiết acid dạ dày, trào ngược GERD)"},
                new String[]{"MED-OMEP-20", "Omeprazole 20mg (Ức chế bơm Proton, viêm loét dạ dày)"},
                new String[]{"MED-PANTO-40", "Pantoprazole 40mg (Bảo vệ niêm mạc, giảm acid dạ dày)"},
                new String[]{"MED-PHOSPHO-P", "Phosphalugel (Gel chữ P trung hòa acid dạ dày, giảm ợ chua)"},
                new String[]{"MED-DOMPER-10", "Domperidone 10mg / Motilium (Chống nôn, đầy hơi, chậm tiêu)"},
                new String[]{"MED-BERB-100", "Berberin 100mg (Kháng khuẩn đường ruột, điều trị tiêu chảy)"},
                new String[]{"MED-SMECTA-3G", "Smecta 3g (Bột pha bảo vệ niêm mạc ruột tiêu chảy cấp)"},
                new String[]{"MED-ENTERO-5ML", "Enterogermina (Men vi sinh dạng ống uống cân bằng hệ vi sinh)"},
                new String[]{"MED-SILY-140", "Silymarin 140mg (Bổ gan, tăng cường chức năng giải độc gan)"},

                // 5. Tim mạch, Huyết áp & Mỡ máu
                new String[]{"MED-AMLO-5", "Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)"},
                new String[]{"MED-LOSA-50", "Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)"},
                new String[]{"MED-MICAR-40", "Micardis 40mg / Telmisartan 40mg (Hạ huyết áp bảo vệ tim thận)"},
                new String[]{"MED-CONCOR-25", "Concor 2.5mg / Bisoprolol (Hạ huyết áp, kiểm soát nhịp tim chẹn Beta)"},
                new String[]{"MED-LIPITOR-20", "Lipitor 20mg / Atorvastatin 20mg (Hạ mỡ máu, giảm Cholesterol xấu LDL)"},
                new String[]{"MED-CRESTOR-10", "Crestor 10mg / Rosuvastatin 10mg (Hạ lipid máu thế hệ mới)"},
                new String[]{"MED-ASPIRIN-81", "Aspirin 81mg (Chống kết tập tiểu cầu, phòng ngừa huyết khối tim mạch)"},

                // 6. Nội tiết & Tiểu đường
                new String[]{"MED-GLUCO-850", "Glucophage 850mg / Metformin (Hạ đường huyết Type 2 an toàn)"},
                new String[]{"MED-METF-500", "Metformin 500mg (Hạ đường huyết đái tháo đường)"},
                new String[]{"MED-DIAMIC-60", "Diamicron MR 60mg / Gliclazide (Kích thích tiết insulin kéo dài)"},

                // 7. Hô hấp, Hen suyễn & Giảm ho
                new String[]{"MED-VENTO-INH", "Ventolin Inhaler 100mcg (Ống hít cắt cơn hen phế quản cấp)"},
                new String[]{"MED-SALB-2", "Salbutamol 2mg (Giãn phế quản đường uống)"},
                new String[]{"MED-ACETYL-200", "Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)"},
                new String[]{"MED-AMBROX-30", "Ambroxol 30mg / Mucosolvan (Tiêu đờm, giảm ho có đờm)"},

                // 8. Bổ não, Khớp & Vi chất dinh dưỡng
                new String[]{"MED-GINKGO-120", "Ginkgo Biloba 120mg / Tanakan (Tăng cường tuần hoàn máu não, giảm chóng mặt)"},
                new String[]{"MED-GLUC-1500", "Glucosamine Sulfate 1500mg (Tái tạo sụn khớp, giảm đau thoái hóa)"},
                new String[]{"MED-CALCI-D3", "Calci Nano + Vitamin D3 (Bổ sung Canxi, phòng ngừa loãng xương)"},
                new String[]{"MED-VITC-500", "Vitamin C 500mg (Chống oxy hóa, tăng sức đề kháng)"},
                new String[]{"MED-VIT-3B", "Vitamin 3B (B1 - B6 - B12) (Bổ thần kinh, giảm đau nhức tê bì chân tay)"},

                // 9. Tai mũi họng & Mắt
                new String[]{"MED-TOBR-03", "Tobramycin 0.3% / Tobrex (Thuốc nhỏ mắt kháng khuẩn)"},
                new String[]{"MED-OTRIVIN-01", "Otrivin 0.1% / Xylometazoline (Thuốc xịt mũi thông mũi, giảm nghẹt cấp)"},
                new String[]{"MED-NACL-09", "Natri Clorid 0.9% 500ml (Nước muối sinh lý đẳng trương súc họng, rửa mũi)"}
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
                // Hô hấp
                new String[]{"J00", "J00 - Viêm mũi họng cấp tính (cảm thường)"},
                new String[]{"J01", "J01 - Viêm xoang cấp tính"},
                new String[]{"J02", "J02 - Viêm họng cấp tính"},
                new String[]{"J03", "J03 - Viêm amiđan cấp tính"},
                new String[]{"J04", "J04 - Viêm thanh quản và khí quản cấp tính"},
                new String[]{"J06", "J06 - Nhiễm khuẩn hô hấp trên cấp tính ở nhiều vị trí"},
                new String[]{"J15", "J15 - Viêm phổi do vi khuẩn"},
                new String[]{"J18", "J18 - Viêm phổi không đặc hiệu"},
                new String[]{"J20", "J20 - Viêm phế quản cấp tính"},
                new String[]{"J44", "J44 - Bệnh phổi tắc nghẽn mạn tính (COPD)"},
                new String[]{"J45", "J45 - Hen phế quản (Suyễn)"},

                // Tim mạch & Tuần hoàn
                new String[]{"I10", "I10 - Tăng huyết áp vô căn (nguyên phát)"},
                new String[]{"I11", "I11 - Bệnh tim do tăng huyết áp"},
                new String[]{"I20", "I20 - Cơn đau thắt ngực (thiếu máu cơ tim)"},
                new String[]{"I25", "I25 - Bệnh tim thiếu máu cục bộ mạn tính"},
                new String[]{"I48", "I48 - Rung nhĩ và cuồng nhĩ"},
                new String[]{"I50", "I50 - Suy tim"},
                new String[]{"I83", "I83 - Giãn tĩnh mạch chi dưới"},

                // Tiêu hóa & Gan mật
                new String[]{"K21", "K21 - Bệnh trào ngược dạ dày - thực quản (GERD)"},
                new String[]{"K25", "K25 - Loét dạ dày"},
                new String[]{"K26", "K26 - Loét tá tràng"},
                new String[]{"K29", "K29 - Viêm dạ dày và tá tràng"},
                new String[]{"K30", "K30 - Chứng khó tiêu chức năng"},
                new String[]{"K58", "K58 - Hội chứng ruột kích thích (IBS)"},
                new String[]{"K59", "K59 - Táo bón mạn tính"},
                new String[]{"K76.0", "K76.0 - Gan nhiễm mỡ không do rượu"},
                new String[]{"K80", "K80 - Sỏi mật"},

                // Nội tiết, Chuyển hóa & Tiết niệu
                new String[]{"E11", "E11 - Đái tháo đường không phụ thuộc insulin (Type 2)"},
                new String[]{"E78", "E78 - Rối loạn chuyển hóa lipoprotein và tăng lipid máu"},
                new String[]{"E79", "E79 - Tăng acid uric máu / Bệnh Gút"},
                new String[]{"E03", "E03 - Suy tuyến giáp khác"},
                new String[]{"E05", "E05 - Nhiễm độc giáp (Cường giáp)"},
                new String[]{"N39.0", "N39.0 - Nhiễm trùng đường tiết niệu vị trí không xác định"},

                // Cơ xương khớp
                new String[]{"M17", "M17 - Thoái hóa khớp gối"},
                new String[]{"M19", "M19 - Thoái hóa các khớp khác"},
                new String[]{"M54.5", "M54.5 - Đau vùng thắt lưng"},
                new String[]{"M54.2", "M54.2 - Đau vùng cổ và vai gáy"},
                new String[]{"M10", "M10 - Bệnh Gút cấp và mạn tính"},
                new String[]{"M81", "M81 - Loãng xương không kèm gãy xương bệnh lý"},

                // Thần kinh & Rối loạn chức năng
                new String[]{"G43", "G43 - Đau nửa đầu (Migraine)"},
                new String[]{"G44", "G44 - Các hội chứng đau đầu khác (Đau đầu căng thẳng)"},
                new String[]{"G47", "G47 - Rối loạn giấc ngủ (Mất ngủ)"},
                new String[]{"H81", "H81 - Rối loạn chức năng tiền đình (Chóng mặt tiền đình)"},

                // Da liễu, Mắt, Tai Mũi Họng
                new String[]{"H10", "H10 - Viêm kết mạc (Đau mắt đỏ)"},
                new String[]{"H52", "H52 - Tật khúc xạ và điều tiết (Cận/loạn/viễn thị)"},
                new String[]{"H66", "H66 - Viêm tai giữa có mủ và không mủ"},
                new String[]{"L20", "L20 - Viêm da cơ địa (Eczema)"},
                new String[]{"L50", "L50 - Mày đay dị ứng"},

                // Khám tổng quát & Tầm soát
                new String[]{"Z00.0", "Z00.0 - Khám sức khỏe tổng quát định kỳ"},
                new String[]{"Z01.0", "Z01.0 - Khám và kiểm tra mắt và thị lực"},
                new String[]{"Z71.3", "Z71.3 - Tư vấn và giám sát chế độ ăn kiêng"}
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

        ensureOneTemplate("TMPL-TM-01", "Mẫu khám Tăng huyết áp & Tim mạch", "Khám Tim Mạch",
                "Mẫu chuẩn theo dõi và điều trị tăng huyết áp nguyên phát",
                """
                {"reason":"Đo kiểm tra huyết áp định kỳ, thỉnh thoảng hơi căng tức thái dương","examinationNotes":"Huyết áp đo tại phòng khám 140/85 mmHg, mạch 76 lần/phút, tim T1 T2 đều rõ, không phù chi dưới, không ran phổi.","diagnosis":"I10 - Tăng huyết áp vô căn (nguyên phát)","conclusion":"Tăng huyết áp độ 1 giai đoạn ổn định","treatmentPlan":"Duy trì thuốc hạ áp hàng ngày vào buổi sáng, chế độ ăn giảm muối, hạn chế dầu mỡ và thức uống có cồn.","followUpDays":30,"followUpNote":"Tái khám đo lại huyết áp và đánh giá chức năng sau 1 tháng"}
                """);

        ensureOneTemplate("TMPL-HH-01", "Mẫu khám viêm đường hô hấp / Cảm cúm", "Khám Hô Hấp",
                "Mẫu chuẩn viêm mũi họng, cảm cúm, ho khan sốt nhẹ",
                """
                {"reason":"Đau rát họng, ho húng hắng, sốt nhẹ 38°C, nghẹt mũi","examinationNotes":"Niêm mạc họng đỏ, amidan sung huyết nhẹ không giả mạc, mũi xuất tiết dịch trong, phổi thông khí tốt không rale.","diagnosis":"J00 - Viêm mũi họng cấp tính (cảm thường)","conclusion":"Viêm đường hô hấp trên cấp tính thể nhẹ","treatmentPlan":"Nghỉ ngơi, súc họng nước muối sinh lý 3 lần/ngày, giữ ấm cổ ngực, uống nhiều nước ấm.","followUpDays":7,"followUpNote":"Tái khám sau 7 ngày nếu còn sốt cao hoặc ho kéo dài"}
                """);

        ensureOneTemplate("TMPL-TH-01", "Mẫu khám Dạ dày - GERD - Gan mật", "Khám Tiêu Hoá - Gan Mật",
                "Mẫu chuẩn viêm dạ dày, trào ngược dạ dày thực quản",
                """
                {"reason":"Đau âm ỉ vùng thượng vị sau ăn, ợ hơi ợ chua, cồn cào","examinationNotes":"Bụng mềm, ấn tức nhẹ vùng thượng vị, không đề kháng thành bụng, gan lách không to.","diagnosis":"K21 - Bệnh trào ngược dạ dày - thực quản (GERD)","conclusion":"Viêm dạ dày kết hợp trào ngược thực quản","treatmentPlan":"Uống thuốc bảo vệ niêm mạc trước ăn sáng 30 phút, ăn đúng giờ, không ăn no sát giờ ngủ, tránh thức ăn cay nóng, cà phê.","followUpDays":14,"followUpNote":"Tái khám sau 2 tuần để đánh giá đáp ứng điều trị"}
                """);

        ensureOneTemplate("TMPL-NK-01", "Mẫu khám Nhi khoa & Dinh dưỡng", "Khám Nhi Khoa",
                "Mẫu khám nhi khoa tổng quát và viêm đường hô hấp trẻ em",
                """
                {"reason":"Trẻ ho, sổ mũi, quấy khóc nhẹ, không sốt cao","examinationNotes":"Tri giác tỉnh, họng đỏ nhẹ, không rale phổi, bụng mềm, thóp phẳng, dinh dưỡng cân đối.","diagnosis":"J20 - Viêm phế quản cấp tính","conclusion":"Viêm phế quản cấp thể nhẹ ở trẻ em","treatmentPlan":"Vệ sinh mũi họng bằng nước muối sinh lý, uống nhiều nước, giữ ấm, theo dõi nhịp thở.","followUpDays":5,"followUpNote":"Tái khám ngay nếu trẻ sốt cao li bì hoặc thở nhanh co lõm ngực"}
                """);

        ensureOneTemplate("TMPL-TMH-01", "Mẫu khám & Nội soi Tai Mũi Họng", "Khám Tai Mũi Họng",
                "Mẫu chuẩn nội soi và chẩn đoán bệnh lý Tai Mũi Họng",
                """
                {"reason":"Nghẹt mũi 2 bên, chảy mũi trong, ngứa mũi hắt hơi","examinationNotes":"Nội soi TMH: Cuống mũi phù nề nhợt màu, xuất tiết dịch nhầy trong, vách ngăn không vẹo, họng sạch.","diagnosis":"J01 - Viêm xoang cấp tính","conclusion":"Viêm mũi xoang dị ứng cấp","treatmentPlan":"Xịt mũi nước muối ưu trương, tránh tiếp xúc khói bụi phấn hoa, dùng kháng histamin khi cần.","followUpDays":10,"followUpNote":"Tái khám sau 10 ngày để nội soi kiểm tra lại"}
                """);

        ensureOneTemplate("TMPL-MAT-01", "Mẫu khám Mắt & Đo khúc xạ", "Khám Mắt",
                "Mẫu chuẩn kiểm tra thị lực, khúc xạ và viêm kết mạc",
                """
                {"reason":"Mắt mờ khi nhìn xa, mỏi mắt khi làm việc máy tính","examinationNotes":"Thị lực không kính: Mắt phải 3/10, Mắt trái 4/10. Khúc xạ có kính: Mắt phải 10/10 (-1.50D), Mắt trái 10/10 (-1.25D). Kết mạc hồng, giác mạc trong.","diagnosis":"H52 - Tật khúc xạ và điều tiết (Cận/loạn/viễn thị)","conclusion":"Cận thị hai mắt đơn thuần","treatmentPlan":"Đeo kính đúng độ khi làm việc và học tập, nghỉ ngơi quy tắc 20-20-20, nhỏ nước mắt nhân tạo khi mỏi mắt.","followUpDays":180,"followUpNote":"Kiểm tra lại độ khúc xạ sau 6 tháng"}
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

    private DoctorProfile ensureDoctorProfile(String username, String fullName, String specialty, String roomCode, String roomName) {
        String avatarUrl = switch (username.toLowerCase()) {
            case "doctor" -> "https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150&auto=format&fit=crop&q=80";
            case "doctor2" -> "https://images.unsplash.com/photo-1594824813589-32212356c382?w=150&auto=format&fit=crop&q=80";
            case "doctor3" -> "https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=150&auto=format&fit=crop&q=80";
            case "doctor4" -> "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=150&auto=format&fit=crop&q=80";
            case "doctor5" -> "https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=150&auto=format&fit=crop&q=80";
            case "doctor6" -> "https://images.unsplash.com/photo-1582750433449-648ed127bb54?w=150&auto=format&fit=crop&q=80";
            case "doctor7" -> "https://images.unsplash.com/photo-1651008376811-b90baee60c1f?w=150&auto=format&fit=crop&q=80";
            default -> "https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150&auto=format&fit=crop&q=80";
        };
        StaffAccount staff = ensureStaff(username, doctorPassword, fullName, StaffRole.DOCTOR);
        DoctorProfile docProfile = profileRepository.findByStaffAccount_Id(staff.getId()).orElse(null);
        ClinicRoom room = ensureRoom(roomCode, roomName, specialty);
        if (docProfile == null) {
            docProfile = profileRepository.save(DoctorProfile.create(staff, specialty, room, avatarUrl));
        } else {
            docProfile.updateAssignment(specialty, room, avatarUrl);
            docProfile = profileRepository.save(docProfile);
        }
        ensureWeekdaySchedules(docProfile);
        return docProfile;
    }

    private ClinicRoom ensureRoom(String code, String name, String specialty) {
        ClinicRoom room = roomRepository.findAllByOrderByCodeAsc().stream()
                .filter(item -> item.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
        if (room == null) {
            return roomRepository.save(ClinicRoom.create(code, name, specialty));
        }
        room.update(code, name, specialty);
        room.setActive(true);
        return roomRepository.save(room);
    }

    private ClinicService ensureOneService(String serviceName, String specialty, String visitType, int duration, List<DoctorProfile> doctors) {
        ClinicService service = clinicServiceRepository.findAllByOrderByNameAsc().stream()
                .filter(item -> item.getName().equalsIgnoreCase(serviceName))
                .findFirst()
                .orElse(null);
        if (service == null) {
            return clinicServiceRepository.save(ClinicService.create(
                    serviceName, specialty, visitType, duration, doctors));
        }
        service.update(serviceName, specialty, visitType, duration, doctors);
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

    private void ensureScheduleTemplates(ClinicService service, DoctorProfile doctorProfile, ClinicRoom room,
                                        Set<DayOfWeek> weekdays, LocalTime dayStart, LocalTime dayEnd,
                                        List<ScheduleBreakRequest> breaks) {
        if (scheduleTemplateService == null || workScheduleTemplateRepository == null) return;
        boolean exists = workScheduleTemplateRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .anyMatch(t -> t.getDoctorProfile().getId().equals(doctorProfile.getId())
                        && t.getClinicService().getId().equals(service.getId()));
        if (exists) return;

        LocalDate startDate = LocalDate.now(CLINIC_ZONE);
        LocalDate endDate = startDate.plusDays(45);
        try {
            scheduleTemplateService.create(new CreateScheduleTemplateRequest(
                    service.getId(),
                    doctorProfile.getStaffAccount().getId(),
                    room.getId(),
                    startDate,
                    endDate,
                    weekdays,
                    dayStart,
                    dayEnd,
                    30,
                    breaks,
                    Set.of()
            ));
        } catch (Exception e) {
            log.warn("Could not create schedule template for doctor {}: {}", doctorProfile.getStaffAccount().getFullName(), e.getMessage());
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

    private PatientProfile ensureDetailedProfile(PatientAccount patient, String fullName, String relationship,
                                                 LocalDate dob, String gender, String phone, String nationalId,
                                                 String streetAddress, String provinceCode, String provinceName,
                                                 String districtCode, String districtName, String wardCode,
                                                 String wardName, boolean isPrimary) {
        String fullAddress = streetAddress + ", " + wardName + ", " + districtName + ", " + provinceName;
        return patientProfileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(patient.getId())
                .stream().filter(p -> p.getFullName().equalsIgnoreCase(fullName)).findFirst()
                .orElseGet(() -> patientProfileRepository.save(PatientProfile.create(
                        patient, fullName, relationship, dob, gender, phone, nationalId, "Việt Nam", "Kinh",
                        fullAddress, provinceCode, provinceName, districtCode, districtName, wardCode, wardName,
                        streetAddress, isPrimary)));
    }

    private void ensurePastSignedRecord(PatientAccount patient, PatientProfile profile, StaffAccount doctor,
                                        DoctorProfile doctorProfile, ClinicRoom room, int daysAgo, String code,
                                        String reason, String notes, String diagnosis, String conclusion, String plan,
                                        List<SeedMedication> meds) {
        LocalDate pastDate = LocalDate.now(CLINIC_ZONE).minusDays(daysAgo);
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
            List<PrescriptionLine> lines = new ArrayList<>();
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
                                           int queueNum, LocalTime time, String code, QueueScenario scenario, String reason) {
        if (appointmentRepository.findByAppointmentCode(code).isPresent()
                || appointmentRepository.existsByPatientIdAndAppointmentDateAndStartTime(patient.getId(), date, time)) {
            return;
        }

        Appointment app = appointmentRepository.save(Appointment.create(patient, doctor.getId(), profile, code,
                doctorProfile.getSpecialty(), doctor.getFullName(), date, time, reason));
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
                record.sign(doctor.getFullName(), reason, "Thể trạng bình thường", "Z00.0 - Khám sức khỏe tổng quát",
                        "Sức khỏe tốt", "Tập thể dục đều đặn", null, null, List.of(), null, null);
                medicalRecordRepository.save(record);
            }
        }
    }

    private void ensureFutureAppointment(PatientAccount patient, PatientProfile profile, DoctorProfile doctorProfile,
                                         LocalDate date, LocalTime time, String code, String reason) {
        if (appointmentRepository.findByAppointmentCode(code).isPresent()
                || appointmentRepository.existsByPatientIdAndAppointmentDateAndStartTime(patient.getId(), date, time)) {
            return;
        }
        appointmentRepository.save(Appointment.create(patient, doctorProfile.getStaffAccount().getId(), profile,
                code, doctorProfile.getSpecialty(), doctorProfile.getStaffAccount().getFullName(), date, time, reason));
    }

    private void ensureRescheduleScenario(PatientAccount patient, PatientProfile profile, DoctorProfile doc,
                                          LocalDate targetDate, LocalTime targetTime, String appCode) {
        if (doctorTimeOffRepository == null || rescheduleCaseRepository == null) return;
        if (appointmentRepository.findByAppointmentCode(appCode).isPresent()) return;

        Appointment app = appointmentRepository.save(Appointment.create(patient, doc.getStaffAccount().getId(), profile,
                appCode, doc.getSpecialty(), doc.getStaffAccount().getFullName(), targetDate, targetTime,
                "Khám kiểm tra hô hấp định kỳ"));

        boolean hasTimeOff = doctorTimeOffRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .anyMatch(t -> t.getDoctorProfile().getId().equals(doc.getId()));
        if (!hasTimeOff) {
            doctorTimeOffRepository.save(DoctorTimeOff.create(doc, targetDate, targetDate.plusDays(1),
                    "Bác sĩ tham dự hội nghị Hô hấp toàn quốc"));
        }

        if (rescheduleCaseRepository.findByAppointmentIdAndStatus(app.getId(), com.clinicone.rescheduling.RescheduleCaseStatus.OPEN).isEmpty()) {
            rescheduleCaseRepository.save(RescheduleCase.open(app,
                    "Bác sĩ phụ trách có lịch công tác đột xuất; vui lòng chọn khung giờ thay thế."));
        }
    }

    private void ensureReconciliationIncidents() {
        if (reconciliationIncidentRepository == null) return;
        boolean inc1Exists = reconciliationIncidentRepository.findAll().stream()
                .anyMatch(i -> "INC-2026-001".equalsIgnoreCase(i.getIncidentCode()));
        if (!inc1Exists) {
            ReconciliationIncident openInc = ReconciliationIncident.open("INC-2026-001", "APPOINTMENT",
                    java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                    "Phát hiện trùng lịch do cập nhật ngoại lệ thời gian từ bên thứ ba", "coordinator");
            reconciliationIncidentRepository.save(openInc);
        }

        boolean inc2Exists = reconciliationIncidentRepository.findAll().stream()
                .anyMatch(i -> "INC-2026-002".equalsIgnoreCase(i.getIncidentCode()));
        if (!inc2Exists) {
            ReconciliationIncident closedInc = ReconciliationIncident.open("INC-2026-002", "EXAMINATION",
                    java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                    "Báo nhầm hồ sơ người bệnh tại phòng khám Tai Mũi Họng", "coordinator");
            closedInc.close(ReconciliationAction.RETRY_BUSINESS_ACTION, ReconciliationReferenceType.BUSINESS_LOG,
                    "CL-20260810-TM01", "Đã đối soát thông tin CCCD và hợp nhất dữ liệu bệnh án", "coordinator",
                    Instant.now());
            reconciliationIncidentRepository.save(closedInc);
        }
    }

    private void ensureNotifications(PatientAccount patient) {
        if (patientNotificationRepository == null) return;
        List<PatientNotification> notifs = List.of(
                PatientNotification.appointmentCreated(patient.getId(), java.util.UUID.randomUUID(),
                        "CL-20260820-TQ01", "Khám Tổng Quát", "BS. CKII Nguyễn An", "20/08/2026", "08:30"),
                PatientNotification.appointmentReminder(patient.getId(), java.util.UUID.randomUUID(),
                        "CL-20260820-TQ01", "Khám Tổng Quát", "BS. CKII Nguyễn An", "20/08/2026", "08:30", 24),
                PatientNotification.recordSigned(patient.getId(), java.util.UUID.randomUUID(),
                        "PAST-REC-001", "BS. CKII Nguyễn An", "Khám Tổng Quát")
        );
        for (PatientNotification n : notifs) {
            if (patientNotificationRepository.findByEventKey(n.getEventKey()).isEmpty()) {
                patientNotificationRepository.save(n);
            }
        }
    }

    private record SeedMedication(String name, String dosage, int quantity, String instructions) {}
}
