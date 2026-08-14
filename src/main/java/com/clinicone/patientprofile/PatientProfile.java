package com.clinicone.patientprofile;

import lombok.Getter;

import com.clinicone.auth.PatientAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "patient_profiles")
public class PatientProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id")
    private PatientAccount owner;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 50)
    private String relationship;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    private String gender;

    @Column(length = 10)
    private String phone;

    @Column(name = "identity_number", length = 12)
    private String identityNumber;

    @Column(length = 100)
    private String nationality;

    @Column(length = 100)
    private String ethnicity;

    @Column(length = 500)
    private String address;

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

    @Column(name = "primary_profile", nullable = false)
    private boolean primaryProfile;

    @Column(name = "temporary_profile")
    private Boolean temporaryProfile = false;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientProfile() {
    }

    private PatientProfile(PatientAccount owner, String fullName, String relationship, LocalDate dateOfBirth,
                           String gender, String phone, String identityNumber, String nationality,
                           String ethnicity, String address, String provinceCode, String provinceName,
                           String districtCode, String districtName, String wardCode, String wardName,
                           String streetAddress, boolean primaryProfile, boolean temporaryProfile) {
        this.owner = owner;
        this.fullName = fullName;
        this.relationship = relationship;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phone = phone;
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
        this.primaryProfile = primaryProfile;
        this.temporaryProfile = temporaryProfile;
        this.active = true;
    }

    public static PatientProfile create(PatientAccount owner, String fullName, String relationship,
                                         LocalDate dateOfBirth, String gender, String phone, String identityNumber,
                                         String nationality, String ethnicity, String address, boolean primaryProfile) {
        return create(owner, fullName, relationship, dateOfBirth, gender, phone, identityNumber, nationality,
                ethnicity, address, null, null, null, null, null, null, null, primaryProfile);
    }

    public static PatientProfile create(PatientAccount owner, String fullName, String relationship,
                                         LocalDate dateOfBirth, String gender, String phone, String identityNumber,
                                         String nationality, String ethnicity, String address, String provinceCode,
                                         String provinceName, String districtCode, String districtName, String wardCode,
                                         String wardName, String streetAddress, boolean primaryProfile) {
        return new PatientProfile(owner, fullName, relationship, dateOfBirth, gender, phone, identityNumber,
                nationality, ethnicity, address, provinceCode, provinceName, districtCode, districtName, wardCode,
                wardName, streetAddress, primaryProfile, false);
    }

    public static PatientProfile createTemporary(String fullName, LocalDate dateOfBirth, String gender,
                                                 String phone, String identityNumber, String nationality,
                                                 String ethnicity, String address) {
        return new PatientProfile(null, fullName, "Tạm tại quầy", dateOfBirth, gender, phone, identityNumber,
                nationality, ethnicity, address, null, null, null, null, null, null, null, false, true);
    }

    static PatientProfile forTest(UUID id, PatientAccount owner, String fullName, String relationship,
                                  LocalDate dateOfBirth, String gender, boolean primaryProfile) {
        PatientProfile profile = create(owner, fullName, relationship, dateOfBirth, gender, null, null, null, null,
                null, primaryProfile);
        profile.id = id;
        return profile;
    }

    public void update(String fullName, String relationship, LocalDate dateOfBirth, String gender, String phone,
                       String identityNumber, String nationality, String ethnicity, String address) {
        update(fullName, relationship, dateOfBirth, gender, phone, identityNumber, nationality, ethnicity, address,
                provinceCode, provinceName, districtCode, districtName, wardCode, wardName, streetAddress);
    }

    public void update(String fullName, String relationship, LocalDate dateOfBirth, String gender, String phone,
                       String identityNumber, String nationality, String ethnicity, String address, String provinceCode,
                       String provinceName, String districtCode, String districtName, String wardCode, String wardName,
                       String streetAddress) {
        this.fullName = fullName;
        this.relationship = relationship;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phone = phone;
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
    }

    public void archive() {
        this.active = false;
    }

    public void linkToAccount(PatientAccount account) {
        if (!isTemporaryProfile()) {
            throw new IllegalStateException("Hồ sơ đã thuộc về một tài khoản.");
        }
        this.owner = account;
        this.temporaryProfile = false;
    }

    public boolean isPrimaryProfile() { return primaryProfile; }
    public boolean isTemporaryProfile() { return Boolean.TRUE.equals(temporaryProfile); }
    public boolean isActive() { return active; }

    @PrePersist
    void onCreate() {
        if (temporaryProfile == null) {
            temporaryProfile = false;
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

}
