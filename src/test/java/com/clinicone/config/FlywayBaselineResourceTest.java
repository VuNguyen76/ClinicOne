package com.clinicone.config;

import com.clinicone.notification.PatientNotificationType;
import com.clinicone.queue.QueueTicketStatus;
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

    @Test
    void queueStatusMigrationAllowsEveryPersistedQueueStatus() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V3__expand_queue_ticket_status.sql")) {
            assertThat(input).as("queue status migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("DROP CONSTRAINT IF EXISTS queue_tickets_status_check");
        assertThat(sql).contains("ADD CONSTRAINT queue_tickets_status_check CHECK");
        for (String status : Arrays.stream(QueueTicketStatus.values()).map(Enum::name).toList()) {
            assertThat(sql).contains("'" + status + "'");
        }
    }

    @Test
    void signRequestKeyMigrationAddsTheColumnUsedByExaminationSessions() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V4__add_examination_sign_request_key.sql")) {
            assertThat(input).as("examination sign request key migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ALTER TABLE public.examination_sessions");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS sign_request_key");
    }

    @Test
    void passwordFailureMigrationAddsThePatientLockColumns() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V5__add_patient_password_failure_columns.sql")) {
            assertThat(input).as("patient password failure migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ALTER TABLE public.patient_accounts");
        assertThat(sql).contains("failed_password_attempts");
        assertThat(sql).contains("password_failure_window_started_at");
        assertThat(sql).contains("locked_until");
    }

    @Test
    void temporaryProfileMigrationAddsTheTemporaryProfileFlag() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V6__add_temporary_profile_flag.sql")) {
            assertThat(input).as("temporary profile migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ALTER TABLE public.patient_profiles");
        assertThat(sql).contains("temporary_profile");
    }

    @Test
    void queueClosureMigrationAddsTheClosureOutcomeColumn() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V7__add_queue_closure_outcome.sql")) {
            assertThat(input).as("queue closure migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ALTER TABLE public.queue_tickets");
        assertThat(sql).contains("closure_outcome");
    }

    @Test
    void wrongProfileIncidentMigrationCreatesTheIncidentSnapshotTable() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V8__add_wrong_profile_incidents.sql")) {
            assertThat(input).as("wrong profile migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS public.wrong_profile_incidents");
        assertThat(sql).contains("prescription_snapshot");
    }
}
