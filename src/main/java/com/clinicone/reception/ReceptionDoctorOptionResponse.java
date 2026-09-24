package com.clinicone.reception;

import com.clinicone.doctor.DoctorProfile;

import java.util.UUID;

public record ReceptionDoctorOptionResponse(
        UUID staffId,
        String fullName,
        String specialty,
        String roomCode,
        String roomName,
        String shiftStatus,
        int waitingCount,
        java.util.List<SlotInfo> slots
) {
    public record SlotInfo(java.time.LocalTime startTime, java.time.LocalTime endTime, int remaining) {}
    static ReceptionDoctorOptionResponse from(DoctorProfile profile) {
        return new ReceptionDoctorOptionResponse(profile.getStaffAccount().getId(),
                profile.getStaffAccount().getFullName(), profile.getSpecialty(),
                profile.getRoom().getCode(), profile.getRoom().getName(), "ACTIVE", 0, java.util.List.of());
    }
    static ReceptionDoctorOptionResponse from(DoctorProfile profile, String shiftStatus, int waitingCount, java.util.List<SlotInfo> slots) {
        return new ReceptionDoctorOptionResponse(profile.getStaffAccount().getId(),
                profile.getStaffAccount().getFullName(), profile.getSpecialty(),
                profile.getRoom().getCode(), profile.getRoom().getName(), shiftStatus, waitingCount, slots);
    }
}
