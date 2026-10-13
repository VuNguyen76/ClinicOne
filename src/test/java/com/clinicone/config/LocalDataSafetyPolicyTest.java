package com.clinicone.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDataSafetyPolicyTest {
    @Test
    void localFixturesAreDisabledUnlessExplicitlyEnabled() {
        assertThat(LocalDataSafetyPolicy.shouldRun(false, "jdbc:postgresql://localhost:5432/clinicone"))
                .isFalse();
        assertThat(LocalDataSafetyPolicy.shouldRun(false,
                "jdbc:postgresql://pg-chuyensaigon-prod-10930.postgres.database.azure.com:5432/clinicone"))
                .isFalse();
    }

    @Test
    void explicitlyEnabledFixturesCanOnlyTargetLocalDatabases() {
        assertThat(LocalDataSafetyPolicy.shouldRun(true, "jdbc:h2:mem:clinicone-test"))
                .isTrue();
        assertThat(LocalDataSafetyPolicy.shouldRun(true, "jdbc:postgresql://127.0.0.1:5432/clinicone"))
                .isTrue();
        assertThatThrownBy(() -> LocalDataSafetyPolicy.shouldRun(true,
                "jdbc:postgresql://pg-chuyensaigon-prod-10930.postgres.database.azure.com:5432/clinicone"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-local database");
    }
}
