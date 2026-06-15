CREATE TABLE booking_modification_requests (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    requester_id UUID NOT NULL REFERENCES auth_users(id),
    requester_role VARCHAR(20) NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    current_pickup_date DATE NOT NULL,
    current_return_date DATE NOT NULL,
    requested_pickup_date DATE NULL,
    requested_return_date DATE NULL,
    current_pickup_location TEXT NULL,
    current_return_location TEXT NULL,
    requested_pickup_location TEXT NULL,
    requested_return_location TEXT NULL,
    price_delta NUMERIC(12,2) NOT NULL DEFAULT 0,
    fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    reason TEXT NULL,
    host_response_note TEXT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE late_return_fees (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    status VARCHAR(30) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    expected_return_date DATE NOT NULL,
    actual_checkout_at TIMESTAMPTZ NULL,
    days_late INT NOT NULL,
    fee_amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    waived_by UUID NULL REFERENCES auth_users(id),
    waiver_reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_booking_mod_requests_booking_created
    ON booking_modification_requests(booking_id, created_at DESC);

CREATE INDEX idx_booking_mod_requests_host_status
    ON booking_modification_requests(status, expires_at);

CREATE INDEX idx_late_return_fees_status_created
    ON late_return_fees(status, created_at DESC);

CREATE UNIQUE INDEX uq_late_return_fee_pending_booking
    ON late_return_fees(booking_id)
    WHERE status = 'PENDING';
