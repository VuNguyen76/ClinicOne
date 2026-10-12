package com.clinicone.reception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class PostgresTemporaryProfileCreationLock implements TemporaryProfileCreationLock {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void lock(String normalizedPhone) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", resultSet -> { },
                "TEMPORARY_PROFILE:" + normalizedPhone);
    }
}
