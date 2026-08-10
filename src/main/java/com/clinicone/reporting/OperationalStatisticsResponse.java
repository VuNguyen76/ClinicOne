package com.clinicone.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

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
        BigDecimal averageExaminationMinutes,
        String groupBy,
        List<OperationalStatisticsBucket> buckets
) {
    public OperationalStatisticsResponse(LocalDate from, LocalDate to, String specialty, UUID doctorId,
                                         long totalAppointments, long checkedInAppointments, long absentAppointments,
                                         long cancelledAppointments, long completedAppointments,
                                         long notPerformedAppointments, BigDecimal averageWaitMinutes,
                                         BigDecimal averageExaminationMinutes) {
        this(from, to, specialty, doctorId, totalAppointments, checkedInAppointments, absentAppointments,
                cancelledAppointments, completedAppointments, notPerformedAppointments, averageWaitMinutes,
                averageExaminationMinutes, "DAY", List.of());
    }
}
