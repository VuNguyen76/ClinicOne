package com.clinicone.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OperationalStatisticsResponse(
        LocalDate from,
        LocalDate to,
        String specialty,
        UUID doctorId,
        long totalAppointments,
        long checkedInAppointments,
        long absentAppointments,
        long cancelledAppointments,
        long completedAppointments,
        long notPerformedAppointments,
        BigDecimal averageWaitMinutes,
        BigDecimal averageExaminationMinutes
) {
}
