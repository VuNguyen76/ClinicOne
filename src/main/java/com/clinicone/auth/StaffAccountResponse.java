package com.clinicone.auth;

import java.util.UUID;

public record StaffAccountResponse(UUID staffId, String username, String fullName,
                                   StaffRole role, AccountStatus status) {
    static StaffAccountResponse from(StaffAccount account) {
        return new StaffAccountResponse(account.getId(), account.getUsername(), account.getFullName(),
                account.getRole(), account.getStatus());
    }

    StaffAccountResponse withStatus(AccountStatus nextStatus) {
        return new StaffAccountResponse(staffId, username, fullName, role, nextStatus);
    }
}
