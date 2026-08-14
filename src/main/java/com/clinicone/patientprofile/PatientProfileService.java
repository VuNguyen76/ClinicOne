package com.clinicone.patientprofile;

import com.clinicone.auth.AuthenticatedIds;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PatientProfileService {
    private static final int MAX_ACTIVE_PROFILES = 10;

    private final PatientAccountRepository accountRepository;
    private final PatientProfileRepository profileRepository;

    @Transactional
    public List<PatientProfileResponse> list(String accountId) {
        UUID ownerId = AuthenticatedIds.patient(accountId);
        PatientAccount owner = findOwner(ownerId);
        List<PatientProfile> profiles = profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(ownerId);
        if (profiles.isEmpty()) {
            PatientProfile primary = PatientProfile.create(owner, owner.getFullName(), "Bản thân", owner.getDateOfBirth(),
                    owner.getGender(), owner.getPhone(), owner.getIdentityNumber(), owner.getNationality(),
                    owner.getEthnicity(), owner.getAddress(), owner.getProvinceCode(), owner.getProvinceName(),
                    owner.getDistrictCode(), owner.getDistrictName(), owner.getWardCode(), owner.getWardName(),
                    owner.getStreetAddress(), true);
            profiles = List.of(profileRepository.save(primary));
        }
        return profiles.stream().map(PatientProfileResponse::from).toList();
    }

    @Transactional
    public PatientProfileResponse create(String accountId, CreatePatientProfileRequest request) {
        UUID ownerId = AuthenticatedIds.patient(accountId);
        PatientAccount owner = findOwner(ownerId);
        if (profileRepository.countByOwnerIdAndActiveTrue(ownerId) >= MAX_ACTIVE_PROFILES) {
            throw new AuthException(HttpStatus.CONFLICT, "PATIENT_PROFILE_LIMIT",
                    "Mỗi tài khoản chỉ được quản lý tối đa 10 hồ sơ.");
        }
        PatientProfile profile = PatientProfile.create(owner, normalize(request.fullName()), normalize(request.relationship()),
                request.dateOfBirth(), normalize(request.gender()), normalize(request.phone()), normalize(request.identityNumber()),
                normalize(request.nationality()), normalize(request.ethnicity()), composeAddress(request.address(), request.streetAddress(), request.wardName(), request.districtName(), request.provinceName()),
                normalize(request.provinceCode()), normalize(request.provinceName()), normalize(request.districtCode()),
                normalize(request.districtName()), normalize(request.wardCode()), normalize(request.wardName()),
                normalize(request.streetAddress()), false);
        return PatientProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public PatientProfileResponse update(String accountId, String profileId, UpdatePatientProfileRequest request) {
        UUID ownerId = AuthenticatedIds.patient(accountId);
        PatientProfile profile = findOwned(profileId, ownerId);
        profile.update(normalize(request.fullName()), normalize(request.relationship()), request.dateOfBirth(),
                normalize(request.gender()), normalize(request.phone()), normalize(request.identityNumber()),
                normalize(request.nationality()), normalize(request.ethnicity()), composeAddress(request.address(), request.streetAddress(), request.wardName(), request.districtName(), request.provinceName()),
                normalize(request.provinceCode()), normalize(request.provinceName()), normalize(request.districtCode()),
                normalize(request.districtName()), normalize(request.wardCode()), normalize(request.wardName()),
                normalize(request.streetAddress()));
        if (profile.isPrimaryProfile()) {
            PatientAccount owner = profile.getOwner();
            owner.syncFromPrimaryProfile(profile.getFullName(), profile.getDateOfBirth(), profile.getGender(),
                    profile.getPhone(), profile.getIdentityNumber(), profile.getNationality(), profile.getEthnicity(),
                    profile.getAddress(), profile.getProvinceCode(), profile.getProvinceName(), profile.getDistrictCode(),
                    profile.getDistrictName(), profile.getWardCode(), profile.getWardName(), profile.getStreetAddress());
            accountRepository.save(owner);
        }
        return PatientProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public PatientProfileResponse updateMissingDataByReceptionist(String profileId, UpdatePatientProfileRequest request) {
        return updateMissingDataByReceptionist(profileId, new ReceptionUpdatePatientProfileRequest(
                request.fullName(), request.dateOfBirth(), request.gender(), request.phone(),
                request.identityNumber(), request.nationality(), request.ethnicity(), request.address(),
                request.provinceCode(), request.provinceName(), request.districtCode(), request.districtName(),
                request.wardCode(), request.wardName(), request.streetAddress()));
    }

    @Transactional
    public PatientProfileResponse updateMissingDataByReceptionist(
            String profileId, ReceptionUpdatePatientProfileRequest request) {
        if (request.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "RECEPTION_UPDATE_EMPTY",
                    "Cần nhập ít nhất một trường còn thiếu của hồ sơ.");
        }

        PatientProfile profile = findByIdForReception(profileId);
        String fullName = fillMissing(profile.getFullName(), request.fullName());
        LocalDate dateOfBirth = profile.getDateOfBirth() == null && request.dateOfBirth() != null
                ? request.dateOfBirth() : profile.getDateOfBirth();
        String gender = fillMissing(profile.getGender(), request.gender());
        String phone = fillMissing(profile.getPhone(), request.phone());
        String identityNumber = fillMissing(profile.getIdentityNumber(), request.identityNumber());
        String nationality = fillMissing(profile.getNationality(), request.nationality());
        String ethnicity = fillMissing(profile.getEthnicity(), request.ethnicity());
        String provinceCode = fillMissing(profile.getProvinceCode(), request.provinceCode());
        String provinceName = fillMissing(profile.getProvinceName(), request.provinceName());
        String districtCode = fillMissing(profile.getDistrictCode(), request.districtCode());
        String districtName = fillMissing(profile.getDistrictName(), request.districtName());
        String wardCode = fillMissing(profile.getWardCode(), request.wardCode());
        String wardName = fillMissing(profile.getWardName(), request.wardName());
        String streetAddress = fillMissing(profile.getStreetAddress(), request.streetAddress());
        String address = fillMissing(profile.getAddress(), composeAddress(request.address(), streetAddress,
                wardName, districtName, provinceName));

        profile.update(fullName, profile.getRelationship(), dateOfBirth, gender, phone, identityNumber,
                nationality, ethnicity, address, provinceCode, provinceName, districtCode, districtName,
                wardCode, wardName, streetAddress);
        if (profile.isPrimaryProfile()) {
            PatientAccount owner = profile.getOwner();
            owner.syncFromPrimaryProfile(profile.getFullName(), profile.getDateOfBirth(), profile.getGender(),
                    profile.getPhone(), profile.getIdentityNumber(), profile.getNationality(), profile.getEthnicity(),
                    profile.getAddress(), profile.getProvinceCode(), profile.getProvinceName(), profile.getDistrictCode(),
                    profile.getDistrictName(), profile.getWardCode(), profile.getWardName(), profile.getStreetAddress());
            accountRepository.save(owner);
        }
        return PatientProfileResponse.from(profileRepository.save(profile));
    }

    private PatientProfile findByIdForReception(String profileId) {
        try {
            return profileRepository.findById(UUID.fromString(profileId)).orElseThrow(this::profileNotFound);
        } catch (IllegalArgumentException exception) {
            throw profileNotFound();
        }
    }

    private String fillMissing(String existing, String candidate) {
        return existing == null || existing.isBlank() ? normalize(candidate) : existing;
    }

    @Transactional
    public void delete(String accountId, String profileId) {
        UUID ownerId = AuthenticatedIds.patient(accountId);
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String composeAddress(String fallback, String street, String ward, String district, String province) {
        String composed = Stream.of(street, ward, district, province)
                .map(this::normalize)
                .filter(value -> value != null)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        return composed == null ? normalize(fallback) : composed;
    }

    private AuthException authenticationRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Phiên đăng nhập không hợp lệ.");
    }

    private AuthException profileNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "PATIENT_PROFILE_NOT_FOUND", "Không tìm thấy hồ sơ.");
    }
}
