package com.clinicone.auth;

import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.HexFormat;

@Service
public class AccountAuthService {

    private static final Duration SESSION_LIFETIME = Duration.ofHours(12);
    private static final LocalDate MIN_DATE_OF_BIRTH = LocalDate.of(1900, 1, 1);
    private static final Set<String> ALLOWED_GENDERS = Set.of("Nam", "Nữ", "Khác");

    private final PatientAccountRepository accountRepository;
    private final LoginSessionRepository sessionRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenGenerator tokenGenerator;
    private final Clock clock;
    private final PatientProfileRepository patientProfileRepository;

    public AccountAuthService(PatientAccountRepository accountRepository, LoginSessionRepository sessionRepository,
                              OtpService otpService, PasswordEncoder passwordEncoder,
                              SessionTokenGenerator tokenGenerator, Clock clock) {
        this(accountRepository, sessionRepository, otpService, passwordEncoder, tokenGenerator, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AccountAuthService(PatientAccountRepository accountRepository, LoginSessionRepository sessionRepository,
                              OtpService otpService, PasswordEncoder passwordEncoder,
                              SessionTokenGenerator tokenGenerator, Clock clock,
                              PatientProfileRepository patientProfileRepository) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.patientProfileRepository = patientProfileRepository;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String phone = request.phone().trim();
        if (!otpService.isPhoneRecentlyVerified(phone, OtpPurpose.REGISTRATION)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "PHONE_NOT_VERIFIED",
                    "Số điện thoại chưa được xác thực OTP.");
        }
        if (accountRepository.existsByPhone(phone)) {
            throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED", "Số điện thoại đã được sử dụng.");
        }
        PatientAccount account = new PatientAccount(phone, passwordEncoder.encode(request.password()),
                request.fullName().trim(), AccountStatus.ACTIVE, false);
        validateProfileDetails(request.dateOfBirth(), request.gender());
        account.updateProfile(request.fullName().trim(), request.dateOfBirth(), normalizeGender(request.gender()),
                normalizeAddress(request.address()));
        account.updateIdentityAndAddress(null, null, null, normalize(request.provinceCode()), normalize(request.provinceName()),
                normalize(request.districtCode()), normalize(request.districtName()), normalize(request.wardCode()),
                normalize(request.wardName()), normalize(request.streetAddress()));
        PatientAccount saved = accountRepository.save(account);
        syncPrimaryProfile(saved);
        return new RegistrationResponse(saved.getId(), saved.getPhone(), saved.getFullName());
    }

    @Transactional
    public LoginResponse loginBySmsOtp(SmsLoginRequest request) {
        otpService.verifySmsOtp(request.phone(), OtpPurpose.LOGIN, request.code());
        PatientAccount account = accountRepository.findByPhone(request.phone().trim())
                .orElseThrow(this::invalidCredentials);
                
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw invalidCredentials();
        }

        // Bổ sung chốt chặn REC-03
        if (account.getStatus() == AccountStatus.PENDING_ACTIVATION) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", 
                "Tài khoản của bạn đang ở trạng thái chờ kích hoạt. Vui lòng đổi mật khẩu tạm để tiếp tục sử dụng.");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalidCredentials();
        }
        
        return createSession(account);
    }

    @Transactional
    public LoginResponse login(PasswordLoginRequest request) {
        PatientAccount account = accountRepository.findByPhone(request.phone().trim())
                .orElseThrow(this::invalidCredentials);
                
        // 1. Kiểm tra mật khẩu trước tiên
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw invalidCredentials();
        }

        // 2. Chốt chặn REC-03
        if (account.getStatus() == AccountStatus.PENDING_ACTIVATION) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", 
                "Tài khoản của bạn đang ở trạng thái chờ kích hoạt. Vui lòng đổi mật khẩu tạm để tiếp tục sử dụng.");
        }

        // 3. Chặn các trạng thái khác
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalidCredentials();
        }

        return createSession(account);
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getProfile(String accountId) {
        PatientAccount account = findAccount(accountId);
        return toProfile(account);
    }

    @Transactional
    public PatientProfileResponse updateProfile(String accountId, UpdateProfileRequest request) {
        PatientAccount account = findAccount(accountId);
        validateProfileDetails(request.dateOfBirth(), request.gender());
        account.updateProfile(request.fullName().trim(), request.dateOfBirth(), normalizeGender(request.gender()),
                normalizeAddress(request.address()));
        account.updateIdentityAndAddress(normalize(request.identityNumber()), normalize(request.nationality()),
                normalize(request.ethnicity()), normalize(request.provinceCode()), normalize(request.provinceName()),
                normalize(request.districtCode()), normalize(request.districtName()), normalize(request.wardCode()),
                normalize(request.wardName()), normalize(request.streetAddress()));
        accountRepository.save(account);
        syncPrimaryProfile(account);
        return toProfile(account);
    }

    private void syncPrimaryProfile(PatientAccount account) {
        if (patientProfileRepository == null || account.getId() == null || account.getDateOfBirth() == null
                || account.getGender() == null || account.getFullName() == null) {
            return;
        }
        PatientProfile primary = patientProfileRepository
                .findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(account.getId())
                .stream()
                .filter(PatientProfile::isPrimaryProfile)
                .findFirst()
                .orElseGet(() -> PatientProfile.create(account, account.getFullName(), "Bản thân",
                        account.getDateOfBirth(), account.getGender(), account.getPhone(), account.getIdentityNumber(),
                        account.getNationality(), account.getEthnicity(), account.getAddress(), account.getProvinceCode(),
                        account.getProvinceName(), account.getDistrictCode(), account.getDistrictName(), account.getWardCode(),
                        account.getWardName(), account.getStreetAddress(), true));
        if (primary.getId() != null) {
            primary.update(account.getFullName(), "Bản thân", account.getDateOfBirth(), account.getGender(),
                    account.getPhone(), account.getIdentityNumber(), account.getNationality(), account.getEthnicity(),
                    account.getAddress(), account.getProvinceCode(), account.getProvinceName(), account.getDistrictCode(),
                    account.getDistrictName(), account.getWardCode(), account.getWardName(), account.getStreetAddress());
        }
        patientProfileRepository.save(primary);
    }

    @Transactional
    public void changePassword(String accountId, ChangePasswordRequest request) {
        PatientAccount account = accountRepository.findById(java.util.UUID.fromString(accountId))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                        "Phiên đăng nhập không hợp lệ."));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Mật khẩu hiện tại không đúng.");
        }
        account.changePassword(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
                "Số điện thoại hoặc mật khẩu không đúng.");
    }

    private PatientAccount findAccount(String accountId) {
        try {
            return accountRepository.findById(java.util.UUID.fromString(accountId))
                    .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                            "Phiên đăng nhập không hợp lệ."));
        } catch (IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Phiên đăng nhập không hợp lệ.");
        }
    }

    private PatientProfileResponse toProfile(PatientAccount account) {
        return new PatientProfileResponse(account.getId(), account.getPhone(), account.getFullName(),
                account.getDateOfBirth(), account.getGender(), account.getAddress(),
                account.getIdentityNumber(), account.getNationality(), account.getEthnicity(), account.getProvinceCode(),
                account.getProvinceName(), account.getDistrictCode(), account.getDistrictName(), account.getWardCode(),
                account.getWardName(), account.getStreetAddress(), account.getStatus(), account.isMustChangePassword());
    }

    private void validateProfileDetails(LocalDate dateOfBirth, String gender) {
        if (dateOfBirth != null && dateOfBirth.isBefore(MIN_DATE_OF_BIRTH)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DATE_OF_BIRTH_INVALID",
                    "Ngày sinh phải từ 01/01/1900 đến hôm nay.");
        }
        if (gender != null && !ALLOWED_GENDERS.contains(gender.trim())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "GENDER_INVALID",
                    "Vui lòng chọn một giới tính trong danh sách.");
        }
    }

    private String normalizeGender(String gender) {
        return gender == null || gender.isBlank() ? null : gender.trim();
    }

    private String normalizeAddress(String address) {
        return address == null || address.isBlank() ? null : address.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LoginResponse createSession(PatientAccount account) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(SESSION_LIFETIME);
        String accessToken = tokenGenerator.generate();
        sessionRepository.save(new LoginSession(account.getId(), hashToken(accessToken), now, expiresAt));
        return new LoginResponse(accessToken, "Bearer", expiresAt, account.getId(), account.getFullName(),
                account.isMustChangePassword());
    }

    @Transactional
    public RegistrationResponse createTemporaryAccountByReception(String phone, String fullName, LocalDate dateOfBirth, String gender) {
        // Sử dụng existsByPhone cho gọn và đúng biến accountRepository
        if (accountRepository.existsByPhone(phone)) {
            throw new AuthException(HttpStatus.CONFLICT, "PHONE_ALREADY_USED", "Số điện thoại đã được sử dụng.");
        }

        String encodedTempPassword = passwordEncoder.encode("123456");

        PatientAccount newAccount = new PatientAccount(
                phone,
                encodedTempPassword,
                fullName,
                AccountStatus.PENDING_ACTIVATION,
                false
        );
        
        accountRepository.save(newAccount);

        // Sử dụng đúng biến patientProfileRepository
        PatientProfile defaultProfile = PatientProfile.create(
                newAccount,
                fullName,
                "Bản thân",
                dateOfBirth,
                gender,
                phone,
                null, null, null, null, true
        );
        patientProfileRepository.save(defaultProfile);

        return new RegistrationResponse(newAccount.getId(), newAccount.getPhone(), newAccount.getFullName());
    }
}
