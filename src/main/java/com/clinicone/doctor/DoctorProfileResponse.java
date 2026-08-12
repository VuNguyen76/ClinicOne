package com.clinicone.doctor;

import java.util.UUID;

public record DoctorProfileResponse(UUID staffId, String username, String fullName, String specialty,
                                    UUID roomId, String roomCode, String roomName, boolean active) {
    public static DoctorProfileResponse from(DoctorProfile profile) {
        var staff = profile.getStaffAccount();
        var room = profile.getRoom();
        return new DoctorProfileResponse(staff.getId(), staff.getUsername(), staff.getFullName(), profile.getSpecialty(),
                room.getId(), room.getCode(), room.getName(), profile.isActive());
    }
}
