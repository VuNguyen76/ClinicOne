package com.clinicone.reporting;

import java.math.BigDecimal;

public record OperationalStatisticsBucket(
        String period,
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
