package com.clinicone.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorSlotBookingCount(UUID doctorStaffId, LocalDate appointmentDate, LocalTime startTime,
                                     long bookedCount) {
}
