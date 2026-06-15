CREATE TABLE booking_deposits (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    customer_id UUID NOT NULL REFERENCES auth_users(id),
    host_id UUID NOT NULL REFERENCES auth_users(id),
    status VARCHAR(40) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    held_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    released_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    deducted_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    hold_expires_at TIMESTAMPTZ NULL,
    release_eligible_at TIMESTAMPTZ NULL,
    released_at TIMESTAMPTZ NULL,
    provider VARCHAR(40) NOT NULL,
    provider_hold_id VARCHAR(120) NULL,
    provider_status VARCHAR(80) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_booking_deposit_amounts CHECK (
        amount >= 0
        AND held_amount >= 0
        AND released_amount >= 0
        AND deducted_amount >= 0
        AND deducted_amount <= held_amount
        AND released_amount <= held_amount
    )
);

CREATE TABLE deposit_transactions (
    id UUID PRIMARY KEY,
    booking_deposit_id UUID NOT NULL REFERENCES booking_deposits(id),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    provider VARCHAR(40) NOT NULL,
    provider_ref VARCHAR(120) NULL,
    idempotency_key_id UUID NULL REFERENCES idempotency_keys(id),
    error_code VARCHAR(80) NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_booking_deposits_status_created
    ON booking_deposits(status, created_at DESC);

CREATE INDEX idx_booking_deposits_host_status
    ON booking_deposits(host_id, status);

CREATE INDEX idx_deposit_transactions_deposit_created
    ON deposit_transactions(booking_deposit_id, created_at DESC);
