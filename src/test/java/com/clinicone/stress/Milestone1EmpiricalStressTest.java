package com.clinicone.stress;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.appointment.AppointmentStatus;
import com.clinicone.audit.BusinessLog;
import com.clinicone.audit.BusinessLogController;
import com.clinicone.audit.BusinessLogIntegrityJob;
import com.clinicone.audit.BusinessLogPageResponse;
import com.clinicone.audit.BusinessLogRepository;
import com.clinicone.audit.BusinessLogResponse;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.notification.SmsDelivery;
import com.clinicone.notification.SmsDeliveryAdminController;
import com.clinicone.notification.SmsDeliveryRepository;
import com.clinicone.notification.SmsDeliveryResponse;
import com.clinicone.notification.SmsDeliveryService;
import com.clinicone.notification.SmsDeliveryStatus;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.reconciliation.ReconciliationIncident;
import com.clinicone.reconciliation.ReconciliationIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Milestone1EmpiricalStressTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PatientAccountRepository patientAccountRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private BusinessLogRepository businessLogRepository;

    @Autowired
    private ReconciliationIncidentRepository reconciliationIncidentRepository;

    @Autowired
    private QueueTicketRepository queueTicketRepository;

    @Autowired
    private SmsDeliveryRepository smsDeliveryRepository;

    @Autowired
    private SmsDeliveryService smsDeliveryService;

    @Autowired
    private BusinessLogService businessLogService;

    @Autowired
    private BusinessLogIntegrityJob businessLogIntegrityJob;

    private MockMvc smsMockMvc;
    private MockMvc auditMockMvc;

    @BeforeEach
    void setupMockMvc() {
        SmsDeliveryAdminController smsController = new SmsDeliveryAdminController(smsDeliveryService);
        smsMockMvc = MockMvcBuilders.standaloneSetup(smsController).build();

        BusinessLogController auditController = new BusinessLogController(businessLogService, businessLogIntegrityJob);
        auditMockMvc = MockMvcBuilders.standaloneSetup(auditController).build();
    }

    @Nested
    @DisplayName("1. AppointmentRepository Flexible Search Harmony Tests")
    class AppointmentSearchHarmonyTests {

        private LocalDate targetDate;
        private Appointment appt1;
        private Appointment appt2;
        private Appointment appt3;
        private Appointment apptOtherDate;
        private Appointment apptCancelled;

        @BeforeEach
        void seedAppointments() {
            targetDate = LocalDate.of(2026, 8, 20);

            // 1. Patient Account only: "Nguyễn Văn An", phone "0912345678", code "CL-20260820-TQ01"
            PatientAccount acc1 = new PatientAccount("0912345678", "hash1", "Nguyễn Văn An", AccountStatus.ACTIVE, false);
            patientAccountRepository.save(acc1);

            appt1 = Appointment.create(acc1, "CL-20260820-TQ01", "Nội tổng quát", "BS. Minh",
                    targetDate, LocalTime.of(8, 0), "Khám định kỳ");
            appointmentRepository.save(appt1);

            // 2. Temporary Patient Profile only (Walk-in): "Trần Thị Bích Loan", phone "0987654321", code "CL-20260820-HH02"
            PatientProfile prof2 = PatientProfile.createTemporary("Trần Thị Bích Loan", LocalDate.of(1990, 5, 15),
                    "Nữ", "0987654321", null, "Việt Nam", "Kinh", "123 Lê Lợi");
            patientProfileRepository.save(prof2);

            appt2 = Appointment.createTemporary(prof2, UUID.randomUUID(), "CL-20260820-HH02",
                    "Hô hấp", "BS. Lan", targetDate, LocalTime.of(8, 30), "Ho kéo dài");
            appt2.checkIn(); // status CHECKED_IN
            appointmentRepository.save(appt2);

            // 3. Both Account & Profile: Account "Lê Hoàng Long" (0933111222), Profile "Lê Hoàng Long (Con)" (0944333222), code "LH-20260820-TM03"
            PatientAccount acc3 = new PatientAccount("0933111222", "hash3", "Lê Hoàng Long", AccountStatus.ACTIVE, false);
            patientAccountRepository.save(acc3);

            PatientProfile prof3 = PatientProfile.create(acc3, "Lê Hoàng Long (Con)", "Con", LocalDate.of(2018, 1, 1),
                    "Nam", "0944333222", "123456789012", "Việt Nam", "Kinh", "456 Nguyễn Trãi", false);
            patientProfileRepository.save(prof3);

            appt3 = Appointment.create(acc3, prof3, "LH-20260820-TM03", "Tim mạch", "BS. Hùng",
                    targetDate, LocalTime.of(9, 0), "Khám tim");
            appt3.markAbsent(); // status ABSENT
            appointmentRepository.save(appt3);

            // 4. Different Date (2026-08-21)
            apptOtherDate = Appointment.create(acc1, "CL-20260821-TQ04", "Nội tổng quát", "BS. Minh",
                    targetDate.plusDays(1), LocalTime.of(8, 0), "Khám lại");
            appointmentRepository.save(apptOtherDate);

            // 5. Cancelled on target date
            apptCancelled = Appointment.create(acc1, "CL-20260820-TQ05", "Nội tổng quát", "BS. Minh",
                    targetDate, LocalTime.of(9, 30), "Bận việc");
            apptCancelled.cancel("Bận việc đột xuất");
            appointmentRepository.save(apptCancelled);
        }

        @Test
        @DisplayName("Search by full name with various cases: lowercase, UPPERCASE, Mixed case")
        void searchByFullNameCaseInsensitive() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            // Lowercase search
            List<Appointment> resultsLower = appointmentRepository.findReceptionCandidatesByStatuses("nguyễn văn an", targetDate, statuses);
            assertThat(resultsLower).hasSize(1);
            assertThat(resultsLower.get(0).getAppointmentCode()).isEqualTo("CL-20260820-TQ01");

            // Uppercase search
            List<Appointment> resultsUpper = appointmentRepository.findReceptionCandidatesByStatuses("NGUYỄN VĂN AN", targetDate, statuses);
            assertThat(resultsUpper).hasSize(1);
            assertThat(resultsUpper.get(0).getAppointmentCode()).isEqualTo("CL-20260820-TQ01");

            // Substring lowercase & uppercase
            List<Appointment> subLower = appointmentRepository.findReceptionCandidatesByStatuses("văn an", targetDate, statuses);
            assertThat(subLower).hasSize(1);

            List<Appointment> subUpper = appointmentRepository.findReceptionCandidatesByStatuses("VĂN", targetDate, statuses);
            assertThat(subUpper).hasSize(1);
        }

        @Test
        @DisplayName("Search by temporary patient profile full name (Walk-in)")
        void searchByTemporaryProfileFullName() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            List<Appointment> results = appointmentRepository.findReceptionCandidatesByStatuses("bích loan", targetDate, statuses);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getAppointmentCode()).isEqualTo("CL-20260820-HH02");

            List<Appointment> resultsUpper = appointmentRepository.findReceptionCandidatesByStatuses("TRẦN THỊ", targetDate, statuses);
            assertThat(resultsUpper).hasSize(1);
            assertThat(resultsUpper.get(0).getAppointmentCode()).isEqualTo("CL-20260820-HH02");
        }

        @Test
        @DisplayName("Search by patient profile child name vs account name")
        void searchByDependentProfileName() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            // Profile full name "(Con)"
            List<Appointment> childResults = appointmentRepository.findReceptionCandidatesByStatuses("(Con)", targetDate, statuses);
            assertThat(childResults).hasSize(1);
            assertThat(childResults.get(0).getAppointmentCode()).isEqualTo("LH-20260820-TM03");

            // Account full name "Lê Hoàng Long"
            List<Appointment> parentResults = appointmentRepository.findReceptionCandidatesByStatuses("hoàng long", targetDate, statuses);
            assertThat(parentResults).hasSize(1);
            assertThat(parentResults.get(0).getAppointmentCode()).isEqualTo("LH-20260820-TM03");
        }

        @Test
        @DisplayName("Search by phone number across patient accounts and patient profiles")
        void searchByPhoneNumber() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            // Exact account phone
            List<Appointment> phone1 = appointmentRepository.findReceptionCandidatesByStatuses("0912345678", targetDate, statuses);
            assertThat(phone1).hasSize(1);
            assertThat(phone1.get(0).getAppointmentCode()).isEqualTo("CL-20260820-TQ01");

            // Substring account phone
            List<Appointment> phoneSub = appointmentRepository.findReceptionCandidatesByStatuses("345678", targetDate, statuses);
            assertThat(phoneSub).hasSize(1);

            // Temporary profile phone
            List<Appointment> phoneProf = appointmentRepository.findReceptionCandidatesByStatuses("0987654321", targetDate, statuses);
            assertThat(phoneProf).hasSize(1);
            assertThat(phoneProf.get(0).getAppointmentCode()).isEqualTo("CL-20260820-HH02");

            // Dependent profile phone
            List<Appointment> phoneDep = appointmentRepository.findReceptionCandidatesByStatuses("0944333222", targetDate, statuses);
            assertThat(phoneDep).hasSize(1);
            assertThat(phoneDep.get(0).getAppointmentCode()).isEqualTo("LH-20260820-TM03");
        }

        @Test
        @DisplayName("Search by appointment code: case-insensitive & substring")
        void searchByAppointmentCode() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            // Exact code
            List<Appointment> codeExact = appointmentRepository.findReceptionCandidatesByStatuses("CL-20260820-TQ01", targetDate, statuses);
            assertThat(codeExact).hasSize(1);

            // Lowercase code
            List<Appointment> codeLower = appointmentRepository.findReceptionCandidatesByStatuses("cl-20260820-tq01", targetDate, statuses);
            assertThat(codeLower).hasSize(1);

            // Substring code "TQ01"
            List<Appointment> codeSub = appointmentRepository.findReceptionCandidatesByStatuses("tq01", targetDate, statuses);
            assertThat(codeSub).hasSize(1);

            // Substring code "LH-2026"
            List<Appointment> codeLH = appointmentRepository.findReceptionCandidatesByStatuses("lh-2026", targetDate, statuses);
            assertThat(codeLH).hasSize(1);
            assertThat(codeLH.get(0).getAppointmentCode()).isEqualTo("LH-20260820-TM03");
        }

        @Test
        @DisplayName("Verify date isolation and status filtering guardrails")
        void dateAndStatusGuardrails() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            // Should not return tomorrow's appointment when searching on target date
            List<Appointment> dateFilter = appointmentRepository.findReceptionCandidatesByStatuses("0912345678", targetDate, statuses);
            assertThat(dateFilter).hasSize(1);
            assertThat(dateFilter.get(0).getAppointmentCode()).isEqualTo("CL-20260820-TQ01");

            // Should not return CANCELLED appointment
            List<Appointment> cancelledFilter = appointmentRepository.findReceptionCandidatesByStatuses("TQ05", targetDate, statuses);
            assertThat(cancelledFilter).isEmpty();

            // Single status overload: findReceptionCandidates(query, date, status)
            List<Appointment> bookedOnly = appointmentRepository.findReceptionCandidates("CL-2026", targetDate, AppointmentStatus.BOOKED);
            assertThat(bookedOnly).hasSize(1);
            assertThat(bookedOnly.get(0).getAppointmentCode()).isEqualTo("CL-20260820-TQ01");

            List<Appointment> checkedInOnly = appointmentRepository.findReceptionCandidates("CL-2026", targetDate, AppointmentStatus.CHECKED_IN);
            assertThat(checkedInOnly).hasSize(1);
            assertThat(checkedInOnly.get(0).getAppointmentCode()).isEqualTo("CL-20260820-HH02");
        }

        @Test
        @DisplayName("Verify chronological ordering by startTime ASC")
        void chronologicalOrdering() {
            List<AppointmentStatus> statuses = List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN, AppointmentStatus.ABSENT);

            // Query matching all 3 appointments on targetDate (e.g. "20260820")
            List<Appointment> allMatches = appointmentRepository.findReceptionCandidatesByStatuses("20260820", targetDate, statuses);
            assertThat(allMatches).hasSize(3);
            assertThat(allMatches.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(allMatches.get(1).getStartTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(allMatches.get(2).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        }
    }

    @Nested
    @DisplayName("2. DTO Serialization & Endpoint Tests via MockMvc")
    class DtoSerializationAndEndpointTests {

        @Test
        @DisplayName("SmsDeliveryAdminController GET returns message in JSON response")
        void smsDeliveryListRecentContainsMessage() throws Exception {
            UUID patientId = UUID.randomUUID();
            Instant now = Instant.parse("2026-08-20T10:00:00Z");
            String smsMessage = "ClinicOne: Lịch hẹn LH-20260820-001 đã được xác nhận lúc 09:00 ngày 20/08/2026.";

            SmsDelivery sms = SmsDelivery.pending(patientId, "APPOINTMENT_CONFIRMED:001", "0912345678", smsMessage, now);
            smsDeliveryRepository.save(sms);

            smsMockMvc.perform(get("/api/v1/admin/notifications/sms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].eventKey").value("APPOINTMENT_CONFIRMED:001"))
                    .andExpect(jsonPath("$[0].phone").value("0912345678"))
                    .andExpect(jsonPath("$[0].message").value(smsMessage));
        }

        @Test
        @DisplayName("BusinessLogController GET /search returns hash in JSON response")
        void businessLogSearchReturnsHash() throws Exception {
            UUID apptId = UUID.randomUUID();
            businessLogService.recordActivity(UUID.randomUUID(), "APPOINTMENT", apptId,
                    "BOOKED", "CHECKED_IN", "CHECK_IN", "BS. Minh", "Check-in tai quay");

            auditMockMvc.perform(get("/api/v1/admin/audit/search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].hash").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].actor").value("BS. Minh"))
                    .andExpect(jsonPath("$.items[0].nextStatus").value("CHECKED_IN"));
        }

        @Test
        @DisplayName("Backward compatibility constructor of SmsDeliveryResponse leaves message null")
        void smsDeliveryResponseBackwardCompatibilityConstructor() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.parse("2026-08-20T10:00:00Z");
            SmsDeliveryResponse legacy = new SmsDeliveryResponse(id, "KEY", "0912345678", SmsDeliveryStatus.PENDING,
                    0, now, null, null, now);
            assertThat(legacy.message()).isNull();
        }

        @Test
        @DisplayName("Backward compatibility constructor of BusinessLogResponse leaves hash null")
        void businessLogResponseBackwardCompatibilityConstructor() {
            UUID id = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            Instant now = Instant.parse("2026-08-20T10:00:00Z");
            BusinessLogResponse legacy = new BusinessLogResponse(id, eventId, "APPOINTMENT", entityId, "BOOKED", "CHECKED_IN",
                    "CHECK_IN", "STAFF", "Reason", now);
            assertThat(legacy.hash()).isNull();
        }
    }

    @Nested
    @DisplayName("3. BusinessLogService Flexible Search & Integrity Tests")
    class BusinessLogFlexibleSearchTests {

        @Test
        @DisplayName("Recent search with empty identifier retrieves all logs descending")
        void searchRecentLogsDescending() {
            UUID apptId1 = UUID.randomUUID();
            UUID apptId2 = UUID.randomUUID();

            businessLogService.recordActivity(UUID.randomUUID(), "APPOINTMENT", apptId1, null, "BOOKED", "CREATE", "PATIENT", "Đặt lịch 1");
            businessLogService.recordActivity(UUID.randomUUID(), "APPOINTMENT", apptId2, null, "BOOKED", "CREATE", "PATIENT", "Đặt lịch 2");

            BusinessLogPageResponse page = businessLogService.search(null, null, 0, 50);
            assertThat(page.items().size()).isGreaterThanOrEqualTo(2);
            assertThat(page.items()).extracting(BusinessLogResponse::entityId).contains(apptId1, apptId2);
            assertThat(page.page()).isEqualTo(0);
            assertThat(page.size()).isEqualTo(50);
        }

        @Test
        @DisplayName("Search by appointment code resolves to appointment UUID")
        void searchByAppointmentCode() {
            PatientAccount acc = new PatientAccount("0911223344", "hash", "Nguyễn Thử Nghiệm", AccountStatus.ACTIVE, false);
            patientAccountRepository.save(acc);
            Appointment appt = Appointment.create(acc, "CL-20260820-SR01", "Nội", "BS. Tuấn",
                    LocalDate.of(2026, 8, 20), LocalTime.of(10, 0), "Khám");
            appointmentRepository.save(appt);

            businessLogService.recordActivity(UUID.randomUUID(), "APPOINTMENT", appt.getId(), "BOOKED", "CHECKED_IN", "CHECK_IN", "STAFF", "Check in");

            BusinessLogPageResponse response = businessLogService.search(null, "CL-20260820-SR01", 0, 10);
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).entityId()).isEqualTo(appt.getId());
            assertThat(response.items().get(0).hash()).isNotNull();
        }

        @Test
        @DisplayName("Search with non-existent code returns empty page gracefully without throwing")
        void searchNonExistentCodeGracefully() {
            BusinessLogPageResponse response = businessLogService.search(null, "LH-NON-EXISTENT-CODE", 0, 10);
            assertThat(response.items()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Pagination bounds validation")
        void paginationBoundsValidation() {
            assertThatThrownBy(() -> businessLogService.search(null, null, -1, 50))
                    .isInstanceOf(com.clinicone.auth.AuthException.class)
                    .hasMessageContaining("Cần chọn đối tượng hợp lệ");

            assertThatThrownBy(() -> businessLogService.search(null, null, 0, 0))
                    .isInstanceOf(com.clinicone.auth.AuthException.class);

            assertThatThrownBy(() -> businessLogService.search(null, null, 0, 101))
                    .isInstanceOf(com.clinicone.auth.AuthException.class);
        }

        @Test
        @DisplayName("Integrity job generates SC-YYYYMM-XXX incident codes and zero UUID leakage in reasons")
        void integrityJobIncidentGenerationFormat() {
            PatientAccount acc = new PatientAccount("0955667788", "hash", "Phạm Văn Đồng", AccountStatus.ACTIVE, false);
            patientAccountRepository.save(acc);
            Appointment appt = Appointment.create(acc, "CL-20260820-INT1", "Nội", "BS. Tuấn",
                    LocalDate.of(2026, 8, 20), LocalTime.of(10, 0), "Khám");
            appointmentRepository.save(appt);

            // Record log claiming CHECKED_IN while appointment is actually BOOKED
            businessLogService.recordActivity(UUID.randomUUID(), "APPOINTMENT", appt.getId(), "BOOKED", "CHECKED_IN", "CHECK_IN", "STAFF", "Check in");

            BusinessLogIntegrityJob.IntegrityCheckResult result = businessLogIntegrityJob.runOnce();
            assertThat(result.incidentsOpened()).isGreaterThanOrEqualTo(1);

            List<ReconciliationIncident> incidents = reconciliationIncidentRepository.findAll();
            assertThat(incidents).isNotEmpty();

            Pattern incidentCodePattern = Pattern.compile("^SC-\\d{6}-[A-Z0-9]{6}$");
            for (ReconciliationIncident inc : incidents) {
                assertThat(inc.getIncidentCode()).matches(incidentCodePattern);
                // Reason must not contain 36-char raw UUID
                assertThat(inc.getReason()).doesNotMatch(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
                // Reason should mention appointmentCode and patient name
                if ("APPOINTMENT".equals(inc.getEntityType())) {
                    assertThat(inc.getReason()).contains("CL-20260820-INT1");
                    assertThat(inc.getReason()).contains("Phạm Văn Đồng");
                }
            }
        }
    }
}
