package com.clinicone.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class PostgresOtpIssuanceLock implements OtpIssuanceLock {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void lock(String destination) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> { }, destination);
    }
}
