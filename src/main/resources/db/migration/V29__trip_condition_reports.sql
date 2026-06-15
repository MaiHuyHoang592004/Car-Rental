CREATE TABLE trip_condition_reports (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    trip_record_id UUID REFERENCES trip_records(id),
    reporter_user_id UUID NOT NULL REFERENCES auth_users(id),
    reporter_role VARCHAR(20) NOT NULL CHECK (reporter_role IN ('CUSTOMER', 'HOST')),
    report_type VARCHAR(20) NOT NULL CHECK (report_type IN ('CHECK_IN', 'CHECK_OUT')),
    odometer INT NOT NULL CHECK (odometer >= 0),
    fuel_level INT NOT NULL CHECK (fuel_level >= 0 AND fuel_level <= 100),
    exterior_cleanliness VARCHAR(30),
    interior_cleanliness VARCHAR(30),
    has_visible_damage BOOLEAN NOT NULL DEFAULT FALSE,
    note TEXT,
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trip_condition_report_actor_type UNIQUE (booking_id, report_type, reporter_user_id)
);

CREATE INDEX idx_trip_condition_reports_booking ON trip_condition_reports(booking_id, submitted_at);
CREATE INDEX idx_trip_condition_reports_trip_record ON trip_condition_reports(trip_record_id);

CREATE TABLE trip_condition_photos (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES trip_condition_reports(id) ON DELETE CASCADE,
    file_id UUID NOT NULL UNIQUE REFERENCES files(id),
    angle VARCHAR(30) NOT NULL CHECK (angle IN (
        'FRONT',
        'REAR',
        'LEFT',
        'RIGHT',
        'INTERIOR_FRONT',
        'INTERIOR_REAR',
        'ODOMETER',
        'FUEL',
        'DAMAGE_CLOSEUP',
        'OTHER'
    )),
    display_order INT NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trip_condition_photos_report_order ON trip_condition_photos(report_id, display_order);

CREATE TABLE trip_damage_items (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES trip_condition_reports(id) ON DELETE CASCADE,
    location VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('MINOR', 'MODERATE', 'SEVERE')),
    description TEXT NOT NULL,
    photo_id UUID REFERENCES trip_condition_photos(id),
    pre_existing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trip_damage_items_report ON trip_damage_items(report_id);
