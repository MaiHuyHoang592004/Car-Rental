CREATE TABLE host_customer_reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    host_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    content TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE host_customer_reviews
    ADD CONSTRAINT chk_host_customer_reviews_rating
    CHECK (rating BETWEEN 1 AND 5);

CREATE INDEX idx_host_customer_reviews_customer_created
    ON host_customer_reviews(customer_id, created_at DESC);

CREATE INDEX idx_host_customer_reviews_host_created
    ON host_customer_reviews(host_id, created_at DESC);
