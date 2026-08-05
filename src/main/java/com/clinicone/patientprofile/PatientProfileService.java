package com.clinicone.patientprofile;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PatientProfileService {
    private static final int MAX_ACTIVE_PROFILES = 10;

    private final PatientAccountRepository accountRepository;
    private final PatientProfileRepository profileRepository;

    public PatientProfileService(PatientAccountRepository accountRepository, PatientProfileRepository profileRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public List<PatientProfileResponse> list(String accountId) {
        UUID ownerId = parseAccountId(accountId);
        PatientAccount owner = findOwner(ownerId);
        List<PatientProfile> profiles = profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(ownerId);
        if (profiles.isEmpty()) {
            PatientProfile primary = PatientProfile.create(owner, owner.getFullName(), "Bản thân", owner.getDateOfBirth(),
                    owner.getGender(), owner.getPhone(), owner.getIdentityNumber(), owner.getNationality(),
                    owner.getEthnicity(), owner.getAddress(), true);
            profiles = List.of(profileRepository.save(primary));
        }
        return profiles.stream().map(PatientProfileResponse::from).toList();
    }

    @Transactional
    public PatientProfileResponse create(String accountId, CreatePatientProfileRequest request) {
        UUID ownerId = parseAccountId(accountId);
        PatientAccount owner = findOwner(ownerId);
        if (profileRepository.countByOwnerIdAndActiveTrue(ownerId) >= MAX_ACTIVE_PROFILES) {
            throw new AuthException(HttpStatus.CONFLICT, "PATIENT_PROFILE_LIMIT",
                    "Mỗi tài khoản chỉ được quản lý tối đa 10 hồ sơ.");
        }
        PatientProfile profile = PatientProfile.create(owner, normalize(request.fullName()), normalize(request.relationship()),
                request.dateOfBirth(), normalize(request.gender()), normalize(request.phone()), normalize(request.identityNumber()),
                normalize(request.nationality()), normalize(request.ethnicity()), normalize(request.address()), false);
        return PatientProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public PatientProfileResponse update(String accountId, String profileId, UpdatePatientProfileRequest request) {
        UUID ownerId = parseAccountId(accountId);
        PatientProfile profile = findOwned(profileId, ownerId);
        profile.update(normalize(request.fullName()), normalize(request.relationship()), request.dateOfBirth(),
                normalize(request.gender()), normalize(request.phone()), normalize(request.identityNumber()),
                normalize(request.nationality()), normalize(request.ethnicity()), normalize(request.address()));
        return PatientProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public void delete(String accountId, String profileId) {
        UUID ownerId = parseAccountId(accountId);
        PatientProfile profile = findOwned(profileId, ownerId);
        if (profile.isPrimaryProfile()) {
            throw new AuthException(HttpStatus.CONFLICT, "PRIMARY_PROFILE_CANNOT_DELETE",
                    "Hồ sơ bản thân không thể xóa.");
        }
        profile.archive();
        profileRepository.save(profile);
    }

    public PatientProfile findOwnedEntity(String profileId, UUID ownerId) {
        return findOwned(profileId, ownerId);
    }

    private PatientProfile findOwned(String profileId, UUID ownerId) {
        try {
            return profileRepository.findByIdAndOwnerIdAndActiveTrue(UUID.fromString(profileId), ownerId)
                    .orElseThrow(this::profileNotFound);
        } catch (IllegalArgumentException exception) {
            throw profileNotFound();
        }
    }

    private PatientAccount findOwner(UUID ownerId) {
        return accountRepository.findById(ownerId).orElseThrow(this::authenticationRequired);
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw authenticationRequired();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AuthException authenticationRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Phiên đăng nhập không hợp lệ.");
    }

    private AuthException profileNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "PATIENT_PROFILE_NOT_FOUND", "Không tìm thấy hồ sơ.");
    }
}
