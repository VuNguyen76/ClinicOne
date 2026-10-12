package com.clinicone.invoice;

import com.clinicone.auth.OtpPurpose;
import com.clinicone.auth.OtpException;
import com.clinicone.auth.OtpService;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.examination.ExaminationSession;
import com.clinicone.examination.MedicalRecord;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.examination.PrescriptionLine;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPatientDataLookupServiceTest {
    @Mock private OtpService otpService;
    @Mock private PatientAccountRepository accountRepository;
    @Mock private PatientProfileRepository profileRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @InjectMocks private PublicPatientDataLookupService service;

    @Test
    void verifiesOtpBeforeReturningReceiptsAndPrescriptionsGroupedByProfile() {
        String phone = "0862764830";
        UUID accountId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        PatientAccount account = org.mockito.Mockito.mock(PatientAccount.class);
        PatientProfile profile = org.mockito.Mockito.mock(PatientProfile.class);
        Invoice invoice = org.mockito.Mockito.mock(Invoice.class);
        var appointment = org.mockito.Mockito.mock(com.clinicone.appointment.Appointment.class);
        MedicalRecord record = org.mockito.Mockito.mock(MedicalRecord.class);
        ExaminationSession session = org.mockito.Mockito.mock(ExaminationSession.class);
        PrescriptionLine prescriptionLine = org.mockito.Mockito.mock(PrescriptionLine.class);
        when(accountRepository.findByPhone(phone)).thenReturn(Optional.of(account));
        when(account.getId()).thenReturn(accountId);
        when(profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(accountId))
                .thenReturn(List.of(profile));
        when(profile.getId()).thenReturn(profileId);
        when(profile.getFullName()).thenReturn("Nguyễn An");
        when(profile.getRelationship()).thenReturn("Con");
        when(invoiceRepository.findByAppointment_Patient_IdOrderByCreatedAtDesc(accountId))
                .thenReturn(List.of(invoice));
        when(invoice.getAppointment()).thenReturn(appointment);
        when(appointment.getPatientProfile()).thenReturn(profile);
        when(invoice.getInvoiceCode()).thenReturn("INV-0123456789ABCDEF0123456789ABCDEF");
        when(invoice.getPatientName()).thenReturn("Nguyễn An");
        when(invoice.getDoctorName()).thenReturn("Bác sĩ A");
        when(invoice.getSpecialty()).thenReturn("Nhi khoa");
        when(invoice.getTotalAmount()).thenReturn(250_000L);
        when(invoice.getPaidAmount()).thenReturn(0L);
        when(invoice.getStatus()).thenReturn(InvoiceStatus.PENDING);
        when(invoice.getCreatedAt()).thenReturn(Instant.parse("2026-10-12T02:00:00Z"));
        when(invoice.getItems()).thenReturn(List.of());
        when(record.getId()).thenReturn(UUID.randomUUID());
        when(record.getSession()).thenReturn(session);
        when(session.getAppointment()).thenReturn(appointment);
        when(record.getSignedAt()).thenReturn(Instant.parse("2026-10-12T02:15:00Z"));
        when(record.getDoctorName()).thenReturn("Bác sĩ A");
        when(record.getDiagnosis()).thenReturn("Cảm cúm");
        when(record.getPrescriptionLines()).thenReturn(List.of(prescriptionLine));
        when(prescriptionLine.getMedicationName()).thenReturn("Thuốc A");
        when(prescriptionLine.getDosage()).thenReturn("1 viên");
        when(prescriptionLine.getQuantity()).thenReturn(5);
        when(prescriptionLine.getUnit()).thenReturn("viên");
        when(prescriptionLine.getInstructions()).thenReturn("Ngày 2 lần");
        when(appointment.getSpecialty()).thenReturn("Nhi khoa");
        when(medicalRecordRepository
                .findTop10BySession_Appointment_PatientProfile_IdAndSignedAtIsNotNullOrderBySignedAtDesc(profileId))
                .thenReturn(List.of(record));

        PublicPatientDataLookupResponse response = service.lookup(phone, "123456");

        assertThat(response.profiles()).singleElement().satisfies(result -> {
            assertThat(result.fullName()).isEqualTo("Nguyễn An");
            assertThat(result.invoices()).hasSize(1);
            assertThat(result.invoices().get(0).invoiceCode()).startsWith("INV-");
            assertThat(result.medicalRecords()).singleElement().satisfies(medicalRecord -> {
                assertThat(medicalRecord.diagnosis()).isEqualTo("Cảm cúm");
                assertThat(medicalRecord.prescriptionLines()).singleElement().satisfies(line -> {
                    assertThat(line.medicationName()).isEqualTo("Thuốc A");
                    assertThat(line.instructions()).isEqualTo("Ngày 2 lần");
                });
            });
        });
        verify(otpService).verifySmsOtp(phone, OtpPurpose.RECEIPT_LOOKUP, "123456");
        verify(otpService).consumeOtp(phone, OtpPurpose.RECEIPT_LOOKUP);
    }

    @Test
    void returnsAnEmptyListForAnUnknownPhoneWithoutDisclosingWhetherItHasAnAccount() {
        String phone = "0862764830";
        when(accountRepository.findByPhone(phone)).thenReturn(Optional.empty());

        PublicPatientDataLookupResponse response = service.lookup(phone, "123456");

        assertThat(response.profiles()).isEmpty();
        verify(otpService).verifySmsOtp(phone, OtpPurpose.RECEIPT_LOOKUP, "123456");
        verify(otpService).consumeOtp(phone, OtpPurpose.RECEIPT_LOOKUP);
    }

    @Test
    void invalidOtpDoesNotQueryAnyPatientOrBillingData() {
        String phone = "0862764830";
        doThrow(new OtpException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Invalid OTP"))
                .when(otpService).verifySmsOtp(phone, OtpPurpose.RECEIPT_LOOKUP, "123456");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.lookup(phone, "123456"))
                .isInstanceOf(OtpException.class);

        verifyNoInteractions(accountRepository, profileRepository, invoiceRepository, medicalRecordRepository);
    }
}
