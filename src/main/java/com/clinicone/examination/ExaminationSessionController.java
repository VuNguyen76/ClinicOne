package com.clinicone.examination;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/examinations")
public class ExaminationSessionController {
    private final ExaminationSessionService service;

    public ExaminationSessionController(ExaminationSessionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ExaminationSessionResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(service.list(authentication.getName()));
    }
}
