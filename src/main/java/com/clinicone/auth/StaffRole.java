package com.clinicone.auth;

public enum StaffRole {
    ADMIN,
    COORDINATOR,
    RECEPTIONIST,
    DOCTOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
