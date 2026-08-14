package com.clinicone.concurrency;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in PostgreSQL test for the same row-lock behavior used by reception capacity checks.
 * It never falls back to the developer or production database. Set all three
 * CLINICONE_POSTGRES_TEST_* variables to run it.
 */
class PostgresConcurrencyTest {
    @Test
    void secondTransactionWaitsForFirstTransactionToReleaseTheLock() throws Exception {
        String url = System.getenv("CLINICONE_POSTGRES_TEST_URL");
        String user = System.getenv("CLINICONE_POSTGRES_TEST_USER");
        String password = System.getenv("CLINICONE_POSTGRES_TEST_PASSWORD");
        assumeTrue(url != null && !url.isBlank() && user != null && password != null,
                "Set CLINICONE_POSTGRES_TEST_URL/USER/PASSWORD to run PostgreSQL integration tests");

        try (Connection first = DriverManager.getConnection(url, user, password);
             Connection second = DriverManager.getConnection(url, user, password)) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            int lockKey = 178214;
            try (PreparedStatement statement = first.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setInt(1, lockKey);
                statement.execute();
            }

            CountDownLatch secondStarted = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Long> waitMillis = executor.submit(() -> {
                secondStarted.countDown();
                long startedAt = System.nanoTime();
                try (PreparedStatement statement = second.prepareStatement("select pg_advisory_xact_lock(?)")) {
                    statement.setInt(1, lockKey);
                    statement.execute();
                }
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            });
            assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            assertThat(waitMillis.isDone()).as("the competing transaction must wait").isFalse();
            first.commit();
            assertThat(waitMillis.get(3, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(100);
            second.commit();
            executor.shutdownNow();
        }
    }
}
