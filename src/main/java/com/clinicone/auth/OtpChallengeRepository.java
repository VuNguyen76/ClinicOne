package com.clinicone.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    Optional<OtpChallenge> findTopByDestinationAndPurposeOrderByCreatedAtDesc(String destination, OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from OtpChallenge challenge where challenge.destination = :destination "
            + "and challenge.purpose = :purpose order by challenge.createdAt desc")
    List<OtpChallenge> findLatestForUpdate(@Param("destination") String destination,
                                            @Param("purpose") OtpPurpose purpose, Pageable pageable);

    long countByDestinationAndCreatedAtAfter(String destination, Instant createdAfter);
}
