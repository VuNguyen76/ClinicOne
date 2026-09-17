-- Migration V18: Add Billing (Invoices, Invoice Items), Inventory Transactions, and Paraclinical Lab Order Lines

-- 1. Extend medication_catalog with inventory and pricing columns
ALTER TABLE medication_catalog
    ADD COLUMN IF NOT EXISTS unit VARCHAR(30) DEFAULT 'viên',
    ADD COLUMN IF NOT EXISTS unit_price BIGINT DEFAULT 5000 NOT NULL,
    ADD COLUMN IF NOT EXISTS cost_price BIGINT DEFAULT 3000 NOT NULL,
    ADD COLUMN IF NOT EXISTS stock_quantity INT DEFAULT 100 NOT NULL,
    ADD COLUMN IF NOT EXISTS min_stock_threshold INT DEFAULT 20 NOT NULL;

-- 2. Paraclinical Lab Order Lines
CREATE TABLE IF NOT EXISTS lab_order_lines (
    id UUID PRIMARY KEY,
    medical_record_id UUID NOT NULL REFERENCES medical_records(id) ON DELETE CASCADE,
    service_id UUID,
    service_name VARCHAR(200) NOT NULL,
    service_group VARCHAR(60),
    price BIGINT NOT NULL DEFAULT 0,
    result_notes VARCHAR(2000),
    line_number INT NOT NULL,
    CONSTRAINT uk_lab_order_lines_record_order UNIQUE (medical_record_id, line_number)
);

CREATE INDEX IF NOT EXISTS idx_lab_order_lines_record ON lab_order_lines(medical_record_id);

-- 3. Inventory Transactions
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id UUID PRIMARY KEY,
    medication_id UUID NOT NULL REFERENCES medication_catalog(id) ON DELETE CASCADE,
    transaction_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    stock_before INT NOT NULL,
    stock_after INT NOT NULL,
    unit_cost BIGINT NOT NULL DEFAULT 0,
    lot_number VARCHAR(60),
    expiry_date DATE,
    supplier VARCHAR(200),
    reference_code VARCHAR(80),
    notes VARCHAR(500),
    actor VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inventory_tx_medication ON inventory_transactions(medication_id);
CREATE INDEX IF NOT EXISTS idx_inventory_tx_created_at ON inventory_transactions(created_at DESC);

-- 4. Invoices
CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY,
    invoice_code VARCHAR(60) NOT NULL,
    appointment_id UUID REFERENCES appointments(id),
    session_id UUID REFERENCES examination_sessions(id),
    patient_name VARCHAR(120) NOT NULL,
    patient_phone VARCHAR(30),
    doctor_name VARCHAR(120),
    room_name VARCHAR(120),
    specialty VARCHAR(120),
    exam_fee BIGINT NOT NULL DEFAULT 0,
    lab_fee BIGINT NOT NULL DEFAULT 0,
    medication_fee BIGINT NOT NULL DEFAULT 0,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    total_amount BIGINT NOT NULL DEFAULT 0,
    paid_amount BIGINT NOT NULL DEFAULT 0,
    payment_method VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    vietqr_url VARCHAR(1000),
    cashier_name VARCHAR(120),
    notes VARCHAR(500),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_invoices_code UNIQUE (invoice_code),
    CONSTRAINT uk_invoices_session UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON invoices(created_at DESC);

-- 5. Invoice Items
CREATE TABLE IF NOT EXISTS invoice_items (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    item_type VARCHAR(30) NOT NULL,
    reference_id UUID,
    item_name VARCHAR(200) NOT NULL,
    unit VARCHAR(30),
    quantity INT NOT NULL DEFAULT 1,
    unit_price BIGINT NOT NULL DEFAULT 0,
    total_price BIGINT NOT NULL DEFAULT 0,
    line_number INT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id);
