package com.clinicone.patientprofile;

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

@Entity
@Table(name = "patient_profiles")
public class PatientProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_account_id", nullable = false)
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
                           String streetAddress, boolean primaryProfile) {
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
                wardName, streetAddress, primaryProfile);
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

    public UUID getId() { return id; }
    public PatientAccount getOwner() { return owner; }
    public String getFullName() { return fullName; }
    public String getRelationship() { return relationship; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String getPhone() { return phone; }
    public String getIdentityNumber() { return identityNumber; }
    public String getNationality() { return nationality; }
    public String getEthnicity() { return ethnicity; }
    public String getAddress() { return address; }
    public String getProvinceCode() { return provinceCode; }
    public String getProvinceName() { return provinceName; }
    public String getDistrictCode() { return districtCode; }
    public String getDistrictName() { return districtName; }
    public String getWardCode() { return wardCode; }
    public String getWardName() { return wardName; }
    public String getStreetAddress() { return streetAddress; }
    public boolean isPrimaryProfile() { return primaryProfile; }
    public boolean isActive() { return active; }
}
