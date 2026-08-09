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
import static org.mockito.ArgumentMatchers.any;

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

    @Test
    void preventsOverwritingExistingFieldsForReceptionist() {
        // AC-REC-02-01: Màn hình khóa trường đã có (Backend không ghi đè dữ liệu cũ)
        PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
        PatientProfileRepository profileRepository = mock(PatientProfileRepository.class);

        // Giả lập DB có sẵn Tên, nhưng thiếu Ngày sinh và Giới tính
        PatientAccount account = mock(PatientAccount.class);
        PatientProfile profile = PatientProfile.forTest(PROFILE_ID, account, "Tên Cũ", "Bản thân", null, null, true);
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(PatientProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientProfileService service = new PatientProfileService(accountRepository, profileRepository);

        // Request gửi lên cố tình sửa tên thành "Tên Bị Sửa"
        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest(
                "Tên Bị Sửa", "Bản thân", LocalDate.of(1990, 1, 1), "Nam",
                null, null, null, null, null
        );

        service.updateMissingDataByReceptionist(PROFILE_ID.toString(), request);

        // Assert: Tên phải bị khóa giữ nguyên "Tên Cũ", Ngày sinh và Giới tính được bổ sung
        assertEquals("Tên Cũ", profile.getFullName());
        assertEquals(LocalDate.of(1990, 1, 1), profile.getDateOfBirth());
        assertEquals("Nam", profile.getGender());
    }

    @Test
    void missingAddressDoesNotBlockUpdateForReceptionist() {
        // AC-REC-02-02: Thiếu địa chỉ không chặn tiếp nhận
        PatientAccountRepository accountRepository = mock(PatientAccountRepository.class);
        PatientProfileRepository profileRepository = mock(PatientProfileRepository.class);

        PatientAccount account = mock(PatientAccount.class);
        PatientProfile profile = PatientProfile.forTest(PROFILE_ID, account, null, "Bản thân", null, null, true);
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(PatientProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientProfileService service = new PatientProfileService(accountRepository, profileRepository);

        // Request gửi lên KHÔNG có địa chỉ (truyền null)
        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest(
                "Nguyễn Văn A", "Bản thân", LocalDate.of(1990, 1, 1), "Nam",
                null, null, null, null, null
        );

        PatientProfileResponse response = service.updateMissingDataByReceptionist(PROFILE_ID.toString(), request);

        // Assert: Hàm chạy thành công, không văng lỗi và lưu được thông tin
        assertEquals("Nguyễn Văn A", response.fullName());
        verify(profileRepository).save(profile);
    }
}
