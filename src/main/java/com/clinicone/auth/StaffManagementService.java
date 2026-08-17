package com.clinicone.auth;

import lombok.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.Objects;

@Service
public class StaffManagementService {
    private final StaffAccountRepository accountRepository;
    private final LoginSessionRepository sessionRepository;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    @Builder
    public StaffManagementService(StaffAccountRepository accountRepository,
                                  LoginSessionRepository sessionRepository,
                                  Clock clock,
                                  PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StaffAccountResponse> list() {
        return accountRepository.findAllByOrderByFullNameAsc().stream()
                .map(StaffAccountResponse::from)
                .toList();
    }

    @Transactional
    public StaffAccountResponse lock(UUID staffId, String actor) {
        StaffAccount account = find(staffId);
        account.lock();
        StaffAccount saved = accountRepository.save(account);
        sessionRepository.revokeActiveByAccountId(staffId, Instant.now(clock));
        return StaffAccountResponse.from(saved);
    }

    @Transactional
    public StaffAccountResponse unlock(UUID staffId, String actor) {
        StaffAccount account = find(staffId);
        account.unlock();
        return StaffAccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public StaffAccountCreatedResponse create(CreateStaffAccountRequest request) {
        if (passwordEncoder == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "STAFF_ACCOUNT_CREATE_UNAVAILABLE",
                    "Chưa bật chức năng tạo tài khoản nhân viên.");
        }
        if (request == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STAFF_ACCOUNT_REQUIRED",
                    "Cần cung cấp thông tin tài khoản nhân viên.");
        }
        String fullName = normalize(request.fullName(), "Họ tên nhân viên không được để trống.");
        String employeeCode = normalize(request.employeeCode(), "Mã nhân viên không được để trống.").toUpperCase(Locale.ROOT);
        String unitName = normalize(request.unitName(), "Đơn vị không được để trống.");
        String departmentName = normalize(request.departmentName(), "Phòng ban không được để trống.");
        Set<StaffRole> roles = validateRoles(request.roles());
        if (accountRepository.existsByEmployeeCodeIgnoreCase(employeeCode)) {
            throw new AuthException(HttpStatus.CONFLICT, "STAFF_EMPLOYEE_CODE_TAKEN",
                    "Mã nhân viên đã được sử dụng.");
        }
        if (accountRepository.findByUsernameIgnoreCase(employeeCode).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "STAFF_USERNAME_TAKEN",
                    "Mã nhân viên đang trùng tên đăng nhập.");
        }
        String initialPassword = generateInitialPassword();
        StaffAccount account = StaffAccount.create(employeeCode, passwordEncoder.encode(initialPassword), fullName,
                employeeCode, unitName, departmentName, request.roles());
        return new StaffAccountCreatedResponse(StaffAccountResponse.from(accountRepository.save(account)), initialPassword);
    }

    @Transactional
    public StaffAccountResponse updateRoles(UUID staffId, UpdateStaffRolesRequest request) {
        Set<StaffRole> roles = validateRoles(request == null ? null : request.roles());
        StaffAccount account = find(staffId);
        account.replaceRoles(roles);
        StaffAccountResponse response = StaffAccountResponse.from(accountRepository.save(account));
        sessionRepository.revokeActiveByAccountId(staffId, Instant.now(clock));
        return response;
    }

    private Set<StaffRole> validateRoles(List<StaffRole> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > 3
                || requested.stream().anyMatch(Objects::isNull)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STAFF_ROLE_COUNT_INVALID",
                    "Tài khoản phải có từ 1 đến 3 vai trò.");
        }
        Set<StaffRole> roles = EnumSet.copyOf(requested);
        if (roles.size() != requested.size()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STAFF_ROLE_DUPLICATE",
                    "Vai trò không được trùng.");
        }
        if (roles.contains(StaffRole.ADMIN) && roles.size() != 1) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STAFF_ROLE_COMBINATION_INVALID",
                    "Vai trò quản trị phải dùng tài khoản riêng, không kết hợp vai trò nghiệp vụ.");
        }
        return roles;
    }

    private String generateInitialPassword() {
        StringBuilder password = new StringBuilder(12);
        for (int index = 0; index < 12; index++) {
            password.append(PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return password.toString();
    }

    private String normalize(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STAFF_FIELD_REQUIRED", message);
        }
        return normalized;
    }

    private StaffAccount find(UUID staffId) {
        return accountRepository.findById(staffId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "STAFF_ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản nhân viên."));
    }
}
