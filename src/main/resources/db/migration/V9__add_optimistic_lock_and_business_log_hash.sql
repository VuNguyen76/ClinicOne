ALTER TABLE appointments
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE queue_tickets
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE examination_sessions
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE reconciliation_incidents
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE sms_deliveries
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE business_logs
    ADD COLUMN previous_hash character varying(64),
    ADD COLUMN hash character varying(64);
