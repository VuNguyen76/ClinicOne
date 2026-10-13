package com.clinicone.appointment;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DoctorAppointmentsService {
    private static final long MAX_RANGE_DAYS = 90;

    private final AppointmentRepository appointmentRepository;

    public DoctorAppointmentsService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<AppointmentResponse> list(UUID doctorStaffId, LocalDate from, LocalDate to) {
        if (doctorStaffId == null || from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DOCTOR_APPOINTMENT_RANGE_INVALID",
                    "Khoảng ngày không hợp lệ; vui lòng chọn tối đa 90 ngày.");
        }

        return appointmentRepository
                .findByDoctorStaffIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                        doctorStaffId, from, to)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }
}
