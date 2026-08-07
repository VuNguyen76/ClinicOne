package com.clinicone.reception;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.AuthException;
import com.clinicone.auth.OtpPurpose;
import com.clinicone.auth.OtpService;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.auth.RequestOtpResponse;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ReceptionPatientService {
    private static final String TEMPORARY_PASSWORD = "123456";
    private static final Set<String> ALLOWED_GENDERS = Set.of("Nam", "Nữ", "Khác");

    private final PatientAccountRepository accountRepository;
    private final PatientProfileRepository profileRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public ReceptionPatientService(PatientAccountRepository accountRepository,
                                   PatientProfileRepository profileRepository,
                                   OtpService otpService,
                                   PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RequestOtpResponse requestOtp(ReceptionPatientOtpRequest request) {
        String phone = normalizePhone(request.phone());
        if (accountRepository.existsByPhone(phone)) {
            throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED", "Số điện thoại đã có tài khoản.");
        }
        return otpService.requestSmsOtp(phone, OtpPurpose.REGISTRATION);
    }

    @Transactional
    public ReceptionPatientRegistrationResponse register(ReceptionPatientRegistrationRequest request) {
        String phone = normalizePhone(request.phone());
        if (accountRepository.existsByPhone(phone)) {
            throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED", "Số điện thoại đã có tài khoản.");
        }
        validateGender(request.gender());
        otpService.verifySmsOtp(phone, OtpPurpose.REGISTRATION, request.otpCode());

        PatientAccount account = new PatientAccount(phone, passwordEncoder.encode(TEMPORARY_PASSWORD),
                request.fullName().trim(), AccountStatus.ACTIVE, true);
        account.updateProfile(request.fullName().trim(), request.dateOfBirth(), request.gender().trim(),
                normalize(request.address()));
        account.updateIdentityAndAddress(normalize(request.identityNumber()), normalize(request.nationality()),
                normalize(request.ethnicity()), normalize(request.provinceCode()), normalize(request.provinceName()),
                normalize(request.districtCode()), normalize(request.districtName()), normalize(request.wardCode()),
                normalize(request.wardName()), normalize(request.streetAddress()));
        PatientAccount saved = accountRepository.save(account);

        PatientProfile profile = PatientProfile.create(saved, saved.getFullName(), "Bản thân",
                saved.getDateOfBirth(), saved.getGender(), saved.getPhone(), saved.getIdentityNumber(),
                saved.getNationality(), saved.getEthnicity(), saved.getAddress(), saved.getProvinceCode(),
                saved.getProvinceName(), saved.getDistrictCode(), saved.getDistrictName(), saved.getWardCode(),
                saved.getWardName(), saved.getStreetAddress(), true);
        profileRepository.save(profile);
        return new ReceptionPatientRegistrationResponse(saved.getId(), saved.getPhone(), saved.getFullName(),
                saved.isMustChangePassword());
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!normalized.matches("0\\d{9}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PHONE_INVALID",
                    "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        }
        return normalized;
    }

    private void validateGender(String gender) {
        if (gender == null || !ALLOWED_GENDERS.contains(gender.trim())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "GENDER_INVALID", "Vui lòng chọn giới tính hợp lệ.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
