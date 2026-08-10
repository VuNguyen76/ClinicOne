package com.clinicone.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
public class BusinessLogController {
    private final BusinessLogService service;
    private final BusinessLogIntegrityJob integrityJob;

    public BusinessLogController(BusinessLogService service, BusinessLogIntegrityJob integrityJob) {
        this.service = service;
        this.integrityJob = integrityJob;
    }

    @GetMapping("/appointments/{appointmentId}")
    public List<BusinessLogResponse> appointmentHistory(@PathVariable UUID appointmentId) {
        return service.list("APPOINTMENT", appointmentId);
    }

    @GetMapping("/queue-tickets/{ticketId}")
    public List<BusinessLogResponse> queueHistory(@PathVariable UUID ticketId) {
        return service.list("QUEUE_TICKET", ticketId);
    }

    @GetMapping("/examinations/{sessionId}")
    public List<BusinessLogResponse> examinationHistory(@PathVariable UUID sessionId) {
        return service.list("EXAMINATION", sessionId);
    }

    @PostMapping("/integrity-check")
    public BusinessLogIntegrityJob.IntegrityCheckResult integrityCheck() {
        return integrityJob.runOnce();
    }
}
