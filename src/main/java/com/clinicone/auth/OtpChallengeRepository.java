package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    Optional<OtpChallenge> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    long countByEmailAndPurposeAndCreatedAtAfter(String email, OtpPurpose purpose, Instant createdAfter);
}
