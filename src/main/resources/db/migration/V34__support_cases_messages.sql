CREATE TABLE support_cases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_id UUID NOT NULL,
    host_id UUID NOT NULL,
    opened_by_user_id UUID NOT NULL,
    category VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE support_cases
    ADD CONSTRAINT chk_support_cases_category
    CHECK (category IN ('BOOKING', 'PAYMENT', 'TRIP', 'DAMAGE', 'ACCOUNT', 'OTHER'));

ALTER TABLE support_cases
    ADD CONSTRAINT chk_support_cases_status
    CHECK (status IN ('OPEN', 'WAITING_ADMIN', 'WAITING_PARTICIPANT', 'CLOSED'));

CREATE INDEX idx_support_cases_booking_created
    ON support_cases(booking_id, created_at DESC);

CREATE INDEX idx_support_cases_customer_created
    ON support_cases(customer_id, created_at DESC);

CREATE INDEX idx_support_cases_host_created
    ON support_cases(host_id, created_at DESC);

CREATE INDEX idx_support_cases_status_created
    ON support_cases(status, created_at DESC);

CREATE TABLE support_case_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    support_case_id UUID NOT NULL REFERENCES support_cases(id) ON DELETE CASCADE,
    sender_user_id UUID,
    sender_type VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE support_case_messages
    ADD CONSTRAINT chk_support_case_messages_sender_type
    CHECK (sender_type IN ('CUSTOMER', 'HOST', 'ADMIN', 'SYSTEM'));

CREATE INDEX idx_support_case_messages_case_created
    ON support_case_messages(support_case_id, created_at ASC);
