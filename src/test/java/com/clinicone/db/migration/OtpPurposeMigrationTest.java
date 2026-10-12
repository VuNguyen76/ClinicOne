package com.clinicone.db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;

class OtpPurposeMigrationTest {

    @Test
    void allowsReceiptLookupPurposeWhileKeepingExistingOtpPurposes() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:otp-purpose-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE otp_challenges (purpose varchar(20) NOT NULL, "
                    + "CONSTRAINT otp_challenges_purpose_check CHECK "
                    + "(purpose IN ('REGISTRATION', 'LOGIN', 'RECOVERY'))) ");

            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V33__allow_receipt_lookup_otp_purpose.sql")).populate(connection);

            assertThatInsertSucceeds(connection, "RECEIPT_LOOKUP");
            assertThatInsertSucceeds(connection, "RECOVERY");
        }
    }

    private static void assertThatInsertSucceeds(Connection connection, String purpose) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO otp_challenges (purpose) VALUES (?)")) {
            statement.setString(1, purpose);
            assertThatCode(statement::executeUpdate).doesNotThrowAnyException();
        }
    }
}
