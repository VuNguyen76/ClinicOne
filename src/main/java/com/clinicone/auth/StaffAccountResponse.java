package com.clinicone.auth;

import java.util.UUID;
import java.util.Set;

public record StaffAccountResponse(UUID staffId, String username, String fullName,
                                   StaffRole role, AccountStatus status, String employeeCode,
                                   String unitName, String departmentName, Set<StaffRole> roles) {
    public StaffAccountResponse(UUID staffId, String username, String fullName, StaffRole role, AccountStatus status) {
        this(staffId, username, fullName, role, status, null, null, null,
                role == null ? Set.of() : Set.of(role));
    }

    static StaffAccountResponse from(StaffAccount account) {
        return new StaffAccountResponse(account.getId(), account.getUsername(), account.getFullName(),
                account.getRole(), account.getStatus(), account.getEmployeeCode(), account.getUnitName(),
                account.getDepartmentName(), account.getRoles());
    }

    StaffAccountResponse withStatus(AccountStatus nextStatus) {
        return new StaffAccountResponse(staffId, username, fullName, role, nextStatus,
                employeeCode, unitName, departmentName, roles);
    }
}
