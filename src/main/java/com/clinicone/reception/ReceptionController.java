package com.clinicone.reception;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.clinicone.patientprofile.PatientProfileResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reception")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'RECEPTIONIST')")
public class ReceptionController {
    private final ReceptionService service;

    public ReceptionController(ReceptionService service) {
        this.service = service;
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<ReceptionAppointmentResponse>> search(
            @RequestParam String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.search(query, date));
    }

    @PostMapping("/appointments/{appointmentId}/check-in")
    public ResponseEntity<ReceptionAppointmentResponse> checkIn(@PathVariable UUID appointmentId,
                                                                  @Valid @RequestBody ReceptionCheckInRequest request) {
        return ResponseEntity.ok(service.checkIn(appointmentId, request));
    }

    @PostMapping("/walk-in")
    public ResponseEntity<ReceptionAppointmentResponse> createWalkIn(
            @Valid @RequestBody ReceptionWalkInRequest request) {
        return ResponseEntity.ok(service.createWalkIn(request));
    }

    @GetMapping("/profiles")
    public ResponseEntity<List<PatientProfileResponse>> profiles(@RequestParam String phone) {
        return ResponseEntity.ok(service.profiles(phone));
    }
}
