CREATE TABLE vehicle_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    host_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    file_id UUID NOT NULL,
    document_number VARCHAR(120),
    issued_at DATE,
    expires_at DATE NOT NULL,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE vehicle_documents
    ADD CONSTRAINT chk_vehicle_documents_type
    CHECK (type IN ('REGISTRATION', 'INSURANCE', 'INSPECTION'));

ALTER TABLE vehicle_documents
    ADD CONSTRAINT chk_vehicle_documents_status
    CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED'));

CREATE INDEX idx_vehicle_documents_vehicle_type_status
    ON vehicle_documents(vehicle_id, type, status);

CREATE INDEX idx_vehicle_documents_status_created
    ON vehicle_documents(status, created_at DESC);

CREATE INDEX idx_vehicle_documents_expires
    ON vehicle_documents(expires_at);
