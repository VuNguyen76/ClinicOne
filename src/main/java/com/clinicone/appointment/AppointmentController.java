package com.clinicone.appointment;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(appointmentService.list(authentication.getName()));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<AppointmentResponse> get(Authentication authentication,
                                                    @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.get(authentication.getName(), appointmentId.toString()));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(Authentication authentication,
                                                       @Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(201).body(appointmentService.create(authentication.getName(), request));
    }

    @PostMapping("/{appointmentId}/cancel")
    public ResponseEntity<Void> cancel(Authentication authentication,
                                       @PathVariable UUID appointmentId,
                                       @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        appointmentService.cancel(authentication.getName(), appointmentId.toString(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{appointmentId}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(Authentication authentication,
                                                           @PathVariable UUID appointmentId,
                                                           @Valid @RequestBody RescheduleAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.reschedule(authentication.getName(), appointmentId.toString(), request));
    }
}
