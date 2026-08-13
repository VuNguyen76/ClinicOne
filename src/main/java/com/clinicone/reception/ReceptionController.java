package com.clinicone.reception;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.clinicone.auth.RequestOtpResponse;
import com.clinicone.queue.QueueLeaveRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reception")
@PreAuthorize("hasAnyRole('COORDINATOR', 'RECEPTIONIST')")
public class ReceptionController {
    private final ReceptionService service;
    private final ReceptionPatientService patientService;

    public ReceptionController(ReceptionService service, ReceptionPatientService patientService) {
        this.service = service;
        this.patientService = patientService;
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<ReceptionAppointmentResponse>> search(
            @RequestParam String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.search(query, date));
    }

    @PostMapping("/appointments/{appointmentId}/check-in")
    public ResponseEntity<ReceptionAppointmentResponse> checkIn(@PathVariable UUID appointmentId,
                                                                  @Valid @RequestBody ReceptionCheckInRequest request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(service.checkIn(appointmentId, request, authentication.getName()));
    }

    @PostMapping("/appointments/{appointmentId}/leave")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<ReceptionAppointmentResponse> leaveBeforeExam(@PathVariable UUID appointmentId,
                                                                          @Valid @RequestBody QueueLeaveRequest request,
                                                                          Authentication authentication) {
        return ResponseEntity.ok(service.leaveBeforeExam(appointmentId, request.reason(), authentication.getName()));
    }

    @PostMapping("/appointments/{appointmentId}/facility-unavailable")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'COORDINATOR')")
    public ResponseEntity<ReceptionAppointmentResponse> markFacilityUnavailable(
            @PathVariable UUID appointmentId, @Valid @RequestBody QueueLeaveRequest request,
            org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(service.markFacilityUnavailable(appointmentId, request.reason(), authentication.getName()));
    }

    @PostMapping("/walk-in")
    public ResponseEntity<ReceptionAppointmentResponse> createWalkIn(
            @Valid @RequestBody ReceptionWalkInRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.createWalkIn(request, authentication.getName()));
    }

    @PostMapping("/temporary-profiles")
    public ResponseEntity<ReceptionPatientProfileResponse> createTemporaryProfile(
            @Valid @RequestBody ReceptionTemporaryProfileRequest request) {
        return ResponseEntity.status(201).body(service.createTemporaryProfile(request));
    }

    @GetMapping("/profiles")
    public ResponseEntity<List<ReceptionPatientProfileResponse>> profiles(@RequestParam String phone) {
        return ResponseEntity.ok(service.profiles(phone));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<ReceptionDoctorOptionResponse>> doctors() {
        return ResponseEntity.ok(service.doctors());
    }

    @PostMapping("/patients/request-otp")
    public ResponseEntity<RequestOtpResponse> requestPatientOtp(
            @Valid @RequestBody ReceptionPatientOtpRequest request) {
        return ResponseEntity.ok(patientService.requestOtp(request));
    }

    @PostMapping("/patients")
    public ResponseEntity<ReceptionPatientRegistrationResponse> registerPatient(
            @Valid @RequestBody ReceptionPatientRegistrationRequest request) {
        return ResponseEntity.status(201).body(patientService.register(request));
    }
}
