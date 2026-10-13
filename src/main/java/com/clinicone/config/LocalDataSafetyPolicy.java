package com.clinicone.config;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class LocalDataSafetyPolicy {
    private static final Set<String> LOCAL_POSTGRES_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private LocalDataSafetyPolicy() {
    }

    static boolean shouldRun(boolean enabled, String datasourceUrl) {
        if (!enabled) {
            return false;
        }
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException("Local data initializer requires an explicit local database URL.");
        }

        if (isLocalDatabaseUrl(datasourceUrl)) {
            return true;
        }
        throw new IllegalStateException("Refusing to run local data initializer against a non-local database.");
    }

    static boolean isLocalDatabaseUrl(String datasourceUrl) {
        if (datasourceUrl == null || datasourceUrl.isBlank()) return false;
        String normalizedUrl = datasourceUrl.trim().toLowerCase(Locale.ROOT);
        if (normalizedUrl.startsWith("jdbc:h2:")) return true;
        if (!normalizedUrl.startsWith("jdbc:postgresql://")) return false;
        String host = URI.create(datasourceUrl.substring("jdbc:".length())).getHost();
        return host != null && LOCAL_POSTGRES_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }
}
