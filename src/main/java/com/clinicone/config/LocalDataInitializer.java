package com.clinicone.config;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.auth.StaffAccount;
import com.clinicone.auth.StaffAccountRepository;
import com.clinicone.auth.StaffRole;
import com.clinicone.diagnosis.DiagnosisCatalog;
import com.clinicone.diagnosis.DiagnosisCatalogRepository;
import com.clinicone.doctor.DoctorProfile;
import com.clinicone.doctor.DoctorProfileRepository;
import com.clinicone.doctor.DoctorSchedule;
import com.clinicone.doctor.DoctorScheduleRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.examination.MedicalRecordTemplate;
import com.clinicone.examination.MedicalRecordTemplateRepository;
import com.clinicone.examination.PrescriptionLine;
import com.clinicone.invoice.Invoice;
import com.clinicone.invoice.InvoiceItem;
import com.clinicone.invoice.InvoiceRepository;
import com.clinicone.invoice.PaymentMethod;
import com.clinicone.medication.Medication;
import com.clinicone.medication.MedicationRepository;
import com.clinicone.notification.PatientNotification;
import com.clinicone.notification.PatientNotificationRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.ClinicRoomRepository;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
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
import com.clinicone.schedule.ScheduleBreak;
import com.clinicone.schedule.ScheduleBreakRequest;
import com.clinicone.schedule.ScheduleTemplateService;
import com.clinicone.schedule.SpecialtyCatalogEntry;
import com.clinicone.schedule.SpecialtyCatalogRepository;
import com.clinicone.schedule.WorkScheduleTemplate;
import com.clinicone.schedule.WorkScheduleTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Safe, idempotent data initializer so the complete staff and patient flows
 * can be exercised against a real database with rich, realistic Vietnamese clinical data.
 */
@Component
@Profile("local")
public class LocalDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDataInitializer.class);
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
    private final String adminPassword;
    private final String receptionistPassword;
    private final String doctorPassword;
    private final String patientPassword;
    private final boolean localDataInitializerEnabled;
    private final boolean allowRemote;
    private final String datasourceUrl;

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

    @Autowired(required = false)
    private InvoiceRepository invoiceRepository;

    @Autowired(required = false)
    private BusinessLogService businessLogService;

    @Autowired
    public LocalDataInitializer(StaffAccountRepository staffRepository,
                                ClinicRoomRepository roomRepository,
                                DoctorProfileRepository profileRepository,
                                DoctorScheduleRepository scheduleRepository,
                                PasswordEncoder passwordEncoder,
                                PatientAccountRepository patientRepository,
                                PatientProfileRepository patientProfileRepository,
                                AppointmentRepository appointmentRepository,
                                ClinicServiceRepository clinicServiceRepository,
                                @Value("${LOCAL_ADMIN_PASSWORD}") String adminPassword,
                                @Value("${LOCAL_RECEPTION_PASSWORD}") String receptionistPassword,
                                @Value("${LOCAL_DOCTOR_PASSWORD}") String doctorPassword,
                                @Value("${LOCAL_PATIENT_PASSWORD}") String patientPassword,
                                @Value("${LOCAL_DATA_INITIALIZER_ENABLED:false}") boolean localDataInitializerEnabled,
                                @Value("${LOCAL_DATA_INITIALIZER_ALLOW_REMOTE:false}") boolean allowRemote,
                                @Value("${spring.datasource.url:${DB_URL:}}") String datasourceUrl) {
        this.staffRepository = staffRepository;
        this.roomRepository = roomRepository;
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.passwordEncoder = passwordEncoder;
        this.patientRepository = patientRepository;
        this.patientProfileRepository = patientProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.clinicServiceRepository = clinicServiceRepository;
        this.adminPassword = adminPassword;
        this.receptionistPassword = receptionistPassword;
        this.doctorPassword = doctorPassword;
        this.patientPassword = patientPassword;
        this.localDataInitializerEnabled = localDataInitializerEnabled;
        this.allowRemote = allowRemote;
        this.datasourceUrl = datasourceUrl;
    }

    public LocalDataInitializer(StaffAccountRepository staffRepository,
                                ClinicRoomRepository roomRepository,
                                DoctorProfileRepository profileRepository,
                                DoctorScheduleRepository scheduleRepository,
                                PasswordEncoder passwordEncoder,
                                PatientAccountRepository patientRepository,
                                PatientProfileRepository patientProfileRepository,
                                AppointmentRepository appointmentRepository,
                                ClinicServiceRepository clinicServiceRepository,
                                String adminPassword,
                                String receptionistPassword,
                                String doctorPassword,
                                String patientPassword,
                                boolean localDataInitializerEnabled,
                                String datasourceUrl) {
        this(staffRepository, roomRepository, profileRepository, scheduleRepository, passwordEncoder,
                patientRepository, patientProfileRepository, appointmentRepository, clinicServiceRepository,
                adminPassword, receptionistPassword, doctorPassword, patientPassword,
                localDataInitializerEnabled, false, datasourceUrl);
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!LocalDataSafetyPolicy.shouldRun(localDataInitializerEnabled, datasourceUrl, allowRemote)) {
            log.info("Local fixture initializer is disabled; no sample data or schema changes were applied.");
            return;
        }
        log.info("Starting ClinicOne comprehensive clinical data bootstrap...");

        // 1. Staff Accounts & Operational Roles
        ensureStaff("admin", adminPassword, "Quản trị viên Hệ thống", StaffRole.ADMIN);
        ensureStaff("coordinator", adminPassword, "Điều phối viên Trần Hoàng", StaffRole.COORDINATOR);
        ensureStaff("reception", receptionistPassword, "Tiếp đón Lê Thu Thảo", StaffRole.RECEPTIONIST);

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

        // 4. Clinic Services for each specialty with realistic consultation fees
        ClinicService srvTQ = ensureOneService("Khám tổng quát ClinicOne", "Khám Tổng Quát", "Khám thường", 30, 150_000L, List.of(docTongQuat));
        ClinicService srvTM = ensureOneService("Khám chuyên sâu Tim mạch", "Khám Tim Mạch", "Khám chuyên khoa", 30, 200_000L, List.of(docTimMach));
        ClinicService srvHH = ensureOneService("Khám chuyên khoa Hô hấp", "Khám Hô Hấp", "Khám chuyên khoa", 30, 200_000L, List.of(docHoHap));
        ClinicService srvTH = ensureOneService("Khám Tiêu hóa & Gan mật", "Khám Tiêu Hoá - Gan Mật", "Khám chuyên khoa", 30, 200_000L, List.of(docTieuHoa));
        ClinicService srvNK = ensureOneService("Khám & Tư vấn Nhi khoa", "Khám Nhi Khoa", "Khám chuyên khoa", 30, 180_000L, List.of(docNhiKhoa));
        ClinicService srvTMH = ensureOneService("Khám & Nội soi Tai Mũi Họng", "Khám Tai Mũi Họng", "Khám chuyên khoa", 30, 250_000L, List.of(docTMH));
        ClinicService srvMAT = ensureOneService("Khám khúc xạ & Mắt chuyên sâu", "Khám Mắt", "Khám chuyên khoa", 30, 220_000L, List.of(docMat));

        // 5. Work Schedule Templates & Slot Generation for all 7 Specialty Doctors (Active through Evening 20h - 22h)
        List<ScheduleBreakRequest> clinicBreaks = List.of(
                new ScheduleBreakRequest(LocalTime.of(12, 0), LocalTime.of(13, 0)),
                new ScheduleBreakRequest(LocalTime.of(18, 0), LocalTime.of(19, 0))
        );

        ensureScheduleTemplates(srvTQ, docTongQuat, docTongQuat.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        ensureScheduleTemplates(srvTM, docTimMach, docTimMach.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        ensureScheduleTemplates(srvHH, docHoHap, docHoHap.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        ensureScheduleTemplates(srvTH, docTieuHoa, docTieuHoa.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        ensureScheduleTemplates(srvNK, docNhiKhoa, docNhiKhoa.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        ensureScheduleTemplates(srvTMH, docTMH, docTMH.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        ensureScheduleTemplates(srvMAT, docMat, docMat.getRoom(),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), LocalTime.of(22, 0), clinicBreaks);

        // 6. Patients & Comprehensive Family Sub-Profiles
        PatientAccount patient1 = ensurePatient("0900000001", "Nguyễn Thanh Vũ");
        PatientProfile profile1 = ensureDetailedProfile(patient1, "Nguyễn Thanh Vũ", "Bản thân", LocalDate.of(2000, 1, 1), "Nam",
                patient1.getPhone(), "079200012345", "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", true);
        PatientProfile childProfile = ensureDetailedProfile(patient1, "Bé Nguyễn Bảo Nam", "Con cái", LocalDate.of(2020, 5, 15), "Nam",
                patient1.getPhone(), null, "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);
        PatientProfile motherProfile = ensureDetailedProfile(patient1, "Nguyễn Thị Mai Lan", "Mẹ", LocalDate.of(1965, 3, 10), "Nữ",
                "0903112233", "079165009876", "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);
        PatientProfile sisterProfile = ensureDetailedProfile(patient1, "Nguyễn Thanh Trúc", "Em gái", LocalDate.of(2003, 8, 14), "Nữ",
                "0903112288", "079303004567", "123 Đường Nguyễn Huệ", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);

        PatientAccount patient2 = ensurePatient("0900000002", "Trần Thị Mai");
        PatientProfile profile2 = ensureDetailedProfile(patient2, "Trần Thị Mai", "Bản thân", LocalDate.of(1994, 8, 20), "Nữ",
                patient2.getPhone(), "079194005432", "456 Đường Lê Duẩn", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", true);
        PatientProfile fatherProfile = ensureDetailedProfile(patient2, "Trần Văn Hùng", "Bố", LocalDate.of(1955, 12, 4), "Nam",
                "0908776655", "079155001122", "456 Đường Lê Duẩn", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);
        PatientProfile motherProfile2 = ensureDetailedProfile(patient2, "Lê Thị Cúc", "Mẹ", LocalDate.of(1959, 4, 10), "Nữ",
                "0908776699", "079159008877", "456 Đường Lê Duẩn", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé", false);

        PatientAccount patient3 = ensurePatient("0900000004", "Phạm Minh Đức");
        PatientProfile profile3 = ensureDetailedProfile(patient3, "Phạm Minh Đức", "Bản thân", LocalDate.of(1958, 11, 12), "Nam",
                patient3.getPhone(), "079158003344", "789 Đường Hai Bà Trưng", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26740", "Phường Tân Định", true);
        PatientProfile wifeProfile = ensureDetailedProfile(patient3, "Lê Thị Ngọc", "Vợ", LocalDate.of(1962, 7, 25), "Nữ",
                "0909334455", "079162007788", "789 Đường Hai Bà Trưng", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26740", "Phường Tân Định", false);
        PatientProfile daughterProfile = ensureDetailedProfile(patient3, "Phạm Ngọc Hân", "Con gái", LocalDate.of(1995, 10, 22), "Nữ",
                "0909334499", "079195006655", "789 Đường Hai Bà Trưng", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26740", "Phường Tân Định", false);

        PatientAccount patient4 = ensurePatient("0912345678", "Hoàng Văn Hải");
        PatientProfile profile4 = ensureDetailedProfile(patient4, "Hoàng Văn Hải", "Bản thân", LocalDate.of(1988, 9, 14), "Nam",
                patient4.getPhone(), "079188009988", "12 Đường Nam Kỳ Khởi Nghĩa", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26737", "Phường Nguyễn Thái Bình", true);
        PatientProfile wifeProfile4 = ensureDetailedProfile(patient4, "Bùi Thúy Hằng", "Vợ", LocalDate.of(1990, 3, 15), "Nữ",
                "0912345699", "079190004433", "12 Đường Nam Kỳ Khởi Nghĩa", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26737", "Phường Nguyễn Thái Bình", false);
        PatientProfile childProfile4 = ensureDetailedProfile(patient4, "Bé Hoàng Gia Khang", "Con trai", LocalDate.of(2018, 7, 9), "Nam",
                patient4.getPhone(), null, "12 Đường Nam Kỳ Khởi Nghĩa", "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26737", "Phường Nguyễn Thái Bình", false);

        // 7. Seed Past Signed Medical Records & Paid Electronic Invoices (Full Circular 33/2025/TT-BYT compliance)
        ensurePastSignedRecord(patient1, profile1, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, 14, "PAST-REC-001",
                "Đau rát họng 3 ngày, sốt nhẹ 38°C, ho khan nhiều về đêm",
                "Niêm mạc họng đỏ, amidan sung huyết nhẹ độ 1, không có giả mạc, tim phổi bình thường",
                "J00 - Viêm mũi họng cấp tính (cảm thường)",
                "Viêm đường hô hấp trên thể nhẹ",
                "Nghỉ ngơi, uống nhiều nước ấm, súc họng nước muối sinh lý",
                new VitalsRecord("120/80 mmHg", 75, new BigDecimal("36.8"), 98, new BigDecimal("65.0"), new BigDecimal("170.0"), "Không có tiền sử dị ứng thuốc"),
                List.of(
                        new SeedMedication("Paracetamol 500mg (Hạ sốt, giảm đau nhanh)", "500mg", 10, "Uống 1 viên khi sốt trên 38.5°C cách 6h"),
                        new SeedMedication("Cefuroxime 500mg / Zinnat (Kháng sinh Cephalosporin thế hệ 2)", "500mg", 14, "Uống 1 viên x 2 lần/ngày sau ăn sáng/tối"),
                        new SeedMedication("Vitamin C 500mg (Chống oxy hóa, tăng sức đề kháng)", "500mg", 10, "Uống 1 viên/ngày sau ăn sáng")
                ));

        ensurePastSignedRecord(patient3, profile3, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, 7, "PAST-REC-002",
                "Kiểm tra huyết áp định kỳ, thỉnh thoảng có cảm giác hồi hộp nhẹ",
                "Huyết áp 145/90 mmHg, nhịp tim 76 ck/phút, phổi trong không ran",
                "I10 - Tăng huyết áp vô căn (nguyên phát)",
                "Tăng huyết áp độ 1 có kiểm soát",
                "Duy trì thuốc hạ áp hàng ngày, ăn nhạt, hạn chế muối",
                new VitalsRecord("145/90 mmHg", 76, new BigDecimal("36.5"), 97, new BigDecimal("72.0"), new BigDecimal("165.0"), "Chưa ghi nhận dị ứng"),
                List.of(
                        new SeedMedication("Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)", "5mg", 30, "Uống 1 viên vào 8h sáng hàng ngày"),
                        new SeedMedication("Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)", "50mg", 30, "Uống 1 viên vào 8h sáng hàng ngày")
                ));

        ensurePastSignedRecord(patient2, profile2, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, 5, "PAST-REC-003",
                "Đau âm ỉ vùng thượng vị sau ăn, ợ hơi, cồn cào",
                "Bụng mềm, ấn tức nhẹ thượng vị, không đề kháng thành bụng",
                "K21 - Bệnh trào ngược dạ dày - thực quản (GERD)",
                "Viêm dạ dày kết hợp trào ngược thực quản độ A",
                "Uống thuốc trước ăn sáng 30 phút, kiêng đồ chua cay, cà phê, không nằm ngay sau ăn",
                new VitalsRecord("110/70 mmHg", 80, new BigDecimal("36.7"), 99, new BigDecimal("52.0"), new BigDecimal("158.0"), "Không dị ứng"),
                List.of(
                        new SeedMedication("Nexium 40mg / Esomeprazole (Ức chế tiết acid dạ dày, trào ngược GERD)", "40mg", 28, "Uống 1 viên trước ăn sáng 30 phút"),
                        new SeedMedication("Phosphalugel (Gel chữ P trung hòa acid dạ dày, giảm ợ chua)", "Gói gel 20g", 20, "Uống 1 gói khi đau hoặc sau ăn 2 giờ")
                ));

        ensurePastSignedRecord(patient1, childProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, 3, "PAST-REC-004",
                "Bé ho húng hắng có đờm, chảy mũi trong, không sốt, ăn uống bình thường",
                "Họng đỏ nhẹ, không rale phổi, tai hai bên sạch",
                "J20 - Viêm phế quản cấp tính",
                "Viêm phế quản cấp thể nhẹ ở trẻ em",
                "Rửa mũi bằng nước muối sinh lý ấm, giữ ấm cổ ngực, uống nhiều nước ấm",
                new VitalsRecord("100/65 mmHg", 100, new BigDecimal("37.2"), 99, new BigDecimal("18.5"), new BigDecimal("110.0"), "Chưa ghi nhận dị ứng"),
                List.of(
                        new SeedMedication("Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)", "200mg", 10, "Pha 1 gói với 50ml nước ấm x 2 lần/ngày"),
                        new SeedMedication("Natri Clorid 0.9% 500ml (Nước muối sinh lý đẳng trương súc họng, rửa mũi)", "500ml", 2, "Nhỏ và rửa mũi 3 lần/ngày")
                ));

        ensurePastSignedRecord(patient2, profile2, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, 9, "PAST-REC-005",
                "Ho khan kéo dài, nghẹt mũi và mệt nhẹ",
                "Họng đỏ nhẹ, phổi thông khí đều, không ran; SpO2 98%.",
                "J06.9 - Nhiễm trùng đường hô hấp trên cấp, chưa xác định",
                "Viêm đường hô hấp trên mức độ nhẹ",
                "Uống đủ nước, nghỉ ngơi, theo dõi sốt và khó thở.",
                new VitalsRecord("115/75 mmHg", 78, new BigDecimal("37.0"), 98, new BigDecimal("53.0"), new BigDecimal("158.0"), "Không có"),
                List.of(
                        new SeedMedication("Paracetamol 500mg", "500mg khi đau/sốt", 10, "Uống khi sốt trên 38.5°C, cách nhau ít nhất 6 giờ"),
                        new SeedMedication("Loratadine 10mg", "10mg mỗi tối", 7, "Uống buổi tối nếu còn triệu chứng dị ứng")
                ));

        ensurePastSignedRecord(patient4, profile4, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, 11, "PAST-REC-006",
                "Nghẹt mũi, đau vùng mặt và giảm ngửi",
                "Niêm mạc mũi phù nề, dịch trong; họng sạch, không sốt.",
                "J01.90 - Viêm xoang cấp, chưa xác định",
                "Viêm mũi xoang cấp, chưa có dấu hiệu biến chứng",
                "Rửa mũi bằng nước muối sinh lý, tránh khói bụi và tái khám nếu đau tăng.",
                new VitalsRecord("125/82 mmHg", 74, new BigDecimal("36.6"), 99, new BigDecimal("68.0"), new BigDecimal("172.0"), "Không"),
                List.of(
                        new SeedMedication("Natri Clorid 0.9%", "Rửa mũi 2-3 lần/ngày", 1, "Dùng vệ sinh mũi theo hướng dẫn"),
                        new SeedMedication("Paracetamol 500mg", "500mg khi đau", 8, "Uống khi đau, tối đa theo hướng dẫn trên nhãn")
                ));

        ensurePastSignedRecord(patient3, profile3, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, 18, "PAST-REC-007",
                "Mỏi mắt khi đọc gần và nhìn màn hình lâu",
                "Thị lực hai mắt ổn định, kết mạc hồng, giác mạc trong, không ghi nhận dấu hiệu viêm.",
                "H53.1 - Rối loạn thị giác chủ quan",
                "Mỏi điều tiết khi làm việc gần kéo dài",
                "Nghỉ mắt định kỳ, đủ ánh sáng khi đọc; tái khám nếu nhìn mờ hoặc đau mắt.",
                new VitalsRecord("130/80 mmHg", 72, new BigDecimal("36.5"), 98, new BigDecimal("70.0"), new BigDecimal("165.0"), "Không"),
                List.of());

        // 8. Seed Today's Active Clinic Queue Tickets Across ALL 7 Doctors (Full Day: Morning, Afternoon, Evening 20h-22h)
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        List<LocalDate> clinicDates = nextClinicDates(today, 4);

        // General Medicine Doctor (docTongQuat - Room TQ-01)
        ensureActiveQueueScenario(patient1, childProfile, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 1, LocalTime.of(8, 0), demoAppointmentCode(today, "TQ-004"), QueueScenario.COMPLETED, "Khám tổng quát học đường");
        ensureActiveQueueScenario(patient1, profile1, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 2, LocalTime.of(8, 30), demoAppointmentCode(today, "TQ-001"), QueueScenario.IN_SERVICE, "Khám sức khỏe tổng quát định kỳ");
        ensureActiveQueueScenario(patient2, profile2, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 3, LocalTime.of(9, 0), demoAppointmentCode(today, "TQ-002"), QueueScenario.WAITING, "Kiểm tra sức khỏe tổng quát");
        ensureActiveQueueScenario(patient3, profile3, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 4, LocalTime.of(9, 30), demoAppointmentCode(today, "TQ-003"), QueueScenario.PRIORITY_WAITING, "Khám kiểm tra định kỳ người cao tuổi");
        ensureActiveQueueScenario(patient4, profile4, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 5, LocalTime.of(10, 0), demoAppointmentCode(today, "TQ-005"), QueueScenario.CALLED, "Khám tổng quát trước khi đi công tác");
        ensureActiveQueueScenario(patient3, wifeProfile, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 6, LocalTime.of(10, 30), demoAppointmentCode(today, "TQ-006"), QueueScenario.RETURN_REQUIRED, "Bệnh nhân chưa có mặt khi được gọi");
        ensureActiveQueueScenario(patient1, sisterProfile, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 7, LocalTime.of(11, 0), demoAppointmentCode(today, "TQ-007"), QueueScenario.RETURNED_AND_RECALLED, "Bệnh nhân đã quay lại sau khi được gọi");
        ensureActiveQueueScenario(patient2, motherProfile2, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 8, LocalTime.of(11, 30), demoAppointmentCode(today, "TQ-008"), QueueScenario.LEFT_BEFORE_EXAM, "Bệnh nhân có việc đột xuất, xin rời trước khi khám");
        ensureActiveQueueScenario(patient3, daughterProfile, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 9, LocalTime.of(15, 0), demoAppointmentCode(today, "TQ-009"), QueueScenario.STOPPED_DURING_EXAM, "Tạm dừng buổi khám để kiểm tra lại thông tin");
        ensureActiveQueueScenario(patient1, sisterProfile, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 10, LocalTime.of(20, 0), demoAppointmentCode(today, "TQ-010"), QueueScenario.WAITING, "Khám tổng quát buổi tối sau giờ làm");
        ensureActiveQueueScenario(patient2, motherProfile2, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 11, LocalTime.of(20, 30), demoAppointmentCode(today, "TQ-011"), QueueScenario.CALLED, "Tư vấn sức khỏe tổng quát ca tối");
        ensureActiveQueueScenario(patient4, profile4, docTongQuat.getStaffAccount(), docTongQuat, docTongQuat.getRoom(), srvTQ, today, 12, LocalTime.of(21, 0), demoAppointmentCode(today, "TQ-012"), QueueScenario.WAITING, "Kiểm tra tổng quát ngoài giờ");

        // Cardiology Doctor (docTimMach - Room TM-01)
        ensureActiveQueueScenario(patient2, fatherProfile, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 1, LocalTime.of(8, 30), demoAppointmentCode(today, "TM-001"), QueueScenario.IN_SERVICE, "Tái khám theo dõi tăng huyết áp và đau ngực");
        ensureActiveQueueScenario(patient1, profile1, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 2, LocalTime.of(9, 0), demoAppointmentCode(today, "TM-003"), QueueScenario.COMPLETED, "Khám tim mạch định kỳ");
        ensureActiveQueueScenario(patient1, motherProfile, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 3, LocalTime.of(10, 0), demoAppointmentCode(today, "TM-004"), QueueScenario.WAITING, "Tư vấn bệnh lý tim mạch");
        ensureActiveQueueScenario(patient3, profile3, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 4, LocalTime.of(14, 0), demoAppointmentCode(today, "TM-002"), QueueScenario.WAITING, "Kiểm tra nhịp tim và huyết áp");
        ensureActiveQueueScenario(patient4, wifeProfile4, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 5, LocalTime.of(14, 30), demoAppointmentCode(today, "TM-005"), QueueScenario.CALLED, "Đo điện tâm đồ và kiểm tra huyết áp");
        ensureActiveQueueScenario(patient4, profile4, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 6, LocalTime.of(15, 30), demoAppointmentCode(today, "TM-006"), QueueScenario.WAITING, "Tái khám bệnh van tim");
        ensureActiveQueueScenario(patient3, wifeProfile, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 7, LocalTime.of(20, 0), demoAppointmentCode(today, "TM-007"), QueueScenario.WAITING, "Khám tim mạch ca tối sau giờ làm");
        ensureActiveQueueScenario(patient3, daughterProfile, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 8, LocalTime.of(20, 30), demoAppointmentCode(today, "TM-008"), QueueScenario.CALLED, "Kiểm tra hồi hộp đánh trống ngực");
        ensureActiveQueueScenario(patient1, sisterProfile, docTimMach.getStaffAccount(), docTimMach, docTimMach.getRoom(), srvTM, today, 9, LocalTime.of(21, 0), demoAppointmentCode(today, "TM-009"), QueueScenario.WAITING, "Tư vấn điều hòa nhịp tim ngoài giờ");

        // Respiratory Doctor (docHoHap - Room HH-01)
        ensureActiveQueueScenario(patient1, sisterProfile, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 1, LocalTime.of(8, 30), demoAppointmentCode(today, "HH-003"), QueueScenario.COMPLETED, "Khám kiểm tra phế quản đầu giờ sáng");
        ensureActiveQueueScenario(patient1, motherProfile, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 2, LocalTime.of(9, 0), demoAppointmentCode(today, "HH-001"), QueueScenario.IN_SERVICE, "Khám ho đờm và khó thở nhẹ");
        ensureActiveQueueScenario(patient2, profile2, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 3, LocalTime.of(10, 0), demoAppointmentCode(today, "HH-002"), QueueScenario.WAITING, "Tái khám viêm phế quản mạn");
        ensureActiveQueueScenario(patient3, wifeProfile, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 4, LocalTime.of(11, 0), demoAppointmentCode(today, "HH-004"), QueueScenario.WAITING, "Khám viêm đường hô hấp trên");
        ensureActiveQueueScenario(patient4, profile4, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 5, LocalTime.of(14, 0), demoAppointmentCode(today, "HH-005"), QueueScenario.WAITING, "Kiểm tra chức năng hô hấp");
        ensureActiveQueueScenario(patient2, motherProfile2, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 6, LocalTime.of(15, 0), demoAppointmentCode(today, "HH-006"), QueueScenario.CALLED, "Tái khám hen phế quản");
        ensureActiveQueueScenario(patient1, motherProfile, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 7, LocalTime.of(20, 0), demoAppointmentCode(today, "HH-007"), QueueScenario.IN_SERVICE, "Khám khó thở và ho về đêm");
        ensureActiveQueueScenario(patient2, profile2, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 8, LocalTime.of(20, 30), demoAppointmentCode(today, "HH-008"), QueueScenario.WAITING, "Tái khám hô hấp ca tối");
        ensureActiveQueueScenario(patient4, childProfile4, docHoHap.getStaffAccount(), docHoHap, docHoHap.getRoom(), srvHH, today, 9, LocalTime.of(21, 0), demoAppointmentCode(today, "HH-009"), QueueScenario.WAITING, "Bé ho có đờm về đêm");

        // Gastroenterology Doctor (docTieuHoa - Room TH-01)
        ensureActiveQueueScenario(patient2, motherProfile2, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 1, LocalTime.of(8, 30), demoAppointmentCode(today, "TH-004"), QueueScenario.COMPLETED, "Nội soi tiêu hóa buổi sáng");
        ensureActiveQueueScenario(patient1, sisterProfile, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 2, LocalTime.of(9, 30), demoAppointmentCode(today, "TH-005"), QueueScenario.WAITING, "Đau bụng âm ỉ vùng quanh rốn");
        ensureActiveQueueScenario(patient1, profile1, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 3, LocalTime.of(10, 0), demoAppointmentCode(today, "TH-001"), QueueScenario.IN_SERVICE, "Đau rát vùng thượng vị, ợ chua nhiều");
        ensureActiveQueueScenario(patient4, profile4, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 4, LocalTime.of(11, 0), demoAppointmentCode(today, "TH-002"), QueueScenario.WAITING, "Nội soi dạ dày kiểm tra vi khuẩn HP");
        ensureActiveQueueScenario(patient3, daughterProfile, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 5, LocalTime.of(14, 0), demoAppointmentCode(today, "TH-006"), QueueScenario.WAITING, "Rối loạn tiêu hóa sau ăn");
        ensureActiveQueueScenario(patient3, wifeProfile, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 6, LocalTime.of(14, 30), demoAppointmentCode(today, "TH-003"), QueueScenario.FACILITY_UNAVAILABLE, "Phòng khám tạm dừng phục vụ do sự cố thiết bị");
        ensureActiveQueueScenario(patient2, profile2, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 7, LocalTime.of(15, 30), demoAppointmentCode(today, "TH-007"), QueueScenario.CALLED, "Tư vấn chế độ ăn trào ngược dạ dày");
        ensureActiveQueueScenario(patient1, profile1, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 8, LocalTime.of(20, 0), demoAppointmentCode(today, "TH-008"), QueueScenario.WAITING, "Ợ nóng rát sau bữa tối");
        ensureActiveQueueScenario(patient4, wifeProfile4, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 9, LocalTime.of(20, 30), demoAppointmentCode(today, "TH-009"), QueueScenario.IN_SERVICE, "Khám viêm dạ dày ca tối");
        ensureActiveQueueScenario(patient3, profile3, docTieuHoa.getStaffAccount(), docTieuHoa, docTieuHoa.getRoom(), srvTH, today, 10, LocalTime.of(21, 0), demoAppointmentCode(today, "TH-010"), QueueScenario.WAITING, "Đau tức hạ sườn phải ngoài giờ");

        // Pediatrics Doctor (docNhiKhoa - Room NK-01)
        ensureActiveQueueScenario(patient1, childProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 1, LocalTime.of(8, 30), demoAppointmentCode(today, "NK-001"), QueueScenario.WAITING, "Bé ho sốt và biếng ăn");
        ensureActiveQueueScenario(patient4, childProfile4, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 2, LocalTime.of(9, 0), demoAppointmentCode(today, "NK-003"), QueueScenario.COMPLETED, "Khám kiểm tra sức khỏe bé định kỳ");
        ensureActiveQueueScenario(patient2, profile2, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 3, LocalTime.of(9, 30), demoAppointmentCode(today, "NK-002"), QueueScenario.CALLED, "Tư vấn dinh dưỡng và tiêm chủng cho trẻ");
        ensureActiveQueueScenario(patient1, childProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 4, LocalTime.of(10, 30), demoAppointmentCode(today, "NK-004"), QueueScenario.WAITING, "Khám theo dõi viêm mũi họng trẻ em");
        ensureActiveQueueScenario(patient4, childProfile4, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 5, LocalTime.of(14, 0), demoAppointmentCode(today, "NK-005"), QueueScenario.IN_SERVICE, "Tái khám viêm họng cấp ở trẻ");
        ensureActiveQueueScenario(patient1, childProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 6, LocalTime.of(15, 0), demoAppointmentCode(today, "NK-006"), QueueScenario.WAITING, "Kiểm tra cân nặng chiều cao trẻ nhỏ");
        ensureActiveQueueScenario(patient4, childProfile4, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 7, LocalTime.of(20, 0), demoAppointmentCode(today, "NK-007"), QueueScenario.WAITING, "Bé sốt nhẹ quấy khóc ban đêm");
        ensureActiveQueueScenario(patient1, childProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 8, LocalTime.of(20, 30), demoAppointmentCode(today, "NK-008"), QueueScenario.IN_SERVICE, "Khám cấp cứu nhi ca tối");
        ensureActiveQueueScenario(patient3, daughterProfile, docNhiKhoa.getStaffAccount(), docNhiKhoa, docNhiKhoa.getRoom(), srvNK, today, 9, LocalTime.of(21, 0), demoAppointmentCode(today, "NK-009"), QueueScenario.WAITING, "Tư vấn sức khỏe trẻ vị thành niên ngoài giờ");

        // ENT Doctor (docTMH - Room TMH-01)
        ensureActiveQueueScenario(patient3, daughterProfile, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 1, LocalTime.of(8, 30), demoAppointmentCode(today, "TMH-003"), QueueScenario.COMPLETED, "Nội soi tai mũi họng kiểm tra amidan");
        ensureActiveQueueScenario(patient2, fatherProfile, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 2, LocalTime.of(9, 30), demoAppointmentCode(today, "TMH-004"), QueueScenario.WAITING, "Ù tai và nghe kém một bên");
        ensureActiveQueueScenario(patient4, wifeProfile4, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 3, LocalTime.of(10, 30), demoAppointmentCode(today, "TMH-005"), QueueScenario.WAITING, "Viêm mũi dị ứng thời tiết");
        ensureActiveQueueScenario(patient4, profile4, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 4, LocalTime.of(13, 30), demoAppointmentCode(today, "TMH-001"), QueueScenario.WAITING, "Nội soi kiểm tra viêm xoang và nghẹt mũi");
        ensureActiveQueueScenario(patient2, profile2, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 5, LocalTime.of(14, 30), demoAppointmentCode(today, "TMH-002"), QueueScenario.CALLED, "Đau rát họng nuốt vướng kéo dài");
        ensureActiveQueueScenario(patient1, profile1, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 6, LocalTime.of(15, 0), demoAppointmentCode(today, "TMH-006"), QueueScenario.WAITING, "Khám vòm họng và kiểm tra hạt xơ thanh quản");
        ensureActiveQueueScenario(patient4, profile4, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 7, LocalTime.of(20, 0), demoAppointmentCode(today, "TMH-007"), QueueScenario.IN_SERVICE, "Khám nội soi mũi xoang ca tối");
        ensureActiveQueueScenario(patient2, fatherProfile, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 8, LocalTime.of(20, 30), demoAppointmentCode(today, "TMH-008"), QueueScenario.WAITING, "Nghẹt mũi khó thở ban đêm");
        ensureActiveQueueScenario(patient4, wifeProfile4, docTMH.getStaffAccount(), docTMH, docTMH.getRoom(), srvTMH, today, 9, LocalTime.of(21, 0), demoAppointmentCode(today, "TMH-009"), QueueScenario.WAITING, "Đau rát tai ngoài giờ hành chính");

        // Ophthalmology Doctor (docMat - Room MAT-01)
        ensureActiveQueueScenario(patient3, profile3, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 1, LocalTime.of(8, 30), demoAppointmentCode(today, "MAT-001"), QueueScenario.IN_SERVICE, "Khám kiểm tra đáy mắt và đo nhãn áp");
        ensureActiveQueueScenario(patient3, wifeProfile, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 2, LocalTime.of(9, 0), demoAppointmentCode(today, "MAT-003"), QueueScenario.COMPLETED, "Khám tầm soát mộng thịt và đục thủy tinh thể");
        ensureActiveQueueScenario(patient1, profile1, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 3, LocalTime.of(10, 30), demoAppointmentCode(today, "MAT-002"), QueueScenario.WAITING, "Đo khúc xạ kiểm tra độ cận thị");
        ensureActiveQueueScenario(patient3, daughterProfile, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 4, LocalTime.of(11, 0), demoAppointmentCode(today, "MAT-004"), QueueScenario.WAITING, "Khám mỏi mắt và khô mắt văn phòng");
        ensureActiveQueueScenario(patient2, motherProfile2, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 5, LocalTime.of(14, 0), demoAppointmentCode(today, "MAT-005"), QueueScenario.WAITING, "Kiểm tra thị lực người lớn tuổi");
        ensureActiveQueueScenario(patient4, profile4, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 6, LocalTime.of(15, 0), demoAppointmentCode(today, "MAT-006"), QueueScenario.CALLED, "Đo khúc xạ điều chỉnh kính cận loạn");
        ensureActiveQueueScenario(patient3, profile3, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 7, LocalTime.of(20, 0), demoAppointmentCode(today, "MAT-007"), QueueScenario.WAITING, "Khám mắt mờ và cộm rát ca tối");
        ensureActiveQueueScenario(patient1, profile1, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 8, LocalTime.of(20, 30), demoAppointmentCode(today, "MAT-008"), QueueScenario.IN_SERVICE, "Khám mắt chuyên sâu ngoài giờ");
        ensureActiveQueueScenario(patient2, fatherProfile, docMat.getStaffAccount(), docMat, docMat.getRoom(), srvMAT, today, 9, LocalTime.of(21, 0), demoAppointmentCode(today, "MAT-009"), QueueScenario.WAITING, "Tái khám mắt định kỳ ca tối");

        // 9. Seed Future Booked Appointments (For patient appointment lists and booking tests)
        LocalDate tomorrow = clinicDates.get(1);
        LocalDate dayAfterTomorrow = clinicDates.get(2);
        ensureFutureAppointment(patient1, profile1, docTimMach, srvTM, tomorrow, LocalTime.of(9, 0), demoAppointmentCode(tomorrow, "TM-F01"), "Tư vấn và kiểm tra tim mạch chuyên sâu");
        ensureFutureAppointment(patient1, motherProfile, docHoHap, srvHH, tomorrow, LocalTime.of(10, 0), demoAppointmentCode(tomorrow, "HH-F01"), "Tái khám kiểm tra chức năng hô hấp");
        ensureFutureAppointment(patient2, profile2, docMat, srvMAT, tomorrow, LocalTime.of(14, 0), demoAppointmentCode(tomorrow, "MAT-F01"), "Khám kiểm tra thị lực và đo khúc xạ mắt");
        ensureFutureAppointment(patient3, profile3, docTongQuat, srvTQ, dayAfterTomorrow, LocalTime.of(8, 30), demoAppointmentCode(dayAfterTomorrow, "TQ-F01"), "Khám sức khỏe tổng quát định kỳ");
        ensureFutureAppointment(patient4, profile4, docNhiKhoa, srvNK, dayAfterTomorrow, LocalTime.of(9, 30), demoAppointmentCode(dayAfterTomorrow, "NK-F01"), "Khám tư vấn dinh dưỡng");

        // 10. Seed Doctor Time Off & Multiple Rescheduling Cases (Xếp lịch lại do bác sĩ nghỉ đột xuất)
        ensureRescheduleScenario(patient1, profile1, docMat, srvMAT, tomorrow, LocalTime.of(8, 30), demoAppointmentCode(tomorrow, "MAT-R01"),
                "Bác sĩ phụ trách có lịch công tác đột xuất tại Bệnh viện Mắt Trung ương; vui lòng chọn khung giờ thay thế.",
                "Bác sĩ tham dự hội nghị nhãn khoa toàn quốc");
        ensureRescheduleScenario(patient2, profile2, docTieuHoa, srvTH, tomorrow, LocalTime.of(9, 30), demoAppointmentCode(tomorrow, "TH-R01"),
                "Bác sĩ phụ trách có ca nội soi cấp cứu đột xuất.",
                "Bác sĩ có ca xử trí cấp cứu đột xuất");
        ensureRescheduleScenario(patient4, profile4, docHoHap, srvHH, dayAfterTomorrow, LocalTime.of(14, 0), demoAppointmentCode(dayAfterTomorrow, "HH-R01"),
                "Bác sĩ phụ trách tham gia hội đồng thẩm định chuyên môn tuyến trên.",
                "Bác sĩ tham gia hội đồng thẩm định chuyên môn");

        // Past outcome records
        LocalDate previousClinicDate = previousClinicDate(today);
        ensurePastAppointmentOutcome(patient3, profile3, docTimMach, srvTM, previousClinicDate,
                LocalTime.of(9, 0), "HIST-" + previousClinicDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-ABSENT",
                "Người bệnh không đến và không thông báo", false);
        ensurePastAppointmentOutcome(patient4, profile4, docTMH, srvTMH, previousClinicDate,
                LocalTime.of(10, 0), "HIST-" + previousClinicDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-CANCEL",
                "Người bệnh báo bận và xin hủy trước ngày khám", true);

        // 11. Seed Reconciliation Incidents (Đối soát: Thanh toán, Giữ chỗ, Trùng lịch, Báo nhầm hồ sơ)
        ensureReconciliationIncidents();

        // 12. Seed Patient Notifications
        ensureNotifications(patient1);

        log.info("Local bootstrap complete: 10 staff accounts, 7 specialty doctors, 10 rooms, 7 services, 13 patient profiles, full-day and evening active queue tickets, templates, catalogs, invoices, and notifications successfully initialized");
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
                new String[]{"MED-PARA-500", "Paracetamol 500mg (Hạ sốt, giảm đau nhanh)", "Hạ sốt & Giảm đau", "Khám Tổng Quát, Khám Hô Hấp, Khám Nhi Khoa, Khám Tai Mũi Họng", "1 viên / lần x 3 lần/ngày khi sốt > 38.5°C", "Uống sau ăn với 200ml nước ấm", "Viên", "2000"},
                new String[]{"MED-PARA-650", "Hapacol 650mg / Paracetamol 650mg (Hạ sốt, giảm đau liều cao)", "Hạ sốt & Giảm đau", "Khám Tổng Quát, Khám Hô Hấp", "1 viên / lần x 2-3 lần/ngày", "Uống sau ăn khi đau nhiều", "Viên", "3500"},
                new String[]{"MED-EFFER-500", "Efferalgan 500mg (Viên sủi hạ sốt, giảm đau)", "Hạ sốt & Giảm đau", "Khám Tổng Quát, Khám Hô Hấp", "1 viên sủi hòa tan trong 150ml nước", "Uống ngay sau khi sủi tan hết", "Viên", "4000"},
                new String[]{"MED-IBUP-400", "Ibuprofen 400mg (Kháng viêm, giảm đau NSAID)", "Hạ sốt & Giảm đau", "Khám Tổng Quát, Khám Tai Mũi Họng", "1 viên / lần x 2 lần/ngày sau bữa ăn no", "Không uống khi bụng đói", "Viên", "3000"},
                new String[]{"MED-MELO-15", "Meloxicam 15mg (Kháng viêm giảm đau thoái hóa khớp)", "Hạ sốt & Giảm đau", "Khám Tổng Quát", "1 viên / ngày sau ăn trưa", "Uống nhiều nước", "Viên", "6500"},
                new String[]{"MED-CELE-200", "Celecoxib 200mg (Kháng viêm chọn lọc COX-2, êm dạ dày)", "Hạ sốt & Giảm đau", "Khám Tổng Quát", "1 viên / ngày sau bữa ăn", "Uống nguyên viên", "Viên", "8000"},
                new String[]{"MED-DICLO-50", "Diclofenac 50mg / Voltaren (Giảm đau kháng viêm cơ xương khớp)", "Hạ sốt & Giảm đau", "Khám Tổng Quát", "1 viên / lần x 2 lần/ngày sau ăn", "Uống sau khi ăn no", "Viên", "4500"},

                // 2. Kháng sinh & Kháng khuẩn
                new String[]{"MED-AUGM-1000", "Augmentin 1g / Amoxicillin + Acid Clavulanic 1000mg (Kháng sinh phổ rộng)", "Kháng sinh", "Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / lần x 2 lần/ngày", "Uống ngay đầu bữa ăn", "Viên", "18000"},
                new String[]{"MED-AMOX-500", "Amoxicillin 500mg (Kháng sinh nhóm Penicillin)", "Kháng sinh", "Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / lần x 3 lần/ngày", "Uống cách đều mỗi 8 tiếng", "Viên", "3000"},
                new String[]{"MED-CEFU-500", "Cefuroxime 500mg / Zinnat (Kháng sinh Cephalosporin thế hệ 2)", "Kháng sinh", "Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / lần x 2 lần/ngày", "Uống sau bữa ăn sáng và tối", "Viên", "15000"},
                new String[]{"MED-CEFI-200", "Cefixime 200mg (Kháng sinh Cephalosporin thế hệ 3 đường uống)", "Kháng sinh", "Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / lần x 2 lần/ngày", "Uống sau ăn", "Viên", "9000"},
                new String[]{"MED-AZIT-500", "Azithromycin 500mg (Kháng sinh Macrolide trị nhiễm khuẩn hô hấp)", "Kháng sinh", "Khám Hô Hấp, Khám Tai Mũi Họng", "1 viên / ngày uống 1 lần duy nhất", "Uống trước ăn 1 giờ hoặc sau ăn 2 giờ", "Viên", "14000"},
                new String[]{"MED-CIPRO-500", "Ciprofloxacin 500mg (Kháng sinh Quinolone tiết niệu & tiêu hóa)", "Kháng sinh", "Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát", "1 viên / lần x 2 lần/ngày", "Uống với nhiều nước", "Viên", "5000"},

                // 3. Kháng viêm Corticoid & Chống dị ứng
                new String[]{"MED-MEDROL-16", "Medrol 16mg / Methylprednisolone (Kháng viêm Corticoid mạnh)", "Kháng viêm & Dị ứng", "Khám Hô Hấp, Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / ngày vào 8h sáng sau ăn no", "Uống sau ăn no buổi sáng", "Viên", "6000"},
                new String[]{"MED-LORA-10", "Loratadine 10mg (Kháng Histamin H1, chống dị ứng thế hệ 2)", "Kháng viêm & Dị ứng", "Khám Hô Hấp, Khám Tai Mũi Họng, Khám Mắt", "1 viên / ngày vào buổi tối trước khi ngủ", "Uống trước khi đi ngủ", "Viên", "2500"},
                new String[]{"MED-CETI-10", "Cetirizine 10mg (Chống dị ứng, viêm mũi dị ứng, ngứa)", "Kháng viêm & Dị ứng", "Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / ngày", "Uống sau ăn tối", "Viên", "2000"},
                new String[]{"MED-TELFAST-180", "Telfast 180mg / Fexofenadine 180mg (Chống dị ứng mề đay, không gây buồn ngủ)", "Kháng viêm & Dị ứng", "Khám Tai Mũi Họng, Khám Tổng Quát", "1 viên / ngày", "Uống trước bữa ăn", "Viên", "11000"},

                // 4. Tiêu hóa, Dạ dày & Gan mật
                new String[]{"MED-NEXIUM-40", "Nexium 40mg / Esomeprazole (Ức chế tiết acid dạ dày, trào ngược GERD)", "Tiêu hóa", "Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát", "1 viên / ngày trước ăn sáng 30 phút", "Uống nguyên viên trước ăn sáng", "Viên", "22000"},
                new String[]{"MED-OMEP-20", "Omeprazole 20mg (Ức chế bơm Proton, viêm loét dạ dày)", "Tiêu hóa", "Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát", "1 viên / lần x 2 lần/ngày trước ăn", "Uống trước bữa ăn 30 phút", "Viên", "3500"},
                new String[]{"MED-PHOSPHO-P", "Phosphalugel (Gel chữ P trung hòa acid dạ dày, giảm ợ chua)", "Tiêu hóa", "Khám Tiêu Hoá - Gan Mật, Khám Tổng Quát", "1 gói / lần khi đau hoặc sau ăn 2 giờ", "Uống trực tiếp", "Gói", "5500"},
                new String[]{"MED-DOMPER-10", "Domperidone 10mg / Motilium (Chống nôn, đầy hơi, chậm tiêu)", "Tiêu hóa", "Khám Tiêu Hoá - Gan Mật", "1 viên / lần x 2-3 lần/ngày trước bữa ăn", "Uống trước ăn 15-30 phút", "Viên", "4000"},
                new String[]{"MED-SMECTA-3G", "Smecta 3g (Bột pha bảo vệ niêm mạc ruột tiêu chảy cấp)", "Tiêu hóa", "Khám Tiêu Hoá - Gan Mật, Khám Nhi Khoa", "1 gói pha 50ml nước ấm x 2-3 lần/ngày", "Khuấy đều và uống", "Gói", "4500"},
                new String[]{"MED-ENTERO-5ML", "Enterogermina (Men vi sinh dạng ống uống cân bằng hệ vi sinh)", "Tiêu hóa", "Khám Tiêu Hoá - Gan Mật, Khám Nhi Khoa", "1-2 ống / ngày sau bữa ăn", "Lắc kỹ trước khi uống", "Ống", "9000"},

                // 5. Tim mạch, Huyết áp & Mỡ máu
                new String[]{"MED-AMLO-5", "Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)", "Tim mạch", "Khám Tim Mạch, Khám Tổng Quát", "1 viên / ngày cố định 8h sáng", "Uống đều đặn mỗi sáng", "Viên", "3000"},
                new String[]{"MED-LOSA-50", "Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)", "Tim mạch", "Khám Tim Mạch, Khám Tổng Quát", "1 viên / ngày vào buổi sáng", "Uống cùng hoặc không cùng thức ăn", "Viên", "5500"},
                new String[]{"MED-CONCOR-25", "Concor 2.5mg / Bisoprolol (Hạ huyết áp, kiểm soát nhịp tim chẹn Beta)", "Tim mạch", "Khám Tim Mạch", "1 viên / ngày vào buổi sáng", "Uống nguyên viên với nước", "Viên", "7500"},
                new String[]{"MED-LIPITOR-20", "Lipitor 20mg / Atorvastatin 20mg (Hạ mỡ máu, giảm Cholesterol xấu LDL)", "Tim mạch", "Khám Tim Mạch, Khám Tổng Quát", "1 viên / ngày vào buổi tối trước khi đi ngủ", "Uống buổi tối", "Viên", "16500"},
                new String[]{"MED-ASPIRIN-81", "Aspirin 81mg (Chống kết tập tiểu cầu, phòng ngừa huyết khối tim mạch)", "Tim mạch", "Khám Tim Mạch", "1 viên / ngày sau ăn no", "Uống nguyên viên không bẻ", "Viên", "2000"},

                // 6. Hô hấp, Hen suyễn & Giảm ho
                new String[]{"MED-VENTO-INH", "Ventolin Inhaler 100mcg (Ống hít cắt cơn hen phế quản cấp)", "Hô hấp", "Khám Hô Hấp, Khám Nhi Khoa", "Xịt 1-2 nhát khi khó thở hoặc lên cơn hen", "Hít sâu và nín thở 5-10 giây", "Bình", "95000"},
                new String[]{"MED-ACETYL-200", "Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)", "Hô hấp", "Khám Hô Hấp, Khám Nhi Khoa, Khám Tai Mũi Họng, Khám Tổng Quát", "1 gói pha nước ấm x 2-3 lần/ngày", "Uống sau ăn", "Gói", "3000"},
                new String[]{"MED-AMBROX-30", "Ambroxol 30mg / Mucosolvan (Tiêu đờm, giảm ho có đờm)", "Hô hấp", "Khám Hô Hấp, Khám Tai Mũi Họng", "1 viên / lần x 3 lần/ngày", "Uống nhiều nước sau ăn", "Viên", "3500"},

                // 7. Bổ não, Khớp & Vi chất dinh dưỡng
                new String[]{"MED-GINKGO-120", "Ginkgo Biloba 120mg / Tanakan (Tăng cường tuần hoàn máu não, giảm chóng mặt)", "Vitamin & Dưỡng chất", "Khám Tổng Quát", "1 viên / lần x 2 lần/ngày sau bữa ăn", "Uống sáng và trưa", "Viên", "6000"},
                new String[]{"MED-CALCI-D3", "Calci Nano + Vitamin D3 (Bổ sung Canxi, phòng ngừa loãng xương)", "Vitamin & Dưỡng chất", "Khám Tổng Quát, Khám Nhi Khoa", "1 viên / ngày vào buổi sáng sau ăn", "Uống với nhiều nước buổi sáng", "Viên", "4500"},
                new String[]{"MED-VITC-500", "Vitamin C 500mg (Chống oxy hóa, tăng sức đề kháng)", "Vitamin & Dưỡng chất", "Khám Tổng Quát, Khám Nhi Khoa, Khám Hô Hấp", "1 viên / ngày sau ăn sáng", "Không uống vào buổi tối", "Viên", "2500"},

                // 8. Tai mũi họng & Mắt
                new String[]{"MED-TOBR-03", "Tobramycin 0.3% / Tobrex (Thuốc nhỏ mắt kháng khuẩn)", "Mắt & TMH", "Khám Mắt", "Nhỏ 1-2 giọt vào mắt bệnh x 3-4 lần/ngày", "Không để đầu lọ chạm vào mi mắt", "Lọ", "45000"},
                new String[]{"MED-OTRIVIN-01", "Otrivin 0.1% / Xylometazoline (Thuốc xịt mũi thông mũi, giảm nghẹt cấp)", "Mắt & TMH", "Khám Tai Mũi Họng", "Xịt 1 nhát mỗi bên mũi x 2 lần/ngày", "Không dùng quá 7 ngày liên tục", "Lọ", "52000"},
                new String[]{"MED-NACL-09", "Natri Clorid 0.9% 500ml (Nước muối sinh lý đẳng trương súc họng, rửa mũi)", "Mắt & TMH", "Khám Tai Mũi Họng, Khám Hô Hấp, Khám Nhi Khoa", "Dùng súc họng và rửa mũi 3-4 lần/ngày", "Dùng trực tiếp", "Chai", "12000"}
        );
        for (String[] item : meds) {
            String code = item[0];
            String name = item[1];
            String category = item.length > 2 ? item[2] : null;
            String specialties = item.length > 3 ? item[3] : null;
            String dosage = item.length > 4 ? item[4] : null;
            String instructions = item.length > 5 ? item[5] : null;
            String unit = item.length > 6 ? item[6] : "Viên";
            long unitPrice = item.length > 7 ? Long.parseLong(item[7]) : 5000L;

            Medication existing = medicationRepository.findAll().stream()
                    .filter(m -> m.getCode().equalsIgnoreCase(code))
                    .findFirst().orElse(null);
            if (existing == null) {
                medicationRepository.save(Medication.create(code, name, category, specialties, dosage, instructions, unit, unitPrice));
            } else if (existing.getCategory() == null || existing.getUnitPrice() <= 0) {
                existing.update(code, name, category, specialties, dosage, instructions, unit, unitPrice);
                medicationRepository.save(existing);
            }
        }
    }

    private void ensureDiagnoses() {
        if (diagnosisCatalogRepository == null) return;
        List<String[]> list = List.of(
                new String[]{"J00", "J00 - Viêm mũi họng cấp tính (cảm thường)"},
                new String[]{"J01", "J01 - Viêm xoang cấp tính"},
                new String[]{"J02", "J02 - Viêm họng cấp tính"},
                new String[]{"J03", "J03 - Viêm amiđan cấp tính"},
                new String[]{"J06.9", "J06.9 - Nhiễm trùng đường hô hấp trên cấp, chưa xác định"},
                new String[]{"J20", "J20 - Viêm phế quản cấp tính"},
                new String[]{"J45", "J45 - Hen phế quản (Suyễn)"},
                new String[]{"I10", "I10 - Tăng huyết áp vô căn (nguyên phát)"},
                new String[]{"I20", "I20 - Cơn đau thắt ngực (thiếu máu cơ tim)"},
                new String[]{"K21", "K21 - Bệnh trào ngược dạ dày - thực quản (GERD)"},
                new String[]{"K25", "K25 - Loét dạ dày"},
                new String[]{"K29", "K29 - Viêm dạ dày và tá tràng"},
                new String[]{"E11", "E11 - Đái tháo đường không phụ thuộc insulin (Type 2)"},
                new String[]{"E78", "E78 - Rối loạn chuyển hóa lipoprotein và tăng lipid máu"},
                new String[]{"H52", "H52 - Tật khúc xạ và điều tiết (Cận/loạn/viễn thị)"},
                new String[]{"H53.1", "H53.1 - Rối loạn thị giác chủ quan"},
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

        ensureOneTemplate("TMPL-TM-01", "Mẫu khám Tăng huyết áp & Tim mạch", "Khám Tim Mạch",
                "Mẫu chuẩn theo dõi và điều trị tăng huyết áp nguyên phát",
                """
                {"reason":"Đo kiểm tra huyết áp định kỳ, thỉnh thoảng hơi căng tức thái dương","examinationNotes":"Huyết áp đo tại phòng khám 140/85 mmHg, mạch 76 lần/phút, tim T1 T2 đều rõ, không phù chi dưới, không ran phổi.","diagnosis":"I10 - Tăng huyết áp vô căn (nguyên phát)","conclusion":"Tăng huyết áp độ 1 giai đoạn ổn định","treatmentPlan":"Duy trì thuốc hạ áp hàng ngày vào buổi sáng, chế độ ăn giảm muối, hạn chế dầu mỡ và thức uống có cồn.","followUpDays":30,"followUpNote":"Tái khám đo lại huyết áp và đánh giá chức năng sau 1 tháng","prescriptionLines":[{"medicationName":"Amlodipine 5mg (Hạ huyết áp chẹn kênh Canxi)","dosage":"5mg x 1 lần/ngày","quantity":30,"unit":"Viên","instructions":"Uống 1 viên vào buổi sáng sau ăn"},{"medicationName":"Losartan 50mg (Hạ huyết áp ức chế thụ thể ARB)","dosage":"50mg x 1 lần/ngày","quantity":30,"unit":"Viên","instructions":"Uống 1 viên vào buổi sáng"}]}
                """);

        ensureOneTemplate("TMPL-HH-01", "Mẫu khám viêm đường hô hấp / Cảm cúm", "Khám Hô Hấp",
                "Mẫu chuẩn viêm mũi họng, cảm cúm, ho khan sốt nhẹ",
                """
                {"reason":"Đau rát họng, ho húng hắng, sốt nhẹ 38°C, nghẹt mũi","examinationNotes":"Niêm mạc họng đỏ, amidan sung huyết nhẹ không giả mạc, mũi xuất tiết dịch trong, phổi thông khí tốt không rale.","diagnosis":"J00 - Viêm mũi họng cấp tính (cảm thường)","conclusion":"Viêm đường hô hấp trên cấp tính thể nhẹ","treatmentPlan":"Nghỉ ngơi, súc họng nước muối sinh lý 3 lần/ngày, giữ ấm cổ ngực, uống nhiều nước ấm.","followUpDays":7,"followUpNote":"Tái khám sau 7 ngày nếu còn sốt cao hoặc ho kéo dài","prescriptionLines":[{"medicationName":"Paracetamol 500mg (Hạ sốt, giảm đau nhanh)","dosage":"500mg x 3 lần/ngày","quantity":15,"unit":"Viên","instructions":"Uống 1 viên khi sốt trên 38.5°C hoặc đau rát họng"},{"medicationName":"Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)","dosage":"200mg x 2 lần/ngày","quantity":14,"unit":"Gói","instructions":"Hòa tan 1 gói với 100ml nước ấm, uống sau ăn"},{"medicationName":"Loratadine 10mg (Kháng Histamin H1, chống dị ứng thế hệ 2)","dosage":"10mg x 1 lần/ngày","quantity":7,"unit":"Viên","instructions":"Uống 1 viên vào buổi tối trước khi đi ngủ"}]}
                """);

        ensureOneTemplate("TMPL-TH-01", "Mẫu khám Dạ dày - GERD - Gan mật", "Khám Tiêu Hoá - Gan Mật",
                "Mẫu chuẩn viêm dạ dày, trào ngược dạ dày thực quản",
                """
                {"reason":"Đau âm ỉ vùng thượng vị sau ăn, ợ hơi ợ chua, cồn cào","examinationNotes":"Bụng mềm, ấn tức nhẹ vùng thượng vị, không đề kháng thành bụng, gan lách không to.","diagnosis":"K21 - Bệnh trào ngược dạ dày - thực quản (GERD)","conclusion":"Viêm dạ dày kết hợp trào ngược thực quản","treatmentPlan":"Uống thuốc bảo vệ niêm mạc trước ăn sáng 30 phút, ăn đúng giờ, không ăn no sát giờ ngủ, tránh thức ăn cay nóng, cà phê.","followUpDays":14,"followUpNote":"Tái khám sau 2 tuần để đánh giá đáp ứng điều trị","prescriptionLines":[{"medicationName":"Omeprazole 20mg (Ức chế bơm Proton, viêm loét dạ dày)","dosage":"20mg x 2 lần/ngày","quantity":28,"unit":"Viên","instructions":"Uống trước ăn sáng và ăn tối 30 phút"},{"medicationName":"Phosphalugel (Gel chữ P trung hòa acid dạ dày, giảm ợ chua)","dosage":"1 gói x 2 lần/ngày","quantity":20,"unit":"Gói","instructions":"Uống khi đau hoặc sau ăn 1-2 giờ"},{"medicationName":"Domperidone 10mg / Motilium (Chống nôn, đầy hơi, chậm tiêu)","dosage":"10mg x 2 lần/ngày","quantity":20,"unit":"Viên","instructions":"Uống trước bữa ăn 15-30 phút"}]}
                """);

        ensureOneTemplate("TMPL-NK-01", "Mẫu khám Nhi khoa & Dinh dưỡng", "Khám Nhi Khoa",
                "Mẫu khám nhi khoa tổng quát và viêm đường hô hấp trẻ em",
                """
                {"reason":"Trẻ ho, sổ mũi, quấy khóc nhẹ, không sốt cao","examinationNotes":"Tri giác tỉnh, họng đỏ nhẹ, không rale phổi, bụng mềm, thóp phẳng, dinh dưỡng cân đối.","diagnosis":"J20 - Viêm phế quản cấp tính","conclusion":"Viêm phế quản cấp thể nhẹ ở trẻ em","treatmentPlan":"Vệ sinh mũi họng bằng nước muối sinh lý, uống nhiều nước, giữ ấm, theo dõi nhịp thở.","followUpDays":5,"followUpNote":"Tái khám ngay nếu trẻ sốt cao li bì hoặc thở nhanh co lõm ngực","prescriptionLines":[{"medicationName":"Paracetamol 500mg (Hạ sốt, giảm đau nhanh)","dosage":"10-15mg/kg/lần khi sốt >= 38.5°C","quantity":10,"unit":"Gói","instructions":"Uống cách nhau 4-6 giờ, tối đa 4 lần/ngày"},{"medicationName":"Acetylcystein 200mg (Long đờm, tiêu nhầy đường hô hấp)","dosage":"100mg x 2 lần/ngày","quantity":10,"unit":"Gói","instructions":"Pha nước ấm uống sau bữa ăn"}]}
                """);

        ensureOneTemplate("TMPL-TMH-01", "Mẫu khám & Nội soi Tai Mũi Họng", "Khám Tai Mũi Họng",
                "Mẫu chuẩn nội soi và chẩn đoán bệnh lý Tai Mũi Họng",
                """
                {"reason":"Nghẹt mũi 2 bên, chảy mũi trong, ngứa mũi hắt hơi","examinationNotes":"Nội soi TMH: Cuống mũi phù nề nhợt màu, xuất tiết dịch nhầy trong, vách ngăn không vẹo, họng sạch.","diagnosis":"J01 - Viêm xoang cấp tính","conclusion":"Viêm mũi xoang dị ứng cấp","treatmentPlan":"Xịt mũi nước muối ưu trương, tránh tiếp xúc khói bụi phấn hoa, dùng kháng histamin khi cần.","followUpDays":10,"followUpNote":"Tái khám sau 10 ngày để nội soi kiểm tra lại","prescriptionLines":[{"medicationName":"Augmentin 1g / Amoxicillin + Acid Clavulanic 1000mg (Kháng sinh phổ rộng)","dosage":"1000mg x 2 lần/ngày","quantity":14,"unit":"Viên","instructions":"Uống ngay trước bữa ăn sáng và tối"},{"medicationName":"Paracetamol 500mg (Hạ sốt, giảm đau nhanh)","dosage":"500mg x 3 lần/ngày","quantity":10,"unit":"Viên","instructions":"Uống khi đau đầu hoặc đau nhức vùng xoang"},{"medicationName":"Otrivin 0.1% / Xylometazoline (Thuốc xịt mũi thông mũi, giảm nghẹt cấp)","dosage":"Xịt 1 nhát mỗi bên mũi x 2 lần/ngày","quantity":1,"unit":"Lọ","instructions":"Xịt khi nghẹt mũi, không dùng quá 7 ngày liên tục"}]}
                """);

        ensureOneTemplate("TMPL-MAT-01", "Mẫu khám Mắt & Đo khúc xạ", "Khám Mắt",
                "Mẫu chuẩn kiểm tra thị lực, khúc xạ và viêm kết mạc",
                """
                {"reason":"Mắt mờ khi nhìn xa, mỏi mắt khi làm việc máy tính","examinationNotes":"Thị lực không kính: Mắt phải 3/10, Mắt trái 4/10. Khúc xạ có kính: Mắt phải 10/10 (-1.50D), Mắt trái 10/10 (-1.25D). Kết mạc hồng, giác mạc trong.","diagnosis":"H52 - Tật khúc xạ và điều tiết (Cận/loạn/viễn thị)","conclusion":"Cận thị hai mắt đơn thuần","treatmentPlan":"Đeo kính đúng độ khi làm việc và học tập, nghỉ ngơi quy tắc 20-20-20, nhỏ nước mắt nhân tạo khi mỏi mắt.","followUpDays":180,"followUpNote":"Kiểm tra lại độ khúc xạ sau 6 tháng"}
                """);
    }

    private void ensureOneTemplate(String code, String name, String specialty, String description, String fieldDef) {
        MedicalRecordTemplate existing = medicalRecordTemplateRepository.findAll().stream()
                .filter(t -> t.getCode().equalsIgnoreCase(code))
                .findFirst().orElse(null);
        if (existing == null) {
            medicalRecordTemplateRepository.save(MedicalRecordTemplate.create(code, name, specialty, null, description, fieldDef.trim(), "admin"));
        } else if (existing.getFieldDefinition() == null || (!existing.getFieldDefinition().contains("prescriptionLines") && fieldDef.contains("prescriptionLines"))) {
            existing.update(existing.getName(), existing.getSpecialty(), existing.getClinicServiceId(), existing.getDescription(), fieldDef.trim());
            medicalRecordTemplateRepository.save(existing);
        }
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
            case "doctor2" -> null;
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
        } else if ("doctor2".equalsIgnoreCase(username) || (docProfile.getAvatarUrl() != null && docProfile.getAvatarUrl().contains("1594824813589"))) {
            docProfile.updateAssignment(specialty, room, null);
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
        return room;
    }

    private ClinicService ensureOneService(String serviceName, String specialty, String visitType, int duration, long consultationFee, List<DoctorProfile> doctors) {
        ClinicService service = clinicServiceRepository.findAllByOrderByNameAsc().stream()
                .filter(item -> item.getName().equalsIgnoreCase(serviceName))
                .findFirst()
                .orElse(null);
        if (service == null) {
            return clinicServiceRepository.save(ClinicService.create(
                    serviceName, specialty, visitType, duration, consultationFee, true, doctors));
        }
        if (service.getConsultationFee() <= 0 && consultationFee > 0) {
            service.update(service.getName(), service.getSpecialty(), service.getVisitType(),
                    service.getDurationMinutes(), consultationFee, service.requiresMedicalRecord(), service.getEligibleDoctors());
            service = clinicServiceRepository.save(service);
        }
        return service;
    }

    private void ensureWeekdaySchedules(DoctorProfile profile) {
        List<DayOfWeek> weekdays = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        for (DayOfWeek day : weekdays) {
            DoctorSchedule schedule = scheduleRepository.findByDoctorProfile_IdAndDayOfWeekAndActiveTrue(profile.getId(), day)
                    .stream().findFirst().orElse(null);
            if (schedule == null) {
                scheduleRepository.save(DoctorSchedule.create(profile, day,
                        LocalTime.of(8, 0), LocalTime.of(22, 0), 30));
            } else if (schedule.getEndTime() == null || schedule.getEndTime().isBefore(LocalTime.of(22, 0))) {
                schedule.setEndTime(LocalTime.of(22, 0));
                scheduleRepository.save(schedule);
            }
        }
    }

    private void ensureScheduleTemplates(ClinicService service, DoctorProfile doctorProfile, ClinicRoom room,
                                        Set<DayOfWeek> weekdays, LocalTime dayStart, LocalTime dayEnd,
                                        List<ScheduleBreakRequest> breaks) {
        if (scheduleTemplateService == null || workScheduleTemplateRepository == null) return;
        List<WorkScheduleTemplate> doctorTemplates = workScheduleTemplateRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .filter(t -> t.getDoctorProfile().getId().equals(doctorProfile.getId()))
                .toList();
        LocalDate startDate = LocalDate.now(CLINIC_ZONE);
        LocalDate endDate = startDate.plusDays(45);
        if (!doctorTemplates.isEmpty()) {
            // Purge only the individual templates that are unusable (midnight start
            // or expired). Deleting every template of the doctor because one row was
            // corrupt wiped good shifts and left the doctor without any bookable slot.
            List<WorkScheduleTemplate> obsolete = doctorTemplates.stream()
                    .filter(t -> (t.getDayStart() != null && t.getDayStart().isBefore(LocalTime.of(7, 0)))
                            || (t.getEndDate() != null && t.getEndDate().isBefore(startDate)))
                    .toList();
            if (!obsolete.isEmpty()) {
                log.info("Purging or replacing obsolete schedule template for doctor {}: upgrading to {} - {}",
                        doctorProfile.getStaffAccount().getFullName(), dayStart, dayEnd);
                for (WorkScheduleTemplate dt : obsolete) {
                    try {
                        scheduleTemplateService.delete(dt.getId());
                    } catch (Exception e) {
                        log.warn("Could not delete legacy template: {}", e.getMessage());
                    }
                }
                doctorTemplates = workScheduleTemplateRepository.findByActiveTrueOrderByStartDateAsc().stream()
                        .filter(t -> t.getDoctorProfile().getId().equals(doctorProfile.getId()))
                        .toList();
            }
            if (doctorTemplates.isEmpty()) {
                log.info("No active schedule template left for doctor {} after the purge, creating a new one",
                        doctorProfile.getStaffAccount().getFullName());
            } else {
                WorkScheduleTemplate primary = doctorTemplates.stream()
                        .max(java.util.Comparator.comparing(WorkScheduleTemplate::getEndDate))
                        .orElse(doctorTemplates.get(0));

                for (WorkScheduleTemplate t : doctorTemplates) {
                    if (!t.getId().equals(primary.getId())) {
                        log.info("Deactivating redundant template {} for doctor {}", t.getId(), doctorProfile.getStaffAccount().getFullName());
                        t.setActive(false);
                        workScheduleTemplateRepository.save(t);
                        continue;
                    }

                    boolean needsUpgrade = t.getDayEnd() == null || t.getDayEnd().isBefore(dayEnd)
                            || !t.getWeekdays().containsAll(weekdays);
                    if (needsUpgrade) {
                        t.setDayStart(dayStart);
                        t.setDayEnd(dayEnd);
                        t.setDurationMinutes(30);
                        Set<DayOfWeek> mergedWeekdays = new LinkedHashSet<>(t.getWeekdays());
                        mergedWeekdays.addAll(weekdays);
                        t.setWeekdays(mergedWeekdays);
                        if (breaks != null) {
                            t.setBreaks(breaks.stream().map(b -> ScheduleBreak.create(b.startTime(), b.endTime())).toList());
                        }
                    }
                    if (t.getExceptionDates() != null) {
                        t.getExceptionDates().remove(startDate);
                    }
                    workScheduleTemplateRepository.save(t);
                    if (needsUpgrade) {
                        try {
                            scheduleTemplateService.regenerateForLocalBootstrap(t.getId());
                        } catch (Exception e) {
                            log.warn("Could not regenerate template {}: {}", t.getId(), e.getMessage());
                        }
                    }
                }
                return;
            }
        }

        try {
            scheduleTemplateService.createForLocalBootstrap(new CreateScheduleTemplateRequest(
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
        boolean stillMissingTemplate = workScheduleTemplateRepository.findByActiveTrueOrderByStartDateAsc().stream()
                .noneMatch(t -> t.getDoctorProfile().getId().equals(doctorProfile.getId()));
        if (stillMissingTemplate) {
            log.error("Doctor {} has no active schedule template: the scheduling screen and slot search will hide them",
                    doctorProfile.getStaffAccount().getFullName());
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
                                        DoctorProfile doctorProfile, ClinicRoom room, ClinicService service, int daysAgo, String code,
                                        String reason, String notes, String diagnosis, String conclusion, String plan,
                                        VitalsRecord vitals, List<SeedMedication> meds) {
        LocalDate pastDate = LocalDate.now(CLINIC_ZONE).minusDays(daysAgo);
        LocalTime pastTime = LocalTime.of(9, 0);
        Appointment app = appointmentRepository.findByAppointmentCode(code).orElse(null);
        if (app == null) {
            app = appointmentRepository.findByPatientProfileId(profile.getId()).stream()
                    .filter(a -> a.getAppointmentDate().equals(pastDate) && a.getStartTime().equals(pastTime))
                    .findFirst()
                    .orElse(null);
        }
        if (app == null) {
            app = Appointment.create(patient, doctor.getId(), profile, code,
                    doctorProfile.getSpecialty(), doctor.getFullName(), pastDate, pastTime, reason);
            app.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                    service.getDurationMinutes(), service.requiresMedicalRecord());
            app.checkIn();
            app.complete();
            app = appointmentRepository.save(app);
        } else if (app.getServiceId() == null) {
            app.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                    service.getDurationMinutes(), service.requiresMedicalRecord());
            app = appointmentRepository.save(app);
        }

        if (queueTicketRepository != null && examinationSessionRepository != null && medicalRecordRepository != null) {
            if (queueTicketRepository.findByAppointmentId(app.getId()).isEmpty()) {
                Integer maxNum = queueTicketRepository.findMaxQueueNumberByRoomCodeAndQueueDate(room.getCode(), pastDate);
                int effectiveNum = maxNum == null ? 1 : maxNum + 1;
                QueueTicket ticket = QueueTicket.create(app, room, pastDate, effectiveNum);
                ticket.call();
                ticket.startService();
                ticket.complete();
                queueTicketRepository.save(ticket);
            }

            final Appointment finalApp = app;
            final ExaminationSession session = examinationSessionRepository.findByAppointment_Id(app.getId())
                    .orElseGet(() -> {
                        ExaminationSession s = ExaminationSession.create(finalApp);
                        s.begin();
                        s.complete();
                        return examinationSessionRepository.saveAndFlush(s);
                    });

            if (medicalRecordRepository.findBySession_Id(session.getId()).isEmpty()) {
                MedicalRecord record = MedicalRecord.draft(session);
                int lineNumber = 1;
                List<PrescriptionLine> lines = new ArrayList<>();
                for (SeedMedication m : meds) {
                    lines.add(PrescriptionLine.create(record, null, m.name(), m.dosage(), m.quantity(), m.unit(), m.instructions(), lineNumber++));
                }
                record.sign(doctor.getFullName(), reason, notes, diagnosis, conclusion, plan, "Đơn thuốc điện tử",
                        pastDate.plusDays(30), lines, 30, "Tái khám đúng hẹn hoặc khi có triệu chứng bất thường",
                        vitals.bloodPressure(), vitals.heartRate(), vitals.temperature(), vitals.spO2(),
                        vitals.weight(), vitals.height(), vitals.allergySummary());
                medicalRecordRepository.save(record);
            }

            // Seed a PAID Electronic Invoice for this past completed session
            if (invoiceRepository != null && !invoiceRepository.existsBySession_Id(session.getId())) {
                String invCode = "INV-" + pastDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + code.replace("PAST-REC-", "REC");
                long fee = service.getConsultationFee() > 0 ? service.getConsultationFee() : 150_000L;
                Invoice invoice = Invoice.create(session, invCode, fee, "Thu ngân Lê Thu Thảo");
                invoice.addItem(InvoiceItem.consultation(service.getName(), fee));
                invoice.markPaid(fee, PaymentMethod.BANK_TRANSFER, "Thu ngân Lê Thu Thảo");
                invoiceRepository.save(invoice);
            }
        }
    }

    private enum QueueScenario {
        IN_SERVICE, WAITING, PRIORITY_WAITING, CALLED, RETURN_REQUIRED, RETURNED_AND_RECALLED,
        LEFT_BEFORE_EXAM, STOPPED_DURING_EXAM, FACILITY_UNAVAILABLE, COMPLETED
    }

    private void ensureActiveQueueScenario(PatientAccount patient, PatientProfile profile, StaffAccount doctor,
                                           DoctorProfile doctorProfile, ClinicRoom room, ClinicService service,
                                           LocalDate date,
                                           int queueNum, LocalTime time, String code, QueueScenario scenario, String reason) {
        Appointment app = appointmentRepository.findByAppointmentCode(code).orElse(null);
        if (app == null) {
            app = appointmentRepository.findByPatientProfileId(profile.getId()).stream()
                    .filter(a -> a.getAppointmentDate().equals(date) && a.getStartTime().equals(time))
                    .findFirst()
                    .orElse(null);
        }
        if (app == null) {
            app = Appointment.create(patient, doctor.getId(), profile, code,
                    doctorProfile.getSpecialty(), doctor.getFullName(), date, time, reason);
            app.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                    service.getDurationMinutes(), service.requiresMedicalRecord());
            app.checkIn();

            if (scenario == QueueScenario.COMPLETED) {
                app.complete();
            } else if (scenario == QueueScenario.LEFT_BEFORE_EXAM
                    || scenario == QueueScenario.STOPPED_DURING_EXAM
                    || scenario == QueueScenario.FACILITY_UNAVAILABLE) {
                app.markNotPerformed();
            }
            app = appointmentRepository.save(app);
        } else {
            if (app.getServiceId() == null) {
                app.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                        service.getDurationMinutes(), service.requiresMedicalRecord());
            }
            if (scenario == QueueScenario.COMPLETED && app.getStatus() != com.clinicone.appointment.AppointmentStatus.COMPLETED) {
                app.complete();
            } else if ((scenario == QueueScenario.LEFT_BEFORE_EXAM || scenario == QueueScenario.STOPPED_DURING_EXAM || scenario == QueueScenario.FACILITY_UNAVAILABLE)
                    && app.getStatus() != com.clinicone.appointment.AppointmentStatus.NOT_PERFORMED) {
                app.markNotPerformed();
            }
            app = appointmentRepository.save(app);
        }
        if (businessLogService != null) {
            businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", app.getId(),
                    null, app.getStatus().name(), "SEED_INITIALIZATION", "SYSTEM", "Dữ liệu mẫu khởi tạo");
        }

        if (queueTicketRepository != null && examinationSessionRepository != null) {
            final Appointment finalActiveApp = app;
            final ExaminationSession session = examinationSessionRepository.findByAppointment_Id(app.getId())
                    .orElseGet(() -> {
                        ExaminationSession createdSession = ExaminationSession.create(finalActiveApp);
                        switch (scenario) {
                            case IN_SERVICE, COMPLETED -> createdSession.begin();
                            case LEFT_BEFORE_EXAM, FACILITY_UNAVAILABLE -> createdSession.cancel();
                            case STOPPED_DURING_EXAM -> {
                                createdSession.begin();
                                createdSession.stop();
                            }
                            default -> { }
                        }
                        if (scenario == QueueScenario.COMPLETED) {
                            createdSession.complete();
                        }
                        ExaminationSession savedSession = examinationSessionRepository.saveAndFlush(createdSession);
                        if (businessLogService != null) {
                            businessLogService.recordTransition(UUID.randomUUID(), "EXAMINATION", savedSession.getId(),
                                    null, savedSession.getStatus().name(), "SEED_INITIALIZATION", "SYSTEM", "Dữ liệu mẫu khởi tạo");
                        }
                        return savedSession;
                    });

            if (queueTicketRepository.findByAppointmentId(app.getId()).isEmpty()) {
                Integer maxNum = queueTicketRepository.findMaxQueueNumberByRoomCodeAndQueueDate(room.getCode(), date);
                int effectiveNum = maxNum == null ? queueNum : Math.max(queueNum, maxNum + 1);
                QueueTicket ticket = QueueTicket.create(app, room, date, effectiveNum);
                switch (scenario) {
                    case WAITING -> { }
                    case PRIORITY_WAITING -> ticket.setPriority(true);
                    case CALLED -> ticket.call();
                    case RETURN_REQUIRED -> {
                        ticket.call();
                        ticket.skip("Người bệnh chưa có mặt khi được gọi");
                    }
                    case RETURNED_AND_RECALLED -> {
                        ticket.call();
                        ticket.skip("Người bệnh chưa có mặt khi được gọi");
                        ticket.markReturned(Instant.now());
                        ticket.call();
                    }
                    case IN_SERVICE -> {
                        ticket.call();
                        ticket.startService();
                    }
                    case LEFT_BEFORE_EXAM -> ticket.leaveBeforeExam(reason);
                    case STOPPED_DURING_EXAM -> {
                        ticket.call();
                        ticket.startService();
                        ticket.stopService(reason);
                    }
                    case FACILITY_UNAVAILABLE -> ticket.closeFacilityUnavailable(reason);
                    case COMPLETED -> {
                        ticket.call();
                        ticket.startService();
                        ticket.complete();
                    }
                }
                queueTicketRepository.save(ticket);
                if (businessLogService != null) {
                    businessLogService.recordTransition(UUID.randomUUID(), "QUEUE_TICKET", ticket.getId(),
                            null, ticket.getStatus().name(), "SEED_INITIALIZATION", "SYSTEM", "Dữ liệu mẫu khởi tạo");
                }
            }

            // For IN_SERVICE: Seed a draft medical record so the doctor sees partially filled notes & vitals
            if (scenario == QueueScenario.IN_SERVICE && medicalRecordRepository != null) {
                if (medicalRecordRepository.findBySession_Id(session.getId()).isEmpty()) {
                    MedicalRecord draftRecord = MedicalRecord.draft(session);
                    draftRecord.saveDraft(doctor.getFullName(), reason,
                            "Thể trạng chung ổn định, người bệnh tiếp xúc tốt, tim phổi bình thường",
                            "Z00.0 - Khám sức khỏe tổng quát định kỳ",
                            "Đang trong quá trình thăm khám lâm sàng",
                            "Theo dõi các chỉ số sinh hiệu và thực hiện xét nghiệm thường quy nếu cần",
                            "Đang kê đơn thuốc", null,
                            List.of(PrescriptionLine.create(draftRecord, null, "Paracetamol 500mg (Hạ sốt, giảm đau nhanh)", "500mg x 2 lần/ngày", 10, "Viên", "Uống khi đau/sốt", 1)),
                            null, null,
                            "120/80 mmHg", 74, new BigDecimal("36.7"), 98, new BigDecimal("64.5"), new BigDecimal("168.0"), "Chưa ghi nhận dị ứng");
                    medicalRecordRepository.save(draftRecord);
                }
            }

            // For COMPLETED: Sign medical record and seed a PENDING invoice ready for payment testing!
            if (scenario == QueueScenario.COMPLETED && medicalRecordRepository != null) {
                if (medicalRecordRepository.findBySession_Id(session.getId()).isEmpty()) {
                    MedicalRecord record = MedicalRecord.draft(session);
                    record.sign(doctor.getFullName(), reason, "Thể trạng bình thường, tim phổi tốt", "Z00.0 - Khám sức khỏe tổng quát",
                            "Sức khỏe tốt", "Tập thể dục đều đặn", null, null, List.of(), null, null,
                            "115/75 mmHg", 70, new BigDecimal("36.6"), 99, new BigDecimal("60.0"), new BigDecimal("165.0"), "Không dị ứng");
                    medicalRecordRepository.save(record);
                }

                if (invoiceRepository != null && !invoiceRepository.existsBySession_Id(session.getId())) {
                    String invCode = "INV-" + date.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + code;
                    long fee = service.getConsultationFee() > 0 ? service.getConsultationFee() : 150_000L;
                    Invoice invoice = Invoice.create(session, invCode, fee, "Tiếp đón Lê Thu Thảo");
                    invoice.addItem(InvoiceItem.consultation(service.getName(), fee));
                    // Intentionally keep PENDING so client can test payment flow!
                    invoiceRepository.save(invoice);
                }
            }
        }
    }

    private void ensureFutureAppointment(PatientAccount patient, PatientProfile profile, DoctorProfile doctorProfile,
                                         ClinicService service, LocalDate date, LocalTime time,
                                         String code, String reason) {
        Appointment app = appointmentRepository.findByAppointmentCode(code).orElse(null);
        if (app == null) {
            app = appointmentRepository.findByPatientProfileId(profile.getId()).stream()
                    .filter(a -> a.getAppointmentDate().equals(date) && a.getStartTime().equals(time))
                    .findFirst()
                    .orElse(null);
        }
        if (app != null) {
            return;
        }
        Appointment appointment = Appointment.create(patient, doctorProfile.getStaffAccount().getId(), profile,
                code, doctorProfile.getSpecialty(), doctorProfile.getStaffAccount().getFullName(), date, time, reason);
        appointment.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                service.getDurationMinutes(), service.requiresMedicalRecord());
        appointment = appointmentRepository.save(appointment);
        if (businessLogService != null) {
            businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", appointment.getId(),
                    null, appointment.getStatus().name(), "CREATE_APPOINTMENT", "SYSTEM", reason);
        }
    }

    private void ensureRescheduleScenario(PatientAccount patient, PatientProfile profile, DoctorProfile doc,
                                          ClinicService service, LocalDate targetDate, LocalTime targetTime, String appCode,
                                          String rescheduleReason, String timeOffReason) {
        if (doctorTimeOffRepository == null || rescheduleCaseRepository == null) return;
        Appointment app = appointmentRepository.findByAppointmentCode(appCode).orElse(null);
        if (app == null) {
            app = Appointment.create(patient, doc.getStaffAccount().getId(), profile,
                    appCode, doc.getSpecialty(), doc.getStaffAccount().getFullName(), targetDate, targetTime,
                    "Khám kiểm tra chuyên khoa");
            app.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                    service.getDurationMinutes(), service.requiresMedicalRecord());
            app = appointmentRepository.save(app);
            if (businessLogService != null) {
                businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", app.getId(),
                        null, app.getStatus().name(), "CREATE_APPOINTMENT", "SYSTEM", "Khám kiểm tra chuyên khoa");
            }
        }

        if (!doctorTimeOffRepository.existsActiveOnDate(doc.getId(), targetDate)) {
            doctorTimeOffRepository.save(DoctorTimeOff.create(doc, targetDate, targetDate, timeOffReason));
        }

        if (app.getStatus() == com.clinicone.appointment.AppointmentStatus.BOOKED
                && rescheduleCaseRepository.findByAppointmentIdAndStatus(app.getId(), com.clinicone.rescheduling.RescheduleCaseStatus.OPEN).isEmpty()) {
            rescheduleCaseRepository.save(RescheduleCase.open(app, rescheduleReason));
        }
    }

    private String demoAppointmentCode(LocalDate date, String scenario) {
        return "DMO-" + date.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + scenario;
    }

    private void ensurePastAppointmentOutcome(PatientAccount patient, PatientProfile profile, DoctorProfile doctor,
                                              ClinicService service, LocalDate date, LocalTime time, String code,
                                              String reason, boolean cancelled) {
        if (appointmentRepository.findByAppointmentCode(code).isPresent()) return;

        Appointment appointment = Appointment.create(patient, doctor.getStaffAccount().getId(), profile,
                code, doctor.getSpecialty(), doctor.getStaffAccount().getFullName(), date, time, reason);
        appointment.applyServiceSnapshot(service.getId(), service.getName(), service.getVisitType(),
                service.getDurationMinutes(), service.requiresMedicalRecord());
        if (cancelled) {
            appointment.cancel(reason, date.minusDays(1).atTime(16, 0).atZone(CLINIC_ZONE).toInstant());
        } else {
            appointment.markAbsent();
        }
        appointment = appointmentRepository.save(appointment);
        if (businessLogService != null) {
            businessLogService.recordTransition(UUID.randomUUID(), "APPOINTMENT", appointment.getId(),
                    null, appointment.getStatus().name(), "SEED_INITIALIZATION", "SYSTEM", reason);
        }
    }

    private LocalDate previousClinicDate(LocalDate date) {
        LocalDate previous = date.minusDays(1);
        while (previous.getDayOfWeek() == DayOfWeek.SATURDAY || previous.getDayOfWeek() == DayOfWeek.SUNDAY) {
            previous = previous.minusDays(1);
        }
        return previous;
    }

    private List<LocalDate> nextClinicDates(LocalDate start, int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        LocalDate date = start;
        while (dates.size() < count) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return List.copyOf(dates);
    }

    private void ensureReconciliationIncidents() {
        if (reconciliationIncidentRepository == null) return;
        boolean inc1Exists = reconciliationIncidentRepository.findAll().stream()
                .anyMatch(i -> "INC-2026-001".equalsIgnoreCase(i.getIncidentCode()));
        if (!inc1Exists) {
            ReconciliationIncident openInc = ReconciliationIncident.open("INC-2026-001", "APPOINTMENT",
                    UUID.randomUUID(), UUID.randomUUID(),
                    "Phát hiện trùng lịch do cập nhật ngoại lệ thời gian từ cổng đặt lịch đối tác", "coordinator");
            reconciliationIncidentRepository.save(openInc);
        }

        boolean inc2Exists = reconciliationIncidentRepository.findAll().stream()
                .anyMatch(i -> "INC-2026-002".equalsIgnoreCase(i.getIncidentCode()));
        if (!inc2Exists) {
            ReconciliationIncident closedInc = ReconciliationIncident.open("INC-2026-002", "EXAMINATION",
                    UUID.randomUUID(), UUID.randomUUID(),
                    "Báo nhầm hồ sơ người bệnh tại phòng khám Tai Mũi Họng", "coordinator");
            closedInc.close(ReconciliationAction.RETRY_BUSINESS_ACTION, ReconciliationReferenceType.BUSINESS_LOG,
                    "CL-20260810-TM01", "Đã đối soát thông tin CCCD và hợp nhất dữ liệu bệnh án chính xác", "coordinator",
                    Instant.now());
            reconciliationIncidentRepository.save(closedInc);
        }

        boolean inc3Exists = reconciliationIncidentRepository.findAll().stream()
                .anyMatch(i -> "INC-2026-003".equalsIgnoreCase(i.getIncidentCode()));
        if (!inc3Exists) {
            ReconciliationIncident payInc = ReconciliationIncident.open("INC-2026-003", "PAYMENT",
                    UUID.randomUUID(), UUID.randomUUID(),
                    "Lệch giao dịch chuyển khoản VietQR: POS-102 ghi nhận thành công nhưng ngân hàng phản hồi trễ", "coordinator");
            reconciliationIncidentRepository.save(payInc);
        }

        boolean inc4Exists = reconciliationIncidentRepository.findAll().stream()
                .anyMatch(i -> "INC-2026-004".equalsIgnoreCase(i.getIncidentCode()));
        if (!inc4Exists) {
            ReconciliationIncident holdInc = ReconciliationIncident.open("INC-2026-004", "REGISTRATION",
                    UUID.randomUUID(), UUID.randomUUID(),
                    "Hết hạn giữ chỗ 10 phút trên hệ thống nhưng bệnh nhân walk-in đã đến quầy số 02", "reception");
            reconciliationIncidentRepository.save(holdInc);
        }
    }

    private void ensureNotifications(PatientAccount patient) {
        if (patientNotificationRepository == null) return;
        List<PatientNotification> notifs = List.of(
                PatientNotification.appointmentCreated(patient.getId(), UUID.randomUUID(),
                        "CL-20260820-TQ01", "Khám Tổng Quát", "BS. CKII Nguyễn An", "20/08/2026", "08:30"),
                PatientNotification.appointmentReminder(patient.getId(), UUID.randomUUID(),
                        "CL-20260820-TQ01", "Khám Tổng Quát", "BS. CKII Nguyễn An", "20/08/2026", "08:30", 24),
                PatientNotification.recordSigned(patient.getId(), UUID.randomUUID(),
                        "PAST-REC-001", "BS. CKII Nguyễn An", "Khám Tổng Quát")
        );
        for (PatientNotification n : notifs) {
            if (patientNotificationRepository.findByEventKey(n.getEventKey()).isEmpty()) {
                patientNotificationRepository.save(n);
            }
        }
    }

    private record SeedMedication(String name, String dosage, int quantity, String unit, String instructions) {
        SeedMedication(String name, String dosage, int quantity, String instructions) {
            this(name, dosage, quantity, "Viên", instructions);
        }
    }

    private record VitalsRecord(
            String bloodPressure,
            Integer heartRate,
            BigDecimal temperature,
            Integer spO2,
            BigDecimal weight,
            BigDecimal height,
            String allergySummary
    ) {}
}
