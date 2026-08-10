package com.clinicone.auth;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffManagementController {
    private final StaffManagementService service;

    public StaffManagementController(StaffManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<StaffAccountResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping("/{staffId}/lock")
    public ResponseEntity<StaffAccountResponse> lock(@PathVariable @NotNull UUID staffId, Principal principal) {
        return ResponseEntity.ok(service.lock(staffId, principal == null ? "SYSTEM" : principal.getName()));
    }

    @PostMapping("/{staffId}/unlock")
    public ResponseEntity<StaffAccountResponse> unlock(@PathVariable @NotNull UUID staffId, Principal principal) {
        return ResponseEntity.ok(service.unlock(staffId, principal == null ? "SYSTEM" : principal.getName()));
    }
}
