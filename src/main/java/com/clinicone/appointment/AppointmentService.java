package com.clinicone.appointment;

import com.clinicone.auth.AuthException;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
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
    private final PatientProfileRepository profileRepository;

    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository) {
        this(accountRepository, appointmentRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentService(PatientAccountRepository accountRepository, AppointmentRepository appointmentRepository,
                              PatientProfileRepository profileRepository) {
        this.accountRepository = accountRepository;
        this.appointmentRepository = appointmentRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(String accountId) {
        UUID patientId = parseAccountId(accountId);
        return appointmentRepository.findByPatientIdOrderByAppointmentDateAscStartTimeAsc(patientId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(String accountId, String appointmentId) {
        UUID patientId = parseAccountId(accountId);
        UUID id = parseAppointmentId(appointmentId);
        return AppointmentResponse.from(findOwned(id, patientId));
    }

    @Transactional
    public AppointmentResponse create(String accountId, CreateAppointmentRequest request) {
        UUID patientId = parseAccountId(accountId);
        PatientAccount patient = accountRepository.findById(patientId)
                .orElseThrow(() -> authenticationRequired());
        LocalDate appointmentDate = request.appointmentDate();
        LocalTime startTime = request.startTime();
        if (appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                patientId, appointmentDate, startTime, AppointmentStatus.BOOKED).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Bạn đã có lịch hẹn trong khung giờ này.");
        }

        PatientProfile profile = resolveProfile(request.profileId(), patientId);
        Appointment appointment = profile == null
                ? Appointment.create(patient, nextAppointmentCode(), request.specialty().trim(), request.doctorName().trim(),
                appointmentDate, startTime, request.reason().trim())
                : Appointment.create(patient, profile, nextAppointmentCode(), request.specialty().trim(),
                request.doctorName().trim(), appointmentDate, startTime, request.reason().trim());
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    private PatientProfile resolveProfile(UUID profileId, UUID patientId) {
        if (profileId == null || profileRepository == null) {
            return null;
        }
        return profileRepository.findByIdAndOwnerIdAndActiveTrue(profileId, patientId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PATIENT_PROFILE_NOT_FOUND",
                        "Không tìm thấy hồ sơ được chọn."));
    }

    @Transactional
    public void cancel(String accountId, String appointmentId, CancelAppointmentRequest request) {
        Appointment appointment = findOwned(parseAppointmentId(appointmentId), parseAccountId(accountId));
        ensureBookable(appointment);
        appointment.cancel(request == null ? null : request.reason());
        appointmentRepository.save(appointment);
    }

    @Transactional
    public AppointmentResponse reschedule(String accountId, String appointmentId, RescheduleAppointmentRequest request) {
        UUID patientId = parseAccountId(accountId);
        Appointment appointment = findOwned(parseAppointmentId(appointmentId), patientId);
        ensureBookable(appointment);
        boolean sameSlot = appointment.getAppointmentDate().equals(request.appointmentDate())
                && appointment.getStartTime().equals(request.startTime());
        if (!sameSlot && appointmentRepository.findByPatientIdAndAppointmentDateAndStartTimeAndStatus(
                patientId, request.appointmentDate(), request.startTime(), AppointmentStatus.BOOKED).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_DUPLICATE",
                    "Bạn đã có lịch hẹn trong khung giờ này.");
        }
        appointment.reschedule(request.appointmentDate(), request.startTime());
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

    private UUID parseAppointmentId(String appointmentId) {
        try {
            return UUID.fromString(appointmentId);
        } catch (IllegalArgumentException exception) {
            throw appointmentNotFound();
        }
    }

    private Appointment findOwned(UUID appointmentId, UUID patientId) {
        return appointmentRepository.findByIdAndPatientId(appointmentId, patientId)
                .orElseThrow(this::appointmentNotFound);
    }

    private void ensureBookable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AuthException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_ACTIONABLE",
                    "Lịch hẹn này không còn cho phép thao tác.");
        }
    }

    private AuthException appointmentNotFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.");
    }

    private AuthException authenticationRequired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Phiên đăng nhập không hợp lệ.");
    }
}
