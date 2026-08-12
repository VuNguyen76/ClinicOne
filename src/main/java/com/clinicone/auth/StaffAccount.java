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
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;

import java.time.Instant;
import java.util.UUID;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;

@Entity
@Table(name = "staff_accounts")
public class StaffAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "employee_code", unique = true, length = 20)
    private String employeeCode;

    @Column(name = "unit_name", length = 160)
    private String unitName;

    @Column(name = "department_name", length = 160)
    private String departmentName;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "staff_account_roles", joinColumns = @JoinColumn(name = "staff_account_id"))
    @jakarta.persistence.Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<StaffRole> roles = EnumSet.noneOf(StaffRole.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffAccount() {
    }

    private StaffAccount(String username, String passwordHash, String fullName, StaffRole role) {
        this(username, passwordHash, fullName, role, null, null, null, Set.of(role));
    }

    private StaffAccount(String username, String passwordHash, String fullName, StaffRole primaryRole,
                         String employeeCode, String unitName, String departmentName, Set<StaffRole> roles) {
        this.username = username.trim();
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.role = primaryRole;
        this.employeeCode = employeeCode == null || employeeCode.isBlank() ? null : employeeCode.trim();
        this.unitName = unitName == null || unitName.isBlank() ? null : unitName.trim();
        this.departmentName = departmentName == null || departmentName.isBlank() ? null : departmentName.trim();
        this.roles = roles == null || roles.isEmpty() ? EnumSet.of(primaryRole) : EnumSet.copyOf(roles);
        this.status = AccountStatus.ACTIVE;
    }

    public static StaffAccount create(String username, String passwordHash, String fullName, StaffRole role) {
        return new StaffAccount(username, passwordHash, fullName, role);
    }

    public static StaffAccount create(String username, String passwordHash, String fullName,
                                      String employeeCode, String unitName, String departmentName,
                                      Set<StaffRole> roles) {
        StaffRole primary = roles == null || roles.isEmpty() ? null : roles.iterator().next();
        if (primary == null) throw new IllegalArgumentException("At least one staff role is required");
        return new StaffAccount(username, passwordHash, fullName, primary, employeeCode, unitName,
                departmentName, roles);
    }

    public static StaffAccount create(String username, String passwordHash, String fullName,
                                      String employeeCode, String unitName, String departmentName,
                                      List<StaffRole> roles) {
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("At least one staff role is required");
        return new StaffAccount(username, passwordHash, fullName, roles.get(0), employeeCode, unitName,
                departmentName, EnumSet.copyOf(roles));
    }

    public void lock() { status = AccountStatus.LOCKED; }
    public void unlock() { status = AccountStatus.ACTIVE; }

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
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public StaffRole getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public Set<StaffRole> getRoles() {
        if (roles == null || roles.isEmpty()) {
            return role == null ? Set.of() : Set.of(role);
        }
        return Set.copyOf(roles);
    }
    public String getEmployeeCode() { return employeeCode; }
    public String getUnitName() { return unitName; }
    public String getDepartmentName() { return departmentName; }
    public boolean hasRole(StaffRole candidate) { return getRoles().contains(candidate); }
    public void replaceRoles(Set<StaffRole> nextRoles) {
        if (nextRoles == null || nextRoles.isEmpty()) throw new IllegalArgumentException("At least one staff role is required");
        this.roles = EnumSet.copyOf(nextRoles);
        this.role = nextRoles.iterator().next();
    }
}
