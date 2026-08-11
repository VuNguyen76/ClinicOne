package com.clinicone.reception;

import com.clinicone.doctor.DoctorProfile;

import java.util.UUID;

public record ReceptionDoctorOptionResponse(
        UUID staffId,
        String fullName,
        String specialty,
        String roomCode,
        String roomName
) {
    static ReceptionDoctorOptionResponse from(DoctorProfile profile) {
        return new ReceptionDoctorOptionResponse(profile.getStaffAccount().getId(),
                profile.getStaffAccount().getFullName(), profile.getSpecialty(),
                profile.getRoom().getCode(), profile.getRoom().getName());
    }
}
