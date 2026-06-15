CREATE TABLE protection_plans (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    price_type VARCHAR(20) NOT NULL,
    price_amount NUMERIC(12,2) NOT NULL,
    deductible_amount NUMERIC(12,2) NOT NULL,
    max_coverage_amount NUMERIC(12,2) NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE booking_protection_snapshots (
    booking_id UUID PRIMARY KEY REFERENCES bookings(id),
    protection_plan_id UUID NULL REFERENCES protection_plans(id),
    plan_code VARCHAR(40) NOT NULL,
    plan_name VARCHAR(120) NOT NULL,
    plan_fee NUMERIC(12,2) NOT NULL,
    deductible_amount NUMERIC(12,2) NOT NULL,
    max_coverage_amount NUMERIC(12,2) NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO protection_plans (
    id, code, name, description, price_type, price_amount,
    deductible_amount, max_coverage_amount, active, created_at, updated_at
) VALUES
    ('10000000-0000-4000-8000-000000000001', 'BASIC', 'Basic', 'Included baseline protection with standard liability.', 'PER_TRIP', 0.00, 5000000.00, NULL, true, now(), now()),
    ('10000000-0000-4000-8000-000000000002', 'STANDARD', 'Standard', 'Lower deductible protection for common rental incidents.', 'PER_DAY', 75000.00, 2000000.00, 15000000.00, true, now(), now()),
    ('10000000-0000-4000-8000-000000000003', 'PREMIUM', 'Premium', 'Lowest deductible protection with higher coverage ceiling.', 'PER_DAY', 150000.00, 500000.00, 30000000.00, true, now(), now());

CREATE INDEX idx_protection_plans_active_code
    ON protection_plans(active, code);
