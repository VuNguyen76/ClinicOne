package com.clinicone.schedule;

import com.clinicone.doctor.DoctorProfile;

import java.util.UUID;

public record EligibleDoctorResponse(UUID doctorProfileId, UUID staffId, String fullName) {
    public static EligibleDoctorResponse from(DoctorProfile profile) {
        return new EligibleDoctorResponse(profile.getId(), profile.getStaffAccount().getId(),
                profile.getStaffAccount().getFullName());
    }
}
