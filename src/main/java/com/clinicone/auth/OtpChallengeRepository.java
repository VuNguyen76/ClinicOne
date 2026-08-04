package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    Optional<OtpChallenge> findTopByDestinationAndPurposeOrderByCreatedAtDesc(String destination, OtpPurpose purpose);

    long countByDestinationAndPurposeAndCreatedAtAfter(String destination, OtpPurpose purpose, Instant createdAfter);
}
