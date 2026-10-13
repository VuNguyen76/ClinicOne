package com.clinicone.invoice;

import com.clinicone.auth.OtpException;
import com.clinicone.auth.OtpPurpose;
import com.clinicone.auth.OtpService;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.examination.MedicalRecordRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import com.clinicone.validation.VietnamesePhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicPatientDataLookupService {
    private final OtpService otpService;
    private final PatientAccountRepository accountRepository;
    private final PatientProfileRepository profileRepository;
    private final InvoiceRepository invoiceRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    @Transactional(noRollbackFor = OtpException.class)
    public PublicPatientDataLookupResponse lookup(String phone, String code) {
        String normalizedPhone = VietnamesePhoneNumbers.local(phone);
        otpService.verifySmsOtp(normalizedPhone, OtpPurpose.RECEIPT_LOOKUP, code);

        PatientAccount account = accountRepository.findByPhone(normalizedPhone).orElse(null);
        if (account == null) {
            return lookupTemporaryProfiles(normalizedPhone);
        }

        Map<UUID, ProfileData> profiles = new LinkedHashMap<>();
        List<PatientProfile> registeredProfiles = profileRepository
                .findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(account.getId());
        for (PatientProfile profile : registeredProfiles) {
            profiles.put(profile.getId(), new ProfileData(profile.getId(), profile.getFullName(), profile.getRelationship()));
        }

        UUID fallbackProfileId = registeredProfiles.stream()
                .filter(PatientProfile::isPrimaryProfile)
                .map(PatientProfile::getId)
                .findFirst()
                .orElse(account.getId());
        if (profiles.isEmpty()) {
            profiles.put(fallbackProfileId, new ProfileData(fallbackProfileId, account.getFullName(), "Bản thân"));
        }

        for (Invoice invoice : invoiceRepository.findByAppointment_Patient_IdOrderByCreatedAtDesc(account.getId())) {
            var appointment = invoice.getAppointment();
            PatientProfile linkedProfile = appointment == null ? null : appointment.getPatientProfile();
            UUID profileId = linkedProfile == null ? fallbackProfileId : linkedProfile.getId();
            ProfileData profileData = profiles.computeIfAbsent(profileId, id -> new ProfileData(id,
                    linkedProfile == null ? invoice.getPatientName() : linkedProfile.getFullName(),
                    linkedProfile == null ? "Hồ sơ" : linkedProfile.getRelationship()));
            profileData.invoices.add(PublicInvoiceResponse.from(invoice));
        }

        if (registeredProfiles.isEmpty()) {
            profiles.get(fallbackProfileId).medicalRecords.addAll(medicalRecordRepository
                    .findTop10BySession_Appointment_Patient_IdAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
                            account.getId())
                    .stream().map(PublicPatientMedicalRecordResponse::from).toList());
        } else {
            for (PatientProfile profile : registeredProfiles) {
                profiles.get(profile.getId()).medicalRecords.addAll(medicalRecordRepository
                        .findTop10BySession_Appointment_PatientProfile_IdAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
                                profile.getId())
                        .stream().map(PublicPatientMedicalRecordResponse::from).toList());
            }
            List<PublicPatientMedicalRecordResponse> legacyRecords = medicalRecordRepository
                    .findTop10BySession_Appointment_Patient_IdAndSession_Appointment_PatientProfileIsNullAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
                            account.getId())
                    .stream().map(PublicPatientMedicalRecordResponse::from).toList();
            if (!legacyRecords.isEmpty()) {
                ProfileData fallbackProfile = profiles.computeIfAbsent(fallbackProfileId,
                        id -> new ProfileData(id, account.getFullName(), "Bản thân"));
                fallbackProfile.medicalRecords.addAll(legacyRecords);
            }
        }

        List<PublicPatientProfileDataResponse> results = profiles.values().stream()
                .filter(profile -> !profile.invoices.isEmpty() || !profile.medicalRecords.isEmpty())
                .map(ProfileData::toResponse)
                .toList();
        otpService.consumeOtp(normalizedPhone, OtpPurpose.RECEIPT_LOOKUP);
        return new PublicPatientDataLookupResponse(results);
    }

    private PublicPatientDataLookupResponse lookupTemporaryProfiles(String normalizedPhone) {
        List<PatientProfile> temporaryProfiles = profileRepository
                .findByPhoneAndTemporaryProfileTrueAndOwnerIsNullAndActiveTrueOrderByCreatedAtAsc(normalizedPhone);
        if (temporaryProfiles.isEmpty()) {
            otpService.consumeOtp(normalizedPhone, OtpPurpose.RECEIPT_LOOKUP);
            return new PublicPatientDataLookupResponse(List.of());
        }

        Map<UUID, ProfileData> profiles = new LinkedHashMap<>();
        for (PatientProfile profile : temporaryProfiles) {
            profiles.put(profile.getId(), new ProfileData(profile.getId(), profile.getFullName(), profile.getRelationship()));
        }

        for (PatientProfile profile : temporaryProfiles) {
            ProfileData profileData = profiles.get(profile.getId());
            profileData.invoices.addAll(invoiceRepository
                    .findByAppointment_PatientProfile_IdOrderByCreatedAtDesc(profile.getId())
                    .stream().map(PublicInvoiceResponse::from).toList());
            profileData.medicalRecords.addAll(medicalRecordRepository
                    .findTop10BySession_Appointment_PatientProfile_IdAndSignedAtIsNotNullOrderBySignedAtDescIdDesc(
                            profile.getId())
                    .stream().map(PublicPatientMedicalRecordResponse::from).toList());
        }

        List<PublicPatientProfileDataResponse> results = profiles.values().stream()
                .filter(profile -> !profile.invoices.isEmpty() || !profile.medicalRecords.isEmpty())
                .map(ProfileData::toResponse)
                .toList();
        otpService.consumeOtp(normalizedPhone, OtpPurpose.RECEIPT_LOOKUP);
        return new PublicPatientDataLookupResponse(results);
    }

    private static final class ProfileData {
        private final UUID id;
        private final String fullName;
        private final String relationship;
        private final List<PublicInvoiceResponse> invoices = new ArrayList<>();
        private final List<PublicPatientMedicalRecordResponse> medicalRecords = new ArrayList<>();

        private ProfileData(UUID id, String fullName, String relationship) {
            this.id = id;
            this.fullName = fullName;
            this.relationship = relationship;
        }

        private PublicPatientProfileDataResponse toResponse() {
            return new PublicPatientProfileDataResponse(id, fullName, relationship, invoices, medicalRecords);
        }
    }
}
