package com.clinicone.invoice;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.OtpChallenge;
import com.clinicone.auth.OtpChallengeRepository;
import com.clinicone.auth.OtpPurpose;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.examination.PrescriptionLine;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.rescheduling.DoctorTimeOffRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PublicPatientDataLookupIntegrationTest {
    private static final String PHONE = "0900000017";
    private static final String OTP = "381924";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PatientAccountRepository accountRepository;
    @Autowired private PatientProfileRepository profileRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ExaminationSessionRepository sessionRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private OtpChallengeRepository otpChallengeRepository;
    @Autowired private DoctorTimeOffRepository doctorTimeOffRepository;

    @Test
    void doctorTimeOffWindowQueryLoadsForAvailabilityFiltering() {
        assertThat(doctorTimeOffRepository.findActiveWindows(List.of(UUID.randomUUID()),
                LocalDate.now(), LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void lookupReturnsInvoicesAndSignedPrescriptionsOnlyUnderTheirOwnProfile() throws Exception {
        PatientAccount account = accountRepository.saveAndFlush(new PatientAccount(PHONE,
                passwordEncoder.encode("test-password"), "Nguyen Test", AccountStatus.ACTIVE, false));
        PatientProfile self = profileRepository.saveAndFlush(PatientProfile.create(account, "Nguyen Test",
                "Ban than", LocalDate.of(1990, 1, 1), "Nam", PHONE, null, "Viet Nam", "Kinh",
                null, true));
        PatientProfile child = profileRepository.saveAndFlush(PatientProfile.create(account, "Nguyen Child",
                "Con", LocalDate.of(2015, 2, 3), "Nu", PHONE, null, "Viet Nam", "Kinh", null, false));

        createClinicalHistory(account, self, "INV-LOOKUP-SELF", "Chan doan self", "Thuoc self");
        createClinicalHistory(account, child, "INV-LOOKUP-CHILD", "Chan doan child", "Thuoc child");
        Instant now = Instant.now();
        otpChallengeRepository.saveAndFlush(new OtpChallenge("+84900000017", OtpPurpose.RECEIPT_LOOKUP,
                passwordEncoder.encode(OTP), now, now.plusSeconds(300)));

        var result = mockMvc.perform(post("/api/v1/public/patient-data/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"" + OTP + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode profiles = objectMapper.readTree(result.getResponse().getContentAsString()).path("profiles");
        JsonNode selfResult = findProfile(profiles, self.getId().toString());
        JsonNode childResult = findProfile(profiles, child.getId().toString());
        assertThat(selfResult.path("fullName").asText()).isEqualTo("Nguyen Test");
        assertThat(selfResult.path("invoices").get(0).path("invoiceCode").asText()).isEqualTo("INV-LOOKUP-SELF");
        assertThat(selfResult.path("medicalRecords").get(0).path("diagnosis").asText()).isEqualTo("Chan doan self");
        assertThat(selfResult.path("medicalRecords").get(0).path("prescriptionLines").get(0)
                .path("medicationName").asText()).isEqualTo("Thuoc self");
        assertThat(childResult.path("fullName").asText()).isEqualTo("Nguyen Child");
        assertThat(childResult.path("invoices").get(0).path("invoiceCode").asText()).isEqualTo("INV-LOOKUP-CHILD");
        assertThat(childResult.path("medicalRecords").get(0).path("diagnosis").asText()).isEqualTo("Chan doan child");
        assertThat(childResult.path("medicalRecords").get(0).path("prescriptionLines").get(0)
                .path("medicationName").asText()).isEqualTo("Thuoc child");
        assertThat(profiles.size()).isEqualTo(2);
    }

    private void createClinicalHistory(PatientAccount account, PatientProfile profile, String invoiceCode,
                                       String diagnosis, String medicationName) {
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.create(account, profile,
                UUID.randomUUID().toString().substring(0, 24), "Kham Tong quat", "BS. Test",
                LocalDate.now().plusDays(1), LocalTime.of(8, 30), "Kiem tra integration"));
        ExaminationSession session = sessionRepository.saveAndFlush(ExaminationSession.create(appointment));
        MedicalRecord record = MedicalRecord.draft(session);
        record.sign("BS. Test", "Kiem tra", "Kham", diagnosis, "On dinh", "Theo doi", null, null,
                List.of(PrescriptionLine.create(record, null, medicationName, "1 vien", 3, "vien", "Sau an", 1)));
        medicalRecordRepository.saveAndFlush(record);
        invoiceRepository.saveAndFlush(Invoice.create(session, invoiceCode, 100_000, "Quay tiep nhan"));
    }

    private JsonNode findProfile(JsonNode profiles, String profileId) {
        return StreamSupport.stream(profiles.spliterator(), false)
                .filter(profile -> profile.path("profileId").asText().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Profile not returned: " + profileId));
    }
}
