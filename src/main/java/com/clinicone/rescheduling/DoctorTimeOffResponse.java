package com.clinicone.rescheduling;

import java.time.LocalDate;
import java.util.UUID;

public record DoctorTimeOffResponse(
        UUID id,
        UUID doctorId,
        String doctorName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        int lockedSlotCount,
        int releasedHoldCount,
        int affectedAppointmentCount,
        boolean active
) {
    static DoctorTimeOffResponse from(DoctorTimeOff item, int lockedSlotCount, int releasedHoldCount,
                                      int affectedAppointmentCount) {
        return new DoctorTimeOffResponse(item.getId(), item.getDoctorProfile().getStaffAccount().getId(),
                item.getDoctorProfile().getStaffAccount().getFullName(), item.getStartDate(), item.getEndDate(),
                item.getReason(), lockedSlotCount, releasedHoldCount, affectedAppointmentCount, item.isActive());
    }
}
