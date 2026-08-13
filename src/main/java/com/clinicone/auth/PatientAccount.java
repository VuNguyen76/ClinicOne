package com.clinicone.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

@Entity
@Table(name = "patient_accounts")
public class PatientAccount {

    private static final int MAX_PASSWORD_FAILURES = 5;
    private static final Duration PASSWORD_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration TEMPORARY_LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    @JsonIgnore
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 500)
    private String address;

    @Column(name = "identity_number", length = 12)
    private String identityNumber;

    @Column(length = 100)
    private String nationality;

    @Column(length = 100)
    private String ethnicity;

    @Column(name = "province_code", length = 10)
    private String provinceCode;

    @Column(name = "province_name", length = 120)
    private String provinceName;

    @Column(name = "district_code", length = 10)
    private String districtCode;

    @Column(name = "district_name", length = 120)
    private String districtName;

    @Column(name = "ward_code", length = 10)
    private String wardCode;

    @Column(name = "ward_name", length = 120)
    private String wardName;

    @Column(name = "street_address", length = 500)
    private String streetAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_password_attempts", nullable = false)
    private int failedPasswordAttempts;

    @Column(name = "password_failure_window_started_at")
    private Instant passwordFailureWindowStartedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientAccount() {
    }

    public PatientAccount(String phone, String passwordHash, String fullName,
                          AccountStatus status, boolean mustChangePassword) {
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        clearPasswordFailures();
    }

    /** Records one failed password attempt and returns whether the account was locked. */
    public boolean recordPasswordFailure(Instant now) {
        if (passwordFailureWindowStartedAt == null
                || !now.isBefore(passwordFailureWindowStartedAt.plus(PASSWORD_FAILURE_WINDOW))) {
            failedPasswordAttempts = 0;
            passwordFailureWindowStartedAt = now;
        }
        failedPasswordAttempts++;
        if (failedPasswordAttempts < MAX_PASSWORD_FAILURES) {
            return false;
        }
        status = AccountStatus.LOCKED;
        lockedUntil = now.plus(TEMPORARY_LOCK_DURATION);
        return true;
    }

    /** Re-opens an automatically locked account once its 15-minute lock has elapsed. */
    public boolean unlockExpiredTemporaryLock(Instant now) {
        if (status != AccountStatus.LOCKED || lockedUntil == null || now.isBefore(lockedUntil)) {
            return false;
        }
        status = AccountStatus.ACTIVE;
        lockedUntil = null;
        clearPasswordFailures();
        return true;
    }

    public void clearPasswordFailures() {
        failedPasswordAttempts = 0;
        passwordFailureWindowStartedAt = null;
    }

    public void unlockAfterPasswordReset() {
        status = AccountStatus.ACTIVE;
        lockedUntil = null;
        clearPasswordFailures();
    }

    public void updateFullName(String fullName) {
        this.fullName = fullName;
    }

    public void updateProfile(String fullName, LocalDate dateOfBirth, String gender, String address) {
        this.fullName = fullName;
        if (dateOfBirth != null) {
            this.dateOfBirth = dateOfBirth;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (address != null) {
            this.address = address;
        }
    }

    public void updateIdentityAndAddress(String identityNumber, String nationality, String ethnicity,
                                         String provinceCode, String provinceName, String districtCode,
                                         String districtName, String wardCode, String wardName, String streetAddress) {
        this.identityNumber = identityNumber;
        this.nationality = nationality;
        this.ethnicity = ethnicity;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.districtCode = districtCode;
        this.districtName = districtName;
        this.wardCode = wardCode;
        this.wardName = wardName;
        this.streetAddress = streetAddress;
        if (hasValue(streetAddress) || hasValue(wardName) || hasValue(districtName) || hasValue(provinceName)) {
            this.address = joinAddress(streetAddress, wardName, districtName, provinceName);
        }
    }

    public void syncFromPrimaryProfile(String fullName, LocalDate dateOfBirth, String gender, String phone,
                                       String identityNumber, String nationality, String ethnicity, String address,
                                       String provinceCode, String provinceName, String districtCode,
                                       String districtName, String wardCode, String wardName, String streetAddress) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.identityNumber = identityNumber;
        this.nationality = nationality;
        this.ethnicity = ethnicity;
        this.address = address;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.districtCode = districtCode;
        this.districtName = districtName;
        this.wardCode = wardCode;
        this.wardName = wardName;
        this.streetAddress = streetAddress;
        // The account phone remains the login identifier; a profile phone is informational only.
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private String joinAddress(String street, String ward, String district, String province) {
        return Stream.of(street, ward, district, province)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String getAddress() { return address; }
    public String getIdentityNumber() { return identityNumber; }
    public String getNationality() { return nationality; }
    public String getEthnicity() { return ethnicity; }
    public String getProvinceCode() { return provinceCode; }
    public String getProvinceName() { return provinceName; }
    public String getDistrictCode() { return districtCode; }
    public String getDistrictName() { return districtName; }
    public String getWardCode() { return wardCode; }
    public String getWardName() { return wardName; }
    public String getStreetAddress() { return streetAddress; }
    public AccountStatus getStatus() { return status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getFailedPasswordAttempts() { return failedPasswordAttempts; }
    public Instant getPasswordFailureWindowStartedAt() { return passwordFailureWindowStartedAt; }
    public Instant getLockedUntil() { return lockedUntil; }
}
