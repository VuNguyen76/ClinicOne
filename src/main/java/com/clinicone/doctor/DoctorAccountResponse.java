package com.clinicone.doctor;

import com.clinicone.auth.StaffAccount;

import java.util.UUID;

public record DoctorAccountResponse(UUID staffId, String username, String fullName, String specialty,
                                    UUID roomId, String roomCode, String roomName, boolean assigned, boolean active) {
    static DoctorAccountResponse from(StaffAccount staff, DoctorProfile profile) {
        var room = profile.getRoom();
        return new DoctorAccountResponse(staff.getId(), staff.getUsername(), staff.getFullName(), profile.getSpecialty(),
                room.getId(), room.getCode(), room.getName(), true, profile.isActive());
    }

    static DoctorAccountResponse unassigned(StaffAccount staff) {
        return new DoctorAccountResponse(staff.getId(), staff.getUsername(), staff.getFullName(), null,
                null, null, null, false, false);
    }
}
