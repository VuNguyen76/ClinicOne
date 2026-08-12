package com.clinicone.config;

import com.clinicone.notification.PatientNotificationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayBaselineResourceTest {
    private static final List<String> EXPECTED_TABLES = List.of(
            "access_audit_events", "appointment_holds", "appointment_reschedule_cases", "appointments",
            "business_logs", "clinic_configuration", "clinic_rooms", "clinic_service_doctors",
            "clinic_services", "diagnosis_catalog", "doctor_profiles", "doctor_schedules", "doctor_time_off",
            "examination_sessions", "generated_clinic_slots", "login_sessions", "medical_records",
            "medication_catalog", "otp_challenges", "patient_accounts", "patient_notifications",
            "patient_profiles", "prescription_lines", "queue_tickets", "reason_catalog",
            "reconciliation_incidents", "sms_deliveries", "staff_account_roles", "staff_accounts",
            "work_schedule_template_breaks", "work_schedule_template_exceptions",
            "work_schedule_template_weekdays", "work_schedule_templates");

    @Test
    void baselineContainsOnlyTheClinicOneSchema() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V1__baseline_schema.sql")) {
            assertThat(input).as("Flyway baseline resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String table : EXPECTED_TABLES) {
            assertThat(sql).contains("CREATE TABLE public." + table + " (");
        }
        assertThat(sql.split("CREATE TABLE public.", -1).length - 1)
                .isEqualTo(EXPECTED_TABLES.size());
        assertThat(sql).doesNotContain("INSERT INTO", "COPY public.");
    }

    @Test
    void notificationTypeMigrationAllowsEveryPersistedNotificationType() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V2__expand_notification_types.sql")) {
            assertThat(input).as("notification type migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("DROP CONSTRAINT IF EXISTS patient_notifications_type_check");
        assertThat(sql).contains("ADD CONSTRAINT patient_notifications_type_check CHECK");
        for (String type : Arrays.stream(PatientNotificationType.values())
                .map(Enum::name)
                .toList()) {
            assertThat(sql).contains("'" + type + "'");
        }
    }
}
