package com.clinicone.examination;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedicalRecordRepositoryPaginationIntegrationTest {
    private static final UUID DOCTOR_ID = UUID.fromString("1d7f1178-9b9c-4f0b-a7c1-a28a85d93f10");
    private static final Instant SIGNED_AT = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private PatientAccountRepository accountRepository;
    @Autowired
    private PatientProfileRepository profileRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private ExaminationSessionRepository sessionRepository;
    @Autowired
    private MedicalRecordRepository recordRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pagedHistoryUsesStableTieBreakerAndLegacyLookupFindsProfilelessRecords() {
        PatientAccount account = accountRepository.saveAndFlush(new PatientAccount(
                "090" + UUID.randomUUID().toString().replace("-", "").substring(0, 7),
                "test-hash", "Pagination Test", AccountStatus.ACTIVE, false));
        PatientProfile profile = profileRepository.saveAndFlush(PatientProfile.create(account,
                "Pagination Test", "Bản thân", LocalDate.of(1990, 1, 1), "Nam", null, null,
                "Việt Nam", "Kinh", null, true));

        List<UUID> recordIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Appointment appointment = appointmentRepository.saveAndFlush(Appointment.create(account, profile,
                    "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    "Nội tổng quát", "Bác sĩ kiểm thử",
                    LocalDate.of(2026, 8, 1), LocalTime.of(8, index), "Kiểm tra phân trang"));
            ExaminationSession session = sessionRepository.saveAndFlush(ExaminationSession.create(appointment));
            MedicalRecord record = recordRepository.saveAndFlush(MedicalRecord.signed(session, "Bác sĩ kiểm thử",
                    "Lý do", "Ghi nhận", "Chẩn đoán", "Kết luận", "Điều trị", null, null));
            recordIds.add(record.getId());
            jdbcTemplate.update("update medical_records set signed_at = ? where id = ?", SIGNED_AT, record.getId());
        }
        Appointment legacyAppointment = appointmentRepository.saveAndFlush(Appointment.create(account,
                "L" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "Nội tổng quát", "Bác sĩ kiểm thử", LocalDate.of(2026, 8, 1), LocalTime.of(9, 0),
                "Hồ sơ lịch sử chưa gắn profile"));
        ExaminationSession legacySession = sessionRepository.saveAndFlush(ExaminationSession.create(legacyAppointment));
        MedicalRecord legacyRecord = recordRepository.saveAndFlush(MedicalRecord.signed(legacySession,
                "Bác sĩ kiểm thử", "Lý do", "Ghi nhận", "Chẩn đoán cũ", "Kết luận", "Điều trị", null, null));
        jdbcTemplate.update("update medical_records set signed_at = ? where id = ?", SIGNED_AT, legacyRecord.getId());
        recordIds.add(legacyRecord.getId());

        List<UUID> actualPageSequence = new ArrayList<>();
        for (int page = 0; page < recordIds.size(); page++) {
            actualPageSequence.addAll(recordRepository.findSignedHistory(account.getId(), FROM, TO,
                    PageRequest.of(page, 1)).getContent().stream().map(MedicalRecord::getId).toList());
        }

        List<UUID> expectedSequence = recordIds.stream()
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();
        assertThat(actualPageSequence).containsExactlyElementsOf(expectedSequence);
        assertThat(recordRepository
                .findTop10BySession_Appointment_Patient_IdAndSession_Appointment_PatientProfileIsNullAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
                        account.getId()))
                .extracting(MedicalRecord::getId)
                .containsExactly(legacyRecord.getId());
    }

    @Test
    void doctorHistoryQueryReturnsOnlyItsSignedRecordsAndDetailLoadsPrescriptionLines() {
        PatientAccount account = accountRepository.saveAndFlush(new PatientAccount(
                "091" + UUID.randomUUID().toString().replace("-", "").substring(0, 7),
                "test-hash", "Doctor History Patient", AccountStatus.ACTIVE, false));
        PatientProfile profile = profileRepository.saveAndFlush(PatientProfile.create(account,
                "Doctor History Patient", "Báº£n thÃ¢n", LocalDate.of(1990, 1, 1), "Nam", null, null,
                "Viá»‡t Nam", "Kinh", null, true));
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.create(account, DOCTOR_ID,
                profile, "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "Ná»™i tá»•ng quÃ¡t", "BÃ¡c sÄ© kiá»ƒm thá»­", LocalDate.of(2026, 8, 1), LocalTime.of(8, 0),
                "LÃ½ do khÃ¡m"));
        ExaminationSession session = sessionRepository.saveAndFlush(ExaminationSession.create(appointment));
        MedicalRecord record = recordRepository.saveAndFlush(MedicalRecord.signed(session, "BÃ¡c sÄ© kiá»ƒm thá»­",
                "LÃ½ do", "Ghi nháº­n", "Cháº©n Ä‘oÃ¡n", "Káº¿t luáº­n", "Äiá»u trá»‹", null, null));
        jdbcTemplate.update("update medical_records set signed_at = ? where id = ?", SIGNED_AT, record.getId());

        var page = recordRepository.findSignedHistoryForDoctor(DOCTOR_ID, "Doctor History Patient",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1),
                PageRequest.of(0, 20));
        var unfilteredPage = recordRepository.findSignedHistoryForDoctor(DOCTOR_ID, "", null, null,
                PageRequest.of(0, 20));
        var detail = recordRepository.findByIdAndSession_Appointment_DoctorStaffIdAndSignedAtIsNotNull(
                record.getId(), DOCTOR_ID);
        var otherDoctorPage = recordRepository.findSignedHistoryForDoctor(UUID.randomUUID(), "",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(MedicalRecord::getId).containsExactly(record.getId());
        assertThat(unfilteredPage.getContent()).extracting(MedicalRecord::getId).containsExactly(record.getId());
        assertThat(detail).isPresent();
        assertThat(otherDoctorPage.getContent()).isEmpty();
    }
}
