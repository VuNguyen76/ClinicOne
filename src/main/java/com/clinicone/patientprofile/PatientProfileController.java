package com.clinicone.patientprofile;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patient-profiles")
public class PatientProfileController {
    private final PatientProfileService service;

    public PatientProfileController(PatientProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PatientProfileResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<PatientProfileResponse> create(Authentication authentication,
                                                          @Valid @RequestBody CreatePatientProfileRequest request) {
        return ResponseEntity.status(201).body(service.create(authentication.getName(), request));
    }

    @PatchMapping("/{profileId}")
    public ResponseEntity<PatientProfileResponse> update(Authentication authentication,
                                                         @PathVariable String profileId,
                                                         @Valid @RequestBody UpdatePatientProfileRequest request) {
        return ResponseEntity.ok(service.update(authentication.getName(), profileId, request));
    }

    // Endpoint dành riêng cho Lễ tân cập nhật hồ sơ thiếu
    @PatchMapping("/{profileId}/reception-update")
    public ResponseEntity<PatientProfileResponse> updateByReceptionist(
            @PathVariable String profileId,
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        return ResponseEntity.ok(service.updateMissingDataByReceptionist(profileId, request));
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable String profileId) {
        service.delete(authentication.getName(), profileId);
        return ResponseEntity.noContent().build();
    }
}
