package com.clinicone.audit;

import com.clinicone.appointment.Appointment;
import com.clinicone.appointment.AppointmentRepository;
import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.ExaminationSessionStatus;
import com.clinicone.notification.SmsDelivery;
import com.clinicone.notification.SmsDeliveryResponse;
import com.clinicone.notification.SmsDeliveryStatus;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.queue.ClinicRoom;
import com.clinicone.queue.QueueTicket;
import com.clinicone.queue.QueueTicketRepository;
import com.clinicone.reconciliation.ReconciliationIncident;
import com.clinicone.reconciliation.ReconciliationIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Adversarial Verification Test Suite for Milestone 1:
 * - Search API Corner Cases (empty, whitespace, malformed, non-existent, valid UUID, valid code)
 * - Pagination Boundary Validation (page < 0, size < 1, size > 100, valid boundaries)
 * - EntityType filtering (null, empty, whitespace, specific)
 * - DTO hash & message field preservation and backward compatibility
 * - Incident code format & Discrepancy reason zero-UUID leak verification
 */
class Milestone1AdversarialVerificationTest {

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern INCIDENT_CODE_PATTERN = Pattern.compile("^SC-\\d{6}-[A-Z0-9]{6}$");

    private BusinessLogRepository logRepository;
    private ReconciliationIncidentRepository incidentRepository;
    private AppointmentRepository appointmentRepository;
    private QueueTicketRepository queueTicketRepository;
    private ExaminationSessionRepository examinationSessionRepository;
    private BusinessLogService logService;
    private BusinessLogController logController;
    private BusinessLogIntegrityJob integrityJob;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        logRepository = mock(BusinessLogRepository.class);
        incidentRepository = mock(ReconciliationIncidentRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        queueTicketRepository = mock(QueueTicketRepository.class);
        examinationSessionRepository = mock(ExaminationSessionRepository.class);

        logService = BusinessLogService.builder()
                .repository(logRepository)
                .reconciliationRepository(incidentRepository)
                .appointmentRepository(appointmentRepository)
                .build();

        integrityJob = new BusinessLogIntegrityJob(
                logRepository,
                incidentRepository,
                appointmentRepository,
                queueTicketRepository,
                examinationSessionRepository,
                fixedClock
        );

        logController = new BusinessLogController(logService, integrityJob);
    }

    @Nested
    @DisplayName("1. Search API Corner Cases & Identifier Resolution")
    class SearchCornerCases {

        @ParameterizedTest(name = "Empty or whitespace identifier: \"{0}\"")
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        void emptyOrWhitespaceIdentifierReturnsRecentLogs(String emptyIdentifier) {
            PageRequest pageable = PageRequest.of(0, 50);
            when(logRepository.findAllByOrderByOccurredAtDescIdDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            BusinessLogPageResponse response = logService.search(null, emptyIdentifier, 0, 50);

            assertThat(response).isNotNull();
            assertThat(response.items()).isEmpty();
            verify(logRepository).findAllByOrderByOccurredAtDescIdDesc(pageable);
            verifyNoInteractions(appointmentRepository);
        }

        @Test
        void nullIdentifierWithEntityTypeQueriesByTypeDescending() {
            PageRequest pageable = PageRequest.of(0, 50);
            when(logRepository.findByEntityTypeOrderByOccurredAtDescIdDesc("APPOINTMENT", pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            BusinessLogPageResponse response = logService.search("APPOINTMENT", null, 0, 50);

            assertThat(response).isNotNull();
            verify(logRepository).findByEntityTypeOrderByOccurredAtDescIdDesc("APPOINTMENT", pageable);
        }

        @ParameterizedTest(name = "Malformed UUID: \"{0}\"")
        @ValueSource(strings = {
                "not-a-uuid",
                "12345",
                "0b75cfa2-0c4c-4039-9541",
                "0b75cfa2-0c4c-4039-9541-9c30c3a2fbe9-extra",
                "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
                "!!!@@@###$$$"
        })
        void malformedUuidSafelyFallsBackToAppointmentCodeLookup(String malformed) {
            when(appointmentRepository.findByAppointmentCode(malformed.trim()))
                    .thenReturn(Optional.empty());

            BusinessLogPageResponse response = logService.search(null, malformed, 0, 50);

            assertThat(response).isNotNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
            verify(appointmentRepository).findByAppointmentCode(malformed.trim());
            verify(logRepository, never()).findByEntityIdOrderByOccurredAtDescIdDesc(any(), any());
        }

        @Test
        void nonExistentAppointmentCodeReturnsEmptyPageGracefully() {
            String nonExistentCode = "CL-20260820-9999";
            when(appointmentRepository.findByAppointmentCode(nonExistentCode))
                    .thenReturn(Optional.empty());

            BusinessLogPageResponse response = logService.search(null, nonExistentCode, 0, 50);

            assertThat(response).isNotNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
            assertThat(response.totalPages()).isEqualTo(0);
            assertThat(response.last()).isTrue();
        }

        @Test
        void validUuidWithoutEntityTypeQueriesByEntityId() {
            UUID entityId = UUID.randomUUID();
            PageRequest pageable = PageRequest.of(0, 50);
            when(logRepository.findByEntityIdOrderByOccurredAtDescIdDesc(entityId, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            BusinessLogPageResponse response = logService.search(null, entityId.toString(), 0, 50);

            assertThat(response).isNotNull();
            verify(logRepository).findByEntityIdOrderByOccurredAtDescIdDesc(entityId, pageable);
            verifyNoInteractions(appointmentRepository);
        }

        @Test
        void validUuidWithEntityTypeQueriesByEntityTypeAndEntityId() {
            UUID entityId = UUID.randomUUID();
            PageRequest pageable = PageRequest.of(0, 50);
            when(logRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("QUEUE_TICKET", entityId, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            BusinessLogPageResponse response = logService.search("QUEUE_TICKET", entityId.toString(), 0, 50);

            assertThat(response).isNotNull();
            verify(logRepository).findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("QUEUE_TICKET", entityId, pageable);
            verifyNoInteractions(appointmentRepository);
        }

        @Test
        void validAppointmentCodeResolvesUuidAndDefaultsEntityTypeToAppointment() {
            UUID apptId = UUID.randomUUID();
            Appointment appt = mock(Appointment.class);
            when(appt.getId()).thenReturn(apptId);
            when(appointmentRepository.findByAppointmentCode("CL-20260820-TQ01"))
                    .thenReturn(Optional.of(appt));

            PageRequest pageable = PageRequest.of(0, 50);
            when(logRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("APPOINTMENT", apptId, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            BusinessLogPageResponse response = logService.search(null, "CL-20260820-TQ01", 0, 50);

            assertThat(response).isNotNull();
            verify(logRepository).findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("APPOINTMENT", apptId, pageable);
        }

        @Test
        void controllerResolvesEffectiveIdentifierCorrectly() {
            PageRequest pageable = PageRequest.of(0, 50);
            when(logRepository.findAllByOrderByOccurredAtDescIdDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            // Both identifier and entityId are null
            BusinessLogPageResponse r1 = logController.search(null, null, null, 0, 50);
            assertThat(r1).isNotNull();

            // identifier is empty, entityId provided (backward compatibility)
            UUID legacyId = UUID.randomUUID();
            when(logRepository.findByEntityIdOrderByOccurredAtDescIdDesc(legacyId, pageable))
                    .thenReturn(new PageImpl<>(List.of()));
            BusinessLogPageResponse r2 = logController.search(null, "", legacyId.toString(), 0, 50);
            assertThat(r2).isNotNull();
            verify(logRepository).findByEntityIdOrderByOccurredAtDescIdDesc(legacyId, pageable);

            // identifier is provided, takes precedence over entityId
            when(appointmentRepository.findByAppointmentCode("CL-20260820-001"))
                    .thenReturn(Optional.empty());
            BusinessLogPageResponse r3 = logController.search(null, "CL-20260820-001", legacyId.toString(), 0, 50);
            assertThat(r3).isNotNull();
            verify(appointmentRepository).findByAppointmentCode("CL-20260820-001");
        }
    }

    @Nested
    @DisplayName("2. Pagination Boundaries")
    class PaginationBoundaries {

        @Test
        void rejectNegativePage() {
            assertThatThrownBy(() -> logService.search(null, null, -1, 50))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ae.getCode()).isEqualTo("AUDIT_PAGE_INVALID");
                    });
        }

        @Test
        void rejectZeroSize() {
            assertThatThrownBy(() -> logService.search(null, null, 0, 0))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        void rejectNegativeSize() {
            assertThatThrownBy(() -> logService.search(null, null, 0, -5))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        void rejectSizeOverHundred() {
            assertThatThrownBy(() -> logService.search(null, null, 0, 101))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        void acceptBoundarySizesOneAndHundred() {
            PageRequest p1 = PageRequest.of(0, 1);
            PageRequest p100 = PageRequest.of(0, 100);
            when(logRepository.findAllByOrderByOccurredAtDescIdDesc(p1)).thenReturn(new PageImpl<>(List.of()));
            when(logRepository.findAllByOrderByOccurredAtDescIdDesc(p100)).thenReturn(new PageImpl<>(List.of()));

            BusinessLogPageResponse r1 = logService.search(null, null, 0, 1);
            assertThat(r1).isNotNull();

            BusinessLogPageResponse r100 = logService.search(null, null, 0, 100);
            assertThat(r100).isNotNull();
        }

        @Test
        void acceptLargePageNumber() {
            PageRequest largePage = PageRequest.of(9999, 50);
            when(logRepository.findAllByOrderByOccurredAtDescIdDesc(largePage)).thenReturn(new PageImpl<>(List.of(mock(BusinessLog.class)), largePage, 500000));

            BusinessLogPageResponse response = logService.search(null, null, 9999, 50);
            assertThat(response).isNotNull();
            assertThat(response.page()).isEqualTo(9999);
        }
    }

    @Nested
    @DisplayName("3. DTO Hash and SMS Message Field Verification")
    class DtoVerification {

        @Test
        void businessLogResponseMapsHashField() {
            UUID id = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();

            BusinessLog log = BusinessLog.transition(eventId, "APPOINTMENT", entityId, "BOOKED", "CHECKED_IN",
                    "CHECK_IN", "STAFF", "Reason");
            log.seal(null); // computes SHA-256

            BusinessLogResponse response = BusinessLogResponse.from(log);

            assertThat(response.hash()).isNotNull();
            assertThat(response.hash()).matches("[0-9a-f]{64}");
            assertThat(response.entityType()).isEqualTo("APPOINTMENT");
        }

        @Test
        void businessLogResponseBackwardCompatibleConstructorDefaultsHashToNull() {
            UUID id = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();

            BusinessLogResponse response = new BusinessLogResponse(
                    id, eventId, "APPOINTMENT", entityId, "BOOKED", "CHECKED_IN",
                    "CHECK_IN", "STAFF", "Reason", Instant.now()
            );

            assertThat(response.hash()).isNull();
            assertThat(response.actor()).isEqualTo("STAFF");
        }

        @Test
        void smsDeliveryResponseMapsMessageField() {
            UUID patientAccountId = UUID.randomUUID();
            SmsDelivery delivery = SmsDelivery.pending(patientAccountId, "APPOINTMENT_REMINDER", "0912345678",
                    "Lich hen CL-20260820-001 tai Phong HH-01 luc 08:30", Instant.now());

            SmsDeliveryResponse response = SmsDeliveryResponse.from(delivery);

            assertThat(response.message()).isEqualTo("Lich hen CL-20260820-001 tai Phong HH-01 luc 08:30");
            assertThat(response.eventKey()).isEqualTo("APPOINTMENT_REMINDER");
            assertThat(response.phone()).isEqualTo("0912345678");
            assertThat(response.status()).isEqualTo(SmsDeliveryStatus.PENDING);
        }

        @Test
        void smsDeliveryResponseBackwardCompatibleConstructorDefaultsMessageToNull() {
            UUID id = UUID.randomUUID();
            SmsDeliveryResponse response = new SmsDeliveryResponse(
                    id, "APPOINTMENT_REMINDER", "0912345678", SmsDeliveryStatus.SENT,
                    1, Instant.now(), Instant.now(), null, Instant.now()
            );

            assertThat(response.message()).isNull();
            assertThat(response.eventKey()).isEqualTo("APPOINTMENT_REMINDER");
        }
    }

    @Nested
    @DisplayName("4. Integrity Job Incident Codes & Reasons (Zero Raw UUID Leak Audit)")
    class IntegrityJobLeakCheck {

        @Test
        void incidentCodeFormatMatchesStandardAndHasNoUuid() throws Exception {
            UUID apptId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            BusinessLog entry = BusinessLog.transition(eventId, "APPOINTMENT", apptId,
                    "BOOKED", "CHECKED_IN", "CHECK_IN", "patient", null);

            when(logRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
            when(appointmentRepository.findById(apptId)).thenReturn(Optional.empty()); // causes mismatch (actual == null)

            ArgumentCaptor<ReconciliationIncident> captor = ArgumentCaptor.forClass(ReconciliationIncident.class);

            integrityJob.runOnce();

            verify(incidentRepository).save(captor.capture());
            ReconciliationIncident incident = captor.getValue();

            // Verify incident code format SC-YYYYMM-XXXXXX
            assertThat(incident.getIncidentCode()).matches(INCIDENT_CODE_PATTERN);
            assertThat(incident.getIncidentCode().length()).isLessThanOrEqualTo(40);
            assertThat(incident.getIncidentCode()).startsWith("SC-202608-");
            assertThat(UUID_PATTERN.matcher(incident.getIncidentCode()).find()).isFalse();
        }

        @Test
        void appointmentDiscrepancyReasonContainsClinicalContextAndZeroRawUuids() throws Exception {
            UUID apptId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            BusinessLog entry = BusinessLog.transition(eventId, "APPOINTMENT", apptId,
                    "BOOKED", "CHECKED_IN", "CHECK_IN", "patient", null);

            PatientAccount patient = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
            Appointment appt = Appointment.create(patient, "CL-20260820-TQ01", "Nội tổng quát", "BS. An",
                    LocalDate.of(2026, 8, 20), LocalTime.of(8, 30), "Đau đầu");
            setField(appt, "id", apptId);

            when(logRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
            when(appointmentRepository.findById(apptId)).thenReturn(Optional.of(appt)); // status is BOOKED, log is CHECKED_IN

            ArgumentCaptor<ReconciliationIncident> captor = ArgumentCaptor.forClass(ReconciliationIncident.class);

            integrityJob.runOnce();

            verify(incidentRepository).save(captor.capture());
            ReconciliationIncident incident = captor.getValue();

            String reason = incident.getReason();
            assertThat(reason).isNotNull();
            assertThat(reason).contains("CL-20260820-TQ01");
            assertThat(reason).contains("Nguyen Van A");
            assertThat(reason).contains("Thực tế là BOOKED, nhật ký ghi nhận CHECKED_IN");

            // Zero raw UUID in reason
            assertThat(UUID_PATTERN.matcher(reason).find()).isFalse();
            assertThat(reason).doesNotContain(apptId.toString());
            assertThat(reason).doesNotContain(eventId.toString());
        }

        @Test
        void appointmentDiscrepancyWithPatientProfileFullNameContainsClinicalContext() throws Exception {
            UUID apptId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            BusinessLog entry = BusinessLog.transition(eventId, "APPOINTMENT", apptId,
                    "BOOKED", "CHECKED_IN", "CHECK_IN", "patient", null);

            PatientAccount patient = new PatientAccount("0912345678", "hash", "Parent Account", AccountStatus.ACTIVE, false);
            PatientProfile profile = PatientProfile.create(patient, "Em Be B", "CON", LocalDate.of(2020, 1, 1),
                    "NAM", "0912345678", "123456789012", "Việt Nam", "Kinh", "Hà Nội", false);

            Appointment appt = Appointment.create(patient, profile, "CL-20260820-PD02", "Nhi khoa", "BS. Binh",
                    LocalDate.of(2026, 8, 20), LocalTime.of(9, 0), "Sot cao");
            setField(appt, "id", apptId);

            when(logRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
            when(appointmentRepository.findById(apptId)).thenReturn(Optional.of(appt));

            ArgumentCaptor<ReconciliationIncident> captor = ArgumentCaptor.forClass(ReconciliationIncident.class);

            integrityJob.runOnce();

            verify(incidentRepository).save(captor.capture());
            ReconciliationIncident incident = captor.getValue();

            String reason = incident.getReason();
            assertThat(reason).contains("CL-20260820-PD02");
            assertThat(reason).contains("Em Be B");
            assertThat(UUID_PATTERN.matcher(reason).find()).isFalse();
        }

        @Test
        void queueTicketDiscrepancyReasonContainsQueueNumberAndAppointmentCodeWithoutRawUuids() throws Exception {
            UUID ticketId = UUID.randomUUID();
            UUID apptId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            BusinessLog entry = BusinessLog.transition(eventId, "QUEUE_TICKET", ticketId,
                    "WAITING", "CALLED", "CALL_PATIENT", "nurse", null);

            PatientAccount patient = new PatientAccount("0912345678", "hash", "Nguyen Van A", AccountStatus.ACTIVE, false);
            Appointment appt = Appointment.create(patient, "CL-20260820-TQ01", "Nội tổng quát", "BS. An",
                    LocalDate.of(2026, 8, 20), LocalTime.of(8, 30), "Đau đầu");
            setField(appt, "id", apptId);

            ClinicRoom room = mock(ClinicRoom.class);
            QueueTicket ticket = QueueTicket.create(appt, room, LocalDate.of(2026, 8, 20), 12);
            setField(ticket, "id", ticketId);

            when(logRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
            when(queueTicketRepository.findById(ticketId)).thenReturn(Optional.of(ticket)); // status is WAITING, log is CALLED

            ArgumentCaptor<ReconciliationIncident> captor = ArgumentCaptor.forClass(ReconciliationIncident.class);

            integrityJob.runOnce();

            verify(incidentRepository).save(captor.capture());
            ReconciliationIncident incident = captor.getValue();

            String reason = incident.getReason();
            assertThat(reason).contains("STT 12");
            assertThat(reason).contains("CL-20260820-TQ01");
            assertThat(reason).contains("Thực tế là WAITING, nhật ký ghi nhận CALLED");

            // Zero raw UUID in reason
            assertThat(UUID_PATTERN.matcher(reason).find()).isFalse();
            assertThat(reason).doesNotContain(ticketId.toString());
            assertThat(reason).doesNotContain(apptId.toString());
            assertThat(reason).doesNotContain(eventId.toString());
        }

        @Test
        void examinationDiscrepancyReasonHasNoRawUuids() throws Exception {
            UUID examId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            BusinessLog entry = BusinessLog.transition(eventId, "EXAMINATION", examId,
                    "IN_PROGRESS", "COMPLETED", "COMPLETE_EXAM", "doctor", null);

            ExaminationSession exam = mock(ExaminationSession.class);
            when(exam.getStatus()).thenReturn(ExaminationSessionStatus.IN_PROGRESS);

            when(logRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
            when(examinationSessionRepository.findById(examId)).thenReturn(Optional.of(exam));

            ArgumentCaptor<ReconciliationIncident> captor = ArgumentCaptor.forClass(ReconciliationIncident.class);

            integrityJob.runOnce();

            verify(incidentRepository).save(captor.capture());
            ReconciliationIncident incident = captor.getValue();

            String reason = incident.getReason();
            assertThat(reason).contains("Lệch trạng thái phiên khám: Thực tế là IN_PROGRESS, nhật ký ghi nhận COMPLETED");
            assertThat(UUID_PATTERN.matcher(reason).find()).isFalse();
        }

        @Test
        void hashTamperIncidentReasonHasNoRawUuids() throws Exception {
            UUID logId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();

            BusinessLog entry = BusinessLog.transition(eventId, "APPOINTMENT", entityId,
                    "BOOKED", "CHECKED_IN", "CHECK_IN", "staff", null);
            setField(entry, "id", logId);
            setField(entry, "hash", "tampered_fake_hash_value_1234567890abcdef1234567890abcdef");

            when(logRepository.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(List.of(entry));
            when(appointmentRepository.findById(entityId)).thenReturn(Optional.empty());

            ArgumentCaptor<ReconciliationIncident> captor = ArgumentCaptor.forClass(ReconciliationIncident.class);

            integrityJob.runOnce();

            verify(incidentRepository, atLeastOnce()).save(captor.capture());
            List<ReconciliationIncident> incidents = captor.getAllValues();

            ReconciliationIncident hashIncident = incidents.stream()
                    .filter(i -> "BUSINESS_LOG".equals(i.getEntityType()))
                    .findFirst()
                    .orElseThrow();

            assertThat(hashIncident.getReason()).isEqualTo("Chuỗi hash nhật ký nghiệp vụ không hợp lệ.");
            assertThat(hashIncident.getIncidentCode()).matches(INCIDENT_CODE_PATTERN);
            assertThat(UUID_PATTERN.matcher(hashIncident.getReason()).find()).isFalse();
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
