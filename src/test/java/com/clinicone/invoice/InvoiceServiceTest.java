package com.clinicone.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.clinicone.appointment.Appointment;
import com.clinicone.auth.AuthException;
import com.clinicone.audit.BusinessLogService;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.ExaminationSessionRepository;
import com.clinicone.examination.ExaminationSessionStatus;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.schedule.ClinicService;
import com.clinicone.schedule.ClinicServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ExaminationSessionRepository sessionRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private ClinicServiceRepository clinicServiceRepository;
    @Mock private BusinessLogService businessLogService;
    @InjectMocks private InvoiceService service;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private Appointment appointment;

    @Test
    void createsPendingPaymentReceiptFromConfiguredDatabaseTariff() {
        ExaminationSession session = completedSession();
        PatientProfile profile = mock(PatientProfile.class);
        when(profile.getFullName()).thenReturn("Patient A");
        when(profile.getPhone()).thenReturn("0912345678");
        when(appointment.getPatientProfile()).thenReturn(profile);
        when(appointment.getServiceName()).thenReturn("Khám nội tổng quát");
        when(appointment.getAppointmentCode()).thenReturn("APT-001");
        when(appointment.getDoctorName()).thenReturn("Doctor A");
        when(appointment.getSpecialty()).thenReturn("Nội tổng quát");
        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getSignedAt()).thenReturn(Instant.parse("2026-10-10T02:00:00Z"));
        ClinicService clinicService = mock(ClinicService.class);
        when(clinicService.getConsultationFee()).thenReturn(250_000L);
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(invoiceRepository.findBySession_Id(sessionId)).thenReturn(Optional.empty());
        when(medicalRecordRepository.findBySession_Id(sessionId)).thenReturn(Optional.of(record));
        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.of(clinicService));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceResponse response = service.createPaymentReceipt(sessionId, "cashier-1");

        assertThat(response.status()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(response.totalAmount()).isEqualTo(250_000L);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.itemName()).isEqualTo("Khám nội tổng quát");
            assertThat(item.totalPrice()).isEqualTo(250_000L);
        });
        assertThat(response.invoiceCode()).startsWith("INV-");
    }

    @Test
    void requiresDoctorToCompleteEncounterBeforeReceiptCreation() {
        ExaminationSession session = mock(ExaminationSession.class);
        when(session.getStatus()).thenReturn(ExaminationSessionStatus.IN_PROGRESS);
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(invoiceRepository.findBySession_Id(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPaymentReceipt(sessionId, "cashier-1"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void requiresConfiguredTariffInsteadOfInventingAnAmount() {
        ExaminationSession session = completedSession();
        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getSignedAt()).thenReturn(Instant.parse("2026-10-10T02:00:00Z"));
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(invoiceRepository.findBySession_Id(sessionId)).thenReturn(Optional.empty());
        when(medicalRecordRepository.findBySession_Id(sessionId)).thenReturn(Optional.of(record));
        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPaymentReceipt(sessionId, "cashier-1"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getCode())
                        .isEqualTo("SERVICE_TARIFF_REQUIRED"));
    }

    @Test
    void publicLookupRejectsNonRandomLookingCodesBeforeDatabaseLookup() {
        assertThatThrownBy(() -> service.lookupPublicInvoice("APT-001"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getCode()).isEqualTo("INVOICE_NOT_FOUND"));
        verifyNoInteractions(invoiceRepository);
    }

    private ExaminationSession completedSession() {
        appointment = mock(Appointment.class);
        when(appointment.getServiceId()).thenReturn(serviceId);
        ExaminationSession session = mock(ExaminationSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAppointment()).thenReturn(appointment);
        when(session.getStatus()).thenReturn(ExaminationSessionStatus.COMPLETED);
        return session;
    }
}
