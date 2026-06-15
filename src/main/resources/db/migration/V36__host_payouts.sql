CREATE TABLE host_payout_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    host_id UUID NOT NULL UNIQUE,
    provider VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    account_holder_name VARCHAR(160) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    account_last4 VARCHAR(4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE host_payout_accounts
    ADD CONSTRAINT chk_host_payout_accounts_provider
    CHECK (provider IN ('MANUAL_BANK'));

ALTER TABLE host_payout_accounts
    ADD CONSTRAINT chk_host_payout_accounts_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED'));

CREATE TABLE host_payouts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    host_id UUID NOT NULL,
    payout_account_id UUID REFERENCES host_payout_accounts(id),
    status VARCHAR(30) NOT NULL,
    gross_amount NUMERIC(12, 2) NOT NULL,
    platform_fee_amount NUMERIC(12, 2) NOT NULL,
    net_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    hold_reason TEXT,
    admin_note TEXT,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    paid_by UUID,
    paid_at TIMESTAMPTZ,
    failed_by UUID,
    failed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE host_payouts
    ADD CONSTRAINT chk_host_payouts_status
    CHECK (status IN ('PENDING', 'ON_HOLD', 'APPROVED', 'PAID', 'FAILED', 'CANCELLED'));

CREATE INDEX idx_host_payouts_host_created
    ON host_payouts(host_id, created_at DESC);

CREATE INDEX idx_host_payouts_status_created
    ON host_payouts(status, created_at DESC);
