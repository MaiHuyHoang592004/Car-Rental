CREATE TABLE damage_claims (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    host_id UUID NOT NULL REFERENCES auth_users(id),
    customer_id UUID NOT NULL REFERENCES auth_users(id),
    check_out_report_id UUID NULL REFERENCES trip_condition_reports(id),
    status VARCHAR(40) NOT NULL,
    claim_amount NUMERIC(12,2) NOT NULL,
    approved_amount NUMERIC(12,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    customer_response TEXT NULL,
    admin_resolution_note TEXT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    customer_responded_at TIMESTAMPTZ NULL,
    resolved_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_damage_claim_amounts CHECK (
        claim_amount > 0
        AND (approved_amount IS NULL OR approved_amount >= 0)
        AND (approved_amount IS NULL OR approved_amount <= claim_amount)
    )
);

CREATE TABLE damage_claim_evidence (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES damage_claims(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES files(id),
    evidence_type VARCHAR(40) NOT NULL,
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_damage_claims_booking_created
    ON damage_claims(booking_id, created_at DESC);

CREATE INDEX idx_damage_claims_host_status_created
    ON damage_claims(host_id, status, created_at DESC);

CREATE INDEX idx_damage_claims_status_created
    ON damage_claims(status, created_at DESC);

CREATE INDEX idx_damage_claim_evidence_claim
    ON damage_claim_evidence(claim_id);
