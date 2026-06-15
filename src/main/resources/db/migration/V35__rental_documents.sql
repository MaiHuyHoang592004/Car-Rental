CREATE TABLE rental_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    html_content TEXT NOT NULL,
    source_entity_type VARCHAR(80),
    source_entity_id UUID,
    generated_by UUID,
    generated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE rental_documents
    ADD CONSTRAINT chk_rental_documents_type
    CHECK (type IN ('RENTAL_AGREEMENT', 'PAYMENT_RECEIPT', 'REFUND_RECEIPT', 'DAMAGE_INVOICE'));

ALTER TABLE rental_documents
    ADD CONSTRAINT chk_rental_documents_status
    CHECK (status IN ('GENERATED', 'VOIDED'));

CREATE INDEX idx_rental_documents_booking_created
    ON rental_documents(booking_id, created_at DESC);

CREATE INDEX idx_rental_documents_type_created
    ON rental_documents(type, created_at DESC);

CREATE INDEX idx_rental_documents_source
    ON rental_documents(source_entity_type, source_entity_id);
