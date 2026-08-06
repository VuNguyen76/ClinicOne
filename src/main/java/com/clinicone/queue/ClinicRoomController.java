package com.clinicone.queue;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/rooms")
public class ClinicRoomController {
    private final ClinicRoomService service;

    public ClinicRoomController(ClinicRoomService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<ClinicRoomResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClinicRoomResponse> create(@Valid @RequestBody CreateClinicRoomRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClinicRoomResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateClinicRoomRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClinicRoomResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClinicRoomResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.setActive(id, false));
    }
}
