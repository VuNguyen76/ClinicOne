CREATE TABLE IF NOT EXISTS medical_record_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    specialty VARCHAR(120) NOT NULL,
    clinic_service_id UUID NULL,
    description VARCHAR(500),
    field_definition VARCHAR(20000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_medical_record_template_code UNIQUE (code),
    CONSTRAINT fk_medical_record_template_service FOREIGN KEY (clinic_service_id) REFERENCES clinic_services(id)
);
CREATE INDEX IF NOT EXISTS idx_medical_record_template_scope
    ON medical_record_templates (active, specialty, clinic_service_id);
