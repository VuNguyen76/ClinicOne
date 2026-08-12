package com.clinicone.doctor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/doctors")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public class DoctorManagementController {
    private final DoctorManagementService service;

    public DoctorManagementController(DoctorManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<DoctorAccountResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<DoctorAccountResponse> create(@Valid @RequestBody DoctorCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDoctor(request));
    }

    @PutMapping("/{staffId}/assignment")
    public ResponseEntity<DoctorProfileResponse> assign(@PathVariable UUID staffId,
                                                        @Valid @RequestBody DoctorAssignmentRequest request) {
        return ResponseEntity.ok(service.assign(staffId, request));
    }

    @GetMapping("/{staffId}/schedules")
    public ResponseEntity<List<DoctorScheduleResponse>> schedules(@PathVariable UUID staffId) {
        return ResponseEntity.ok(service.schedules(staffId));
    }

    @PostMapping("/{staffId}/schedules")
    public ResponseEntity<DoctorScheduleResponse> addSchedule(@PathVariable UUID staffId,
                                                              @Valid @RequestBody DoctorScheduleRequest request) {
        return ResponseEntity.ok(service.addSchedule(staffId, request));
    }

    @DeleteMapping("/{staffId}/schedules/{scheduleId}")
    public ResponseEntity<Void> removeSchedule(@PathVariable UUID staffId, @PathVariable UUID scheduleId) {
        service.removeSchedule(staffId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
