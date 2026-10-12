package com.clinicone.invoice;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/patient-data")
public class PublicPatientDataLookupController {
    private final PublicPatientDataLookupService service;

    @PostMapping("/lookup")
    public ResponseEntity<PublicPatientDataLookupResponse> lookup(
            @Valid @RequestBody PublicPatientDataLookupRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.lookup(request.phone(), request.code()));
    }
}
