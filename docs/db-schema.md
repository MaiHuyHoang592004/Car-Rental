# Database Schema — RentFlow

> Extract from SRS sections 21-22. Exact SQL is maintained in Flyway migrations.

## Tables

### auth_users

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| email | VARCHAR(120) UNIQUE NOT NULL |
| password_hash | VARCHAR(255) NOT NULL |
| status | VARCHAR(20) NOT NULL (ACTIVE, SUSPENDED, DELETED) |
| email_verified | BOOLEAN NOT NULL DEFAULT false |
| last_login_at | TIMESTAMPTZ NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### user_roles

| Column | Type / Constraint |
|---|---|
| user_id | UUID FK auth_users(id) |
| role | VARCHAR(20) NOT NULL (CUSTOMER, HOST, ADMIN) |
| created_at | TIMESTAMPTZ NOT NULL |
| PK | (user_id, role) |

### refresh_tokens

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| user_id | UUID FK auth_users(id) |
| token_hash | VARCHAR(255) UNIQUE NOT NULL |
| expires_at | TIMESTAMPTZ NOT NULL |
| revoked_at | TIMESTAMPTZ NULL |
| replaced_by_token_id | UUID NULL |
| created_at | TIMESTAMPTZ NOT NULL |

### user_profiles

| Column | Type / Constraint |
|---|---|
| user_id | UUID PK/FK auth_users(id) |
| full_name | VARCHAR(120) NOT NULL |
| phone | VARCHAR(30) NULL |
| date_of_birth | DATE NULL |
| address_line | TEXT NULL |
| driver_verification_status | VARCHAR(20) NOT NULL (NOT_SUBMITTED, PENDING, APPROVED, REJECTED, EXPIRED) |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### driver_verifications

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| customer_id | UUID FK auth_users(id) |
| license_number_encrypted | TEXT NOT NULL |
| license_number_hash | VARCHAR(128) NOT NULL |
| license_expiry_date | DATE NOT NULL |
| document_file_id | UUID FK files(id) NULL |
| status | VARCHAR(20) NOT NULL (PENDING, APPROVED, REJECTED, EXPIRED) |
| reviewed_by | UUID FK auth_users(id) NULL |
| review_reason | TEXT NULL |
| reviewed_at | TIMESTAMPTZ NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### vehicles

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| host_id | UUID FK auth_users(id) |
| category | VARCHAR(30) NOT NULL (SEDAN, SUV, HATCHBACK, MPV, PICKUP, LUXURY, VAN) |
| make | VARCHAR(60) NOT NULL |
| model | VARCHAR(60) NOT NULL |
| year | INT NOT NULL |
| plate_number_encrypted | TEXT NOT NULL |
| plate_number_hash | VARCHAR(128) UNIQUE NOT NULL |
| vin_encrypted | TEXT NULL |
| vin_hash | VARCHAR(128) UNIQUE NULL |
| transmission | VARCHAR(20) NOT NULL (AUTO, MANUAL) |
| fuel_type | VARCHAR(20) NOT NULL (GASOLINE, DIESEL, EV, HYBRID) |
| seats | INT NOT NULL |
| status | VARCHAR(20) NOT NULL (DRAFT, ACTIVE, MAINTENANCE, SUSPENDED, ARCHIVED) |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### listings

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| vehicle_id | UUID FK vehicles(id) |
| host_id | UUID FK auth_users(id) |
| title | VARCHAR(160) NOT NULL |
| description | TEXT NULL |
| city | VARCHAR(80) NOT NULL |
| address | TEXT NULL |
| latitude | NUMERIC(9,6) NULL |
| longitude | NUMERIC(9,6) NULL |
| base_price_per_day | NUMERIC(12,2) NOT NULL |
| currency | VARCHAR(3) NOT NULL DEFAULT 'VND' |
| daily_km_limit | INT NULL |
| instant_book | BOOLEAN NOT NULL DEFAULT true |
| cancellation_policy | VARCHAR(30) NOT NULL (FLEXIBLE, MODERATE, STRICT) |
| status | VARCHAR(30) NOT NULL (DRAFT, PENDING_APPROVAL, ACTIVE, SUSPENDED, ARCHIVED) |
| version | INT NOT NULL DEFAULT 0 |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### availability_calendar

| Column | Type / Constraint |
|---|---|
| listing_id | UUID FK listings(id) |
| available_date | DATE NOT NULL |
| status | VARCHAR(20) NOT NULL (FREE, HOLD, BOOKED, BLOCKED) |
| hold_token | UUID NULL |
| hold_expires_at | TIMESTAMPTZ NULL |
| booking_id | UUID FK bookings(id) NULL |
| version | INT NOT NULL DEFAULT 0 |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |
| PK | (listing_id, available_date) |

### extras

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| listing_id | UUID FK listings(id) |
| name | VARCHAR(80) NOT NULL |
| pricing_type | VARCHAR(20) NOT NULL (PER_DAY, PER_TRIP) |
| price | NUMERIC(12,2) NOT NULL |
| active | BOOLEAN NOT NULL DEFAULT true |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### bookings

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| customer_id | UUID FK auth_users(id) |
| host_id | UUID FK auth_users(id) |
| listing_id | UUID FK listings(id) |
| pickup_date | DATE NOT NULL |
| return_date | DATE NOT NULL |
| status | VARCHAR(30) NOT NULL |
| hold_token | UUID NULL |
| hold_expires_at | TIMESTAMPTZ NULL |
| host_approval_expires_at | TIMESTAMPTZ NULL |
| pickup_location | TEXT NULL |
| return_location | TEXT NULL |
| price_snapshot | JSONB NOT NULL |
| policy_snapshot | JSONB NOT NULL |
| cancellation_reason | VARCHAR(500) NULL |
| version | INT NOT NULL DEFAULT 0 |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### booking_extras

| Column | Type / Constraint |
|---|---|
| booking_id | UUID FK bookings(id) |
| extra_id | UUID FK extras(id) |
| quantity | INT NOT NULL |
| price_snapshot | NUMERIC(12,2) NOT NULL |
| PK | (booking_id, extra_id) |

### idempotency_keys

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| user_id | UUID FK auth_users(id) |
| scope | VARCHAR(80) NOT NULL |
| key | VARCHAR(120) NOT NULL |
| request_hash | VARCHAR(128) NOT NULL |
| status | VARCHAR(20) NOT NULL (PROCESSING, COMPLETED, FAILED) |
| response_status | INT NULL |
| response_body | JSONB NULL |
| locked_until | TIMESTAMPTZ NULL |
| expires_at | TIMESTAMPTZ NOT NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |
| UNIQUE | (user_id, scope, key) |

### booking_payments

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| booking_id | UUID FK bookings(id) UNIQUE |
| status | VARCHAR(30) NOT NULL (UNPAID, AUTHORIZED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED, VOIDED, FAILED) |
| authorized_amount | NUMERIC(12,2) NOT NULL DEFAULT 0 |
| captured_amount | NUMERIC(12,2) NOT NULL DEFAULT 0 |
| refunded_amount | NUMERIC(12,2) NOT NULL DEFAULT 0 |
| currency | VARCHAR(3) NOT NULL DEFAULT 'VND' |
| version | INT NOT NULL DEFAULT 0 |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### payment_transactions

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| booking_payment_id | UUID FK booking_payments(id) |
| booking_id | UUID FK bookings(id) |
| type | VARCHAR(20) NOT NULL (AUTHORIZE, CAPTURE, VOID, REFUND) |
| status | VARCHAR(20) NOT NULL (PENDING, SUCCEEDED, FAILED) |
| amount | NUMERIC(12,2) NOT NULL |
| currency | VARCHAR(3) NOT NULL |
| provider | VARCHAR(40) NOT NULL |
| provider_ref | VARCHAR(120) NULL |
| idempotency_key_id | UUID FK idempotency_keys(id) NULL |
| error_code | VARCHAR(80) NULL |
| error_message | TEXT NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### files

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| owner_id | UUID FK auth_users(id) |
| bucket | VARCHAR(80) NOT NULL |
| object_key | VARCHAR(255) NOT NULL |
| original_name | VARCHAR(255) NOT NULL |
| content_type | VARCHAR(100) NOT NULL |
| size_bytes | BIGINT NOT NULL |
| file_purpose | VARCHAR(40) NOT NULL (LISTING_PHOTO, LICENSE, TRIP_PHOTO, DOCUMENT) |
| checksum | VARCHAR(128) NULL |
| storage_status | VARCHAR(20) NOT NULL (PENDING, UPLOADED, DELETED) |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |
| deleted_at | TIMESTAMPTZ NULL |

### listing_photos

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| listing_id | UUID FK listings(id) |
| file_id | UUID FK files(id) |
| sort_order | INT NOT NULL |
| is_cover | BOOLEAN NOT NULL DEFAULT false |
| created_at | TIMESTAMPTZ NOT NULL |

### trip_condition_reports

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| booking_id | UUID FK bookings(id) NOT NULL |
| trip_record_id | UUID FK trip_records(id) NULL |
| reporter_user_id | UUID FK auth_users(id) NOT NULL |
| reporter_role | VARCHAR(20) NOT NULL (CUSTOMER, HOST) |
| report_type | VARCHAR(20) NOT NULL (CHECK_IN, CHECK_OUT) |
| odometer | INT NOT NULL CHECK >= 0 |
| fuel_level | INT NOT NULL CHECK 0..100 |
| exterior_cleanliness | VARCHAR(30) NULL |
| interior_cleanliness | VARCHAR(30) NULL |
| has_visible_damage | BOOLEAN NOT NULL DEFAULT false |
| note | TEXT NULL |
| latitude | NUMERIC(9,6) NULL |
| longitude | NUMERIC(9,6) NULL |
| submitted_at | TIMESTAMPTZ NOT NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |
| version | BIGINT NOT NULL DEFAULT 0 |
| UNIQUE | (booking_id, report_type, reporter_user_id) |

### trip_condition_photos

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| report_id | UUID FK trip_condition_reports(id) NOT NULL |
| file_id | UUID FK files(id) NOT NULL UNIQUE |
| angle | VARCHAR(30) NOT NULL (FRONT, REAR, LEFT, RIGHT, INTERIOR_FRONT, INTERIOR_REAR, ODOMETER, FUEL, DAMAGE_CLOSEUP, OTHER) |
| display_order | INT NOT NULL DEFAULT 0 |
| note | TEXT NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |
| version | BIGINT NOT NULL DEFAULT 0 |

### trip_damage_items

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| report_id | UUID FK trip_condition_reports(id) NOT NULL |
| location | VARCHAR(80) NOT NULL |
| severity | VARCHAR(20) NOT NULL (MINOR, MODERATE, SEVERE) |
| description | TEXT NOT NULL |
| photo_id | UUID FK trip_condition_photos(id) NULL |
| pre_existing | BOOLEAN NOT NULL DEFAULT false |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |
| version | BIGINT NOT NULL DEFAULT 0 |

### notifications

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| user_id | UUID FK auth_users(id) |
| type | VARCHAR(40) NOT NULL |
| title | VARCHAR(160) NOT NULL |
| message | TEXT NOT NULL |
| read_at | TIMESTAMPTZ NULL |
| delivery_status | VARCHAR(20) NOT NULL (PENDING, SENT, FAILED) |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### audit_logs

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| actor_id | UUID FK auth_users(id) NULL |
| action | VARCHAR(60) NOT NULL |
| target_type | VARCHAR(60) NOT NULL |
| target_id | UUID NULL |
| before_value | JSONB NULL |
| after_value | JSONB NULL |
| reason | TEXT NULL |
| ip_address | VARCHAR(60) NULL |
| correlation_id | VARCHAR(80) NULL |
| created_at | TIMESTAMPTZ NOT NULL |

### outbox_events

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| aggregate_type | VARCHAR(60) NOT NULL |
| aggregate_id | UUID NOT NULL |
| event_type | VARCHAR(80) NOT NULL |
| payload | JSONB NOT NULL |
| status | VARCHAR(20) NOT NULL (NEW, PUBLISHED, FAILED) |
| retry_count | INT NOT NULL DEFAULT 0 |
| next_retry_at | TIMESTAMPTZ NULL |
| processed_at | TIMESTAMPTZ NULL |
| created_at | TIMESTAMPTZ NOT NULL |
| updated_at | TIMESTAMPTZ NOT NULL |

### booking_timeline

| Column | Type / Constraint |
|---|---|
| id | UUID PK |
| booking_id | UUID FK bookings(id) |
| actor_id | UUID FK auth_users(id) NULL |
| actor_type | VARCHAR(20) NOT NULL (USER, SYSTEM) |
| event_type | VARCHAR(60) NOT NULL |
| message | TEXT NOT NULL |
| metadata | JSONB NULL |
| created_at | TIMESTAMPTZ NOT NULL |

---

## Key Constraints

```sql
-- One ACTIVE listing per vehicle
CREATE UNIQUE INDEX uq_listings_one_active_per_vehicle
ON listings(vehicle_id) WHERE status = 'ACTIVE';

-- One PENDING/APPROVED driver verification per customer
CREATE UNIQUE INDEX uq_driver_verification_active
ON driver_verifications(customer_id)
WHERE status IN ('PENDING', 'APPROVED');

-- Idempotency key uniqueness
ALTER TABLE idempotency_keys
ADD CONSTRAINT uq_idempotency_scope_key UNIQUE (user_id, scope, key);

-- One cover photo per listing
CREATE UNIQUE INDEX uq_listing_photos_cover
ON listing_photos(listing_id) WHERE is_cover = true;

-- Booking date range
ALTER TABLE bookings
ADD CONSTRAINT chk_bookings_date_range CHECK (pickup_date < return_date);

-- Vehicle seats positive
ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_seats CHECK (seats > 0);

-- Vehicle year
ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_year CHECK (year >= 1990);

-- Payment amounts
ALTER TABLE booking_payments
ADD CONSTRAINT chk_booking_payment_amounts CHECK (
  authorized_amount >= 0
  AND captured_amount >= 0
  AND refunded_amount >= 0
  AND captured_amount <= authorized_amount
  AND refunded_amount <= captured_amount
);
```

---

## Key Indexes

```sql
CREATE INDEX idx_auth_users_status_created ON auth_users(status, created_at DESC);
CREATE INDEX idx_vehicles_host_status ON vehicles(host_id, status);
CREATE INDEX idx_listings_status_city_price ON listings(status, city, base_price_per_day);
CREATE INDEX idx_listings_host_status ON listings(host_id, status);
CREATE INDEX idx_listings_vehicle_status ON listings(vehicle_id, status);
CREATE INDEX idx_availability_listing_date_status ON availability_calendar(listing_id, available_date, status);
CREATE INDEX idx_availability_hold_expiry ON availability_calendar(status, hold_expires_at);
CREATE INDEX idx_bookings_customer_period_status ON bookings(customer_id, pickup_date, return_date, status);
CREATE INDEX idx_bookings_listing_period_status ON bookings(listing_id, pickup_date, return_date, status);
CREATE INDEX idx_bookings_status_hold_expiry ON bookings(status, hold_expires_at);
CREATE INDEX idx_driver_verifications_expiry ON driver_verifications(status, license_expiry_date);
CREATE INDEX idx_idempotency_status_locked_until ON idempotency_keys(status, locked_until);
CREATE INDEX idx_booking_payments_booking_status ON booking_payments(booking_id, status);
CREATE INDEX idx_outbox_status_retry ON outbox_events(status, next_retry_at);
CREATE INDEX idx_booking_timeline_booking_created ON booking_timeline(booking_id, created_at);
```
