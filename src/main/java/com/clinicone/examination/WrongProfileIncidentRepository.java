package com.clinicone.examination;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WrongProfileIncidentRepository extends JpaRepository<WrongProfileIncident, UUID> {
}
