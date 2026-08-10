package com.clinicone.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClinicConfigurationRepository extends JpaRepository<ClinicConfiguration, UUID> {
}
