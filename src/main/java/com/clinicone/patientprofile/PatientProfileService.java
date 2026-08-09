package com.clinicone.patientprofile;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
                    owner.getEthnicity(), owner.getAddress(), owner.getProvinceCode(), owner.getProvinceName(),
                    owner.getDistrictCode(), owner.getDistrictName(), owner.getWardCode(), owner.getWardName(),
                    owner.getStreetAddress(), true);
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
                normalize(request.nationality()), normalize(request.ethnicity()), composeAddress(request.address(), request.streetAddress(), request.wardName(), request.districtName(), request.provinceName()),
                normalize(request.provinceCode()), normalize(request.provinceName()), normalize(request.districtCode()),
                normalize(request.districtName()), normalize(request.wardCode()), normalize(request.wardName()),
                normalize(request.streetAddress()), false);
        return PatientProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public PatientProfileResponse update(String accountId, String profileId, UpdatePatientProfileRequest request) {
        UUID ownerId = parseAccountId(accountId);
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
        // 1. Lễ tân không bị ràng buộc bởi ownerId, tìm hồ sơ trực tiếp bằng ID
        PatientProfile profile = profileRepository.findById(UUID.fromString(profileId))
                .orElseThrow(this::profileNotFound);

        // 2. AC-REC-02-01: Khóa trường đã có 
        // Chỉ lấy giá trị từ request nếu dữ liệu cũ đang thực sự trống (legacy data)
        String newFullName = (profile.getFullName() == null || profile.getFullName().isBlank()) ? normalize(request.fullName()) : profile.getFullName();
        LocalDate newDob = (profile.getDateOfBirth() == null) ? request.dateOfBirth() : profile.getDateOfBirth();
        String newGender = (profile.getGender() == null || profile.getGender().isBlank()) ? normalize(request.gender()) : profile.getGender();

        // 3. AC-REC-02-02: Địa chỉ có thể nhập thêm (nếu có thì lấy, không thì giữ cũ)
        String newAddress = request.address() != null ? normalize(request.address()) : profile.getAddress();

        // 4. Cập nhật vào Entity. Các trường khác (Relationship, Identity,...) giữ nguyên như cũ.
        profile.update(
                newFullName,
                profile.getRelationship(), 
                newDob,
                newGender,
                profile.getPhone(),
                profile.getIdentityNumber(),
                profile.getNationality(),
                profile.getEthnicity(),
                newAddress,
                profile.getProvinceCode(),
                profile.getProvinceName(),
                profile.getDistrictCode(),
                profile.getDistrictName(),
                profile.getWardCode(),
                profile.getWardName(),
                profile.getStreetAddress()
        );

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

    private String composeAddress(String fallback, String street, String ward, String district, String province) {
        String composed = java.util.stream.Stream.of(street, ward, district, province)
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
