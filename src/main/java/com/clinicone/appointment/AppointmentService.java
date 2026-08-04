package com.clinicone.appointment;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AppointmentService {
    private final PatientAccountRepository accountRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository) {
        this.accountRepository = accountRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(String accountId) {
        UUID patientId = parseAccountId(accountId);
        return appointmentRepository.findByPatientIdOrderByAppointmentDateAscStartTimeAsc(patientId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional
    public AppointmentResponse create(String accountId, CreateAppointmentRequest request) {
        UUID patientId = parseAccountId(accountId);
        PatientAccount patient = accountRepository.findById(patientId)
                .orElseThrow(() -> authenticationRequired());
        LocalDate appointmentDate = request.appointmentDate();
        LocalTime startTime = request.startTime();
        if (appointmentRepository.findByPatientIdAndAppointmentDateAndStartTime(patientId, appointmentDate, startTime).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Bạn đã có lịch hẹn trong khung giờ này.");
        }

        Appointment appointment = Appointment.create(patient, nextAppointmentCode(), request.specialty().trim(),
                request.doctorName().trim(), appointmentDate, startTime, request.reason().trim());
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    private String nextAppointmentCode() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "CL-" + LocalDate.now().toString().replace("-", "") + "-" + suffix;
    }

    private UUID parseAccountId(String accountId) {
        try {
            return UUID.fromString(accountId);
        } catch (IllegalArgumentException exception) {
            throw authenticationRequired();
        }
    }

    private AuthException authenticationRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Phiên đăng nhập không hợp lệ.");
    }
}
