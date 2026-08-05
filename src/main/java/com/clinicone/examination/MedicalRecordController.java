package com.clinicone.examination;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medical-records")
public class MedicalRecordController {
    private final MedicalRecordService service;

    public MedicalRecordController(MedicalRecordService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<MedicalRecordResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(authentication.getName()));
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<MedicalRecordResponse> get(Authentication authentication, @PathVariable UUID recordId) {
        return ResponseEntity.ok(service.get(authentication.getName(), recordId.toString()));
    }
}
