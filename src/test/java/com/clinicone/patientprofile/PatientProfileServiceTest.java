package com.clinicone.patientprofile;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

class PatientProfileServiceTest {
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    @Test
    void listsOnlyActiveProfilesOwnedByAccount() {
        PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
        PatientProfileRepository profileRepository = mock(PatientProfileRepository.class);
        PatientAccount account = mock(PatientAccount.class);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        PatientProfile profile = PatientProfile.forTest(PROFILE_ID, account, "Nguyễn An", "Bản thân",
                LocalDate.of(2000, 1, 1), "Nam", true);
        when(profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(ACCOUNT_ID))
                .thenReturn(List.of(profile));

        List<PatientProfileResponse> result = new PatientProfileService(accountRepository, profileRepository)
                .list(ACCOUNT_ID.toString());

        assertEquals(1, result.size());
        assertEquals("Nguyễn An", result.get(0).fullName());
        assertEquals("Bản thân", result.get(0).relationship());
    }

    @Test
    void rejectsCreatingAnEleventhActiveProfile() {
        PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
        PatientProfileRepository profileRepository = mock(PatientProfileRepository.class);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(mock(PatientAccount.class)));
        when(profileRepository.countByOwnerIdAndActiveTrue(ACCOUNT_ID)).thenReturn(10L);

        AuthException exception = assertThrows(AuthException.class, () ->
                new PatientProfileService(accountRepository, profileRepository).create(ACCOUNT_ID.toString(),
                        new CreatePatientProfileRequest("Nguyễn B", "Con", LocalDate.of(2015, 2, 1), "Nữ",
                                null, null, null, null, null)));

        assertEquals("PATIENT_PROFILE_LIMIT", exception.getCode());
    }

    @Test
    void doesNotAllowDeletingThePrimaryProfile() {
        PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
        PatientProfileRepository profileRepository = mock(PatientProfileRepository.class);
        PatientAccount account = mock(PatientAccount.class);
        PatientProfile profile = PatientProfile.forTest(PROFILE_ID, account, "Nguyễn An", "Bản thân",
                LocalDate.of(2000, 1, 1), "Nam", true);
        when(profileRepository.findByIdAndOwnerIdAndActiveTrue(PROFILE_ID, ACCOUNT_ID)).thenReturn(Optional.of(profile));

        AuthException exception = assertThrows(AuthException.class, () ->
                new PatientProfileService(accountRepository, profileRepository).delete(ACCOUNT_ID.toString(), PROFILE_ID.toString()));

        assertEquals("PRIMARY_PROFILE_CANNOT_DELETE", exception.getCode());
    }

    @Test
    void updatingThePrimaryProfileUpdatesTheAccountSummary() {
        PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
        PatientProfileRepository profileRepository = mock(PatientProfileRepository.class);
        PatientAccount account = new PatientAccount("0912345678", "hash", "Cũ", com.clinicone.auth.AccountStatus.ACTIVE, false);
        PatientProfile profile = PatientProfile.forTest(PROFILE_ID, account, "Cũ", "Bản thân",
                LocalDate.of(2000, 1, 1), "Nam", true);
        when(profileRepository.findByIdAndOwnerIdAndActiveTrue(PROFILE_ID, ACCOUNT_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(PatientProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new PatientProfileService(accountRepository, profileRepository).update(ACCOUNT_ID.toString(), PROFILE_ID.toString(),
                new UpdatePatientProfileRequest("Mới", "Bản thân", LocalDate.of(2001, 2, 2), "Nữ",
                        null, null, "Việt Nam", "Kinh", "Địa chỉ mới"));

        assertEquals("Mới", account.getFullName());
        assertEquals(LocalDate.of(2001, 2, 2), account.getDateOfBirth());
        assertEquals("Nữ", account.getGender());
        assertEquals("Địa chỉ mới", account.getAddress());
        verify(accountRepository).save(account);
    }
}
