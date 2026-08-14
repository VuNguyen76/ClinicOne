package com.clinicone.audit;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/access-audit")
@PreAuthorize("hasRole('ADMIN')")
public class AccessAuditController {
    private final AccessAuditService service;

    @GetMapping
    public List<AccessAuditResponse> list(@RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(required = false) String actor,
                                          @RequestParam(required = false) String outcome,
                                          @RequestParam(required = false) String eventType) {
        return service.list(from, to, actor, outcome, eventType);
    }
}
