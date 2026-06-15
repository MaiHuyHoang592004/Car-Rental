# Phase 5 — Booking Core: Executable Implementation Spec

**Project:** RentFlow  
**Spec version:** 1.1 — execution-ready  
**Primary goal:** Implement backend Phase 5 booking core with idempotency, pessimistic availability locking, HELD booking holds, basic HELD cancellation, and expiry scheduler.  
**Migration rule:** Use the next available Flyway version. For the current repo, use `V7__bookings.sql` because `V6__vehicle_fuel_type_enum_values.sql` already exists.

---

## 0. Hard Guardrails

1. Keep Phase 5 backend-only.
2. Do not implement payment, payment tables, host approval, audit, timeline, outbox, notifications, files, trip lifecycle, reviews, disputes, reports, or frontend pages.
3. All booking date ranges are half-open: `[pickupDate, returnDate)`.
4. Availability locks must use `ORDER BY available_date ASC FOR UPDATE`.
5. Idempotency is required for `CREATE_BOOKING` and `CANCEL_BOOKING`.
6. The concurrent booking proof test is mandatory: 10 customers request the same listing/date range, exactly 1 succeeds and 9 return `409 LISTING_NOT_AVAILABLE`.
7. Scheduler `FOR UPDATE SKIP LOCKED` selection and state update must run in the same database transaction.
8. Unknown fields in `PATCH /api/v1/bookings/{id}` must be rejected, not silently ignored.

---

## 1. Current Codebase Assumptions

### 1.1 Existing reusable pieces

| Existing item | Package / area | Phase 5 usage |
|---|---|---|
| `BaseEntity` | `common` | Extend `Booking`, `IdempotencyKey` if it already owns `id`, `createdAt`, `updatedAt` |
| `SecurityContext` / `UserPrincipal` | `common.security` | Resolve current user id and roles |
| `GlobalExceptionHandler` | `common.exception` | Reuse existing error response format |
| `BusinessRuleException` | `common.exception` | Use for business conflicts such as unavailable dates, overlap, invalid status |
| `IdempotencyException` | `common.exception` | Use for idempotency errors if already present |
| `PageResponse<T>` | `common.web` | Response wrapper for `GET /bookings/me` |
| `AvailabilityCalendar` | `availability.entity` | Update rows to `HOLD` / `FREE` |
| `ListingRepository` | `listing.repository` | Load listing for validation; lock listing if existing method supports it |
| `ExtraRepository` | `listing.repository` | Load listing extras for price calculation |
| `UserProfileRepository` | `user.repository` | Driver verification gate when feature flag is enabled |
| `BaseIntegrationTest` | `test.integration` | Base class for booking integration tests |

### 1.2 Required repo-specific checks before coding

Before implementation, inspect the actual repo and confirm:

- `src/main/resources/db/migration` contains `V6__vehicle_fuel_type_enum_values.sql`; if yes, create `V7__bookings.sql`.
- `availability_calendar.booking_id` already exists as nullable UUID. If it exists as plain UUID, this spec keeps it plain UUID in Phase 5.
- Existing availability lock method uses `BETWEEN`; do not use it for booking because `BETWEEN` is inclusive and `returnDate` must be exclusive.
- Confirm whether `BaseEntity.version` already exists. If not, add `version` only to `Booking` as specified.
- Confirm fuel enum values in the actual DB migration. Do not patch `PETROL`/`GASOLINE` test helpers unless they are failing against the current DB constraint.

---

## 2. Phase 5 Scope

### 2.1 In scope

- `POST /api/v1/bookings` — create HELD booking with idempotency and pessimistic locking.
- `GET /api/v1/bookings/me` — current customer booking list, paginated, optional status filter.
- `GET /api/v1/bookings/{id}` — booking detail with role-based access.
- `PATCH /api/v1/bookings/{id}` — update only `pickupLocation` and/or `returnLocation`.
- `POST /api/v1/bookings/{id}/cancel` — cancel HELD booking only.
- `Booking`, `BookingExtra`, `BookingStatus`.
- `IdempotencyKey`, `IdempotencyStatus`, `IdempotencyScope` or string scopes.
- Canonical JSON hashing with sorted object keys, UTF-8 SHA-256, null fields preserved.
- Price calculation: base + extras, with `PER_DAY` and `PER_TRIP` behavior.
- `priceSnapshot` and `policySnapshot` stored as JSONB.
- Expire HELD scheduler using bounded batch and `FOR UPDATE SKIP LOCKED`.
- Feature flag `rentflow.booking.require-driver-verification=false` by default.
- Vehicle archive precondition fix: replace placeholder active-booking check with `BookingRepository` query.
- Unit and integration tests, including critical concurrent booking test.

### 2.2 Out of scope

| Feature | Phase |
|---|---|
| Payment authorize/capture/void/refund | Phase 6 |
| `booking_payments`, `payment_transactions` | Phase 6 |
| Cancellation policy calculator | Phase 7 |
| Cancel CONFIRMED / PENDING_HOST_APPROVAL | Phase 7 |
| Audit logs | Phase 7 |
| Booking timeline | Phase 7 |
| Outbox events | Phase 7 |
| Driver verification submit/admin/expiry flow | Phase 8A |
| Host approval/rejection | Phase 8B |
| Notifications | Phase 8B |
| Rate limiting | Phase 8B |
| Files / MinIO / listing photos | Phase 9 |
| Trip lifecycle / reviews / disputes / reports | Phase 9 |
| Frontend screens | Separate frontend phase |

---

## 3. Files to Create

```text
src/main/java/com/rentflow/
├── booking/
│   ├── controller/
│   │   └── BookingController.java
│   ├── dto/
│   │   ├── CreateBookingRequest.java
│   │   ├── BookingExtraRequest.java
│   │   ├── BookingResponse.java
│   │   ├── BookingSummaryResponse.java
│   │   ├── UpdateBookingRequest.java
│   │   ├── CancelBookingRequest.java
│   │   └── CancelBookingResponse.java
│   ├── entity/
│   │   ├── Booking.java
│   │   ├── BookingExtra.java
│   │   ├── BookingExtraId.java
│   │   └── BookingStatus.java
│   ├── repository/
│   │   ├── BookingRepository.java
│   │   └── BookingExtraRepository.java
│   └── service/
│       ├── BookingService.java
│       ├── BookingPriceCalculator.java
│       ├── BookingCancelService.java
│       └── BookingEligibilityChecker.java
├── common/
│   └── idempotency/
│       ├── entity/
│       │   ├── IdempotencyKey.java
│       │   └── IdempotencyStatus.java
│       ├── repository/
│       │   └── IdempotencyKeyRepository.java
│       └── service/
│           ├── CanonicalJsonHasher.java
│           ├── IdempotencyService.java
│           ├── IdempotencyResolution.java
│           └── IdempotencyScope.java
└── scheduler/
    ├── ExpireHeldBookingsJob.java
    └── ExpireHeldBookingsProcessor.java
```

```text
src/main/resources/db/migration/
└── V7__bookings.sql
```

---

## 4. Files to Modify

| File | Required change |
|---|---|
| `SecurityConfig.java` | Add booking endpoints as authenticated |
| `AvailabilityCalendarRepository.java` | Add half-open range lock method for booking |
| `VehicleService.java` | Replace placeholder `hasActiveBookings()` with `BookingRepository.existsActiveBookingsForVehicle()` |
| `application.yml` | Add `rentflow.booking.*` and `rentflow.scheduler.expire-held-bookings.*` |
| `application-test.yml` | Disable driver verification gate; disable scheduler unless explicitly tested |
| `GlobalExceptionHandler.java` | Add missing handlers only if existing handler does not already cover codes correctly |
| `ObjectMapper` config or DTO annotations | Ensure PATCH rejects unknown fields |

---

## 5. Flyway Migration

**File:** `src/main/resources/db/migration/V7__bookings.sql`

If the next available version is not V7, rename this migration to the next available Flyway version and keep the content.

```sql
-- Phase 5: bookings, booking_extras, idempotency_keys

CREATE TABLE bookings (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id                 UUID NOT NULL REFERENCES auth_users(id),
    host_id                     UUID NOT NULL REFERENCES auth_users(id),
    listing_id                  UUID NOT NULL REFERENCES listings(id),
    pickup_date                 DATE NOT NULL,
    return_date                 DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'HELD',
    hold_token                  UUID,
    hold_expires_at             TIMESTAMPTZ,
    host_approval_expires_at    TIMESTAMPTZ,
    pickup_location             TEXT,
    return_location             TEXT,
    price_snapshot              JSONB NOT NULL,
    policy_snapshot             JSONB NOT NULL,
    cancellation_reason         VARCHAR(500),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE bookings
ADD CONSTRAINT chk_bookings_status
CHECK (status IN (
    'HELD',
    'PENDING_HOST_APPROVAL',
    'CONFIRMED',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED',
    'REJECTED',
    'EXPIRED'
));

ALTER TABLE bookings
ADD CONSTRAINT chk_bookings_date_range
CHECK (pickup_date < return_date);

CREATE TABLE booking_extras (
    booking_id      UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    extra_id        UUID NOT NULL REFERENCES extras(id),
    quantity        INTEGER NOT NULL,
    price_snapshot  NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (booking_id, extra_id)
);

ALTER TABLE booking_extras
ADD CONSTRAINT chk_booking_extras_quantity CHECK (quantity > 0);

CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth_users(id),
    scope           VARCHAR(80) NOT NULL,
    key             VARCHAR(120) NOT NULL,
    request_hash    VARCHAR(128) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    response_status INTEGER,
    response_body   JSONB,
    locked_until    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE idempotency_keys
ADD CONSTRAINT chk_idempotency_status
CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'));

ALTER TABLE idempotency_keys
ADD CONSTRAINT chk_idempotency_scope
CHECK (scope IN ('CREATE_BOOKING', 'CANCEL_BOOKING'));

ALTER TABLE idempotency_keys
ADD CONSTRAINT uq_idempotency_scope_key
UNIQUE (user_id, scope, key);

CREATE INDEX idx_bookings_customer_period_status
    ON bookings(customer_id, pickup_date, return_date, status);

CREATE INDEX idx_bookings_listing_period_status
    ON bookings(listing_id, pickup_date, return_date, status);

CREATE INDEX idx_bookings_status_hold_expiry
    ON bookings(status, hold_expires_at)
    WHERE status = 'HELD';

CREATE INDEX idx_bookings_vehicle_archive_lookup
    ON bookings(listing_id, status);

CREATE INDEX idx_idempotency_status_locked_until
    ON idempotency_keys(status, locked_until);

CREATE INDEX idx_idempotency_user_scope
    ON idempotency_keys(user_id, scope);
```

### 5.1 Availability FK decision

If `availability_calendar.booking_id` already exists as nullable UUID without FK, keep it that way in Phase 5. Do not rewrite earlier migrations.

Optional later hardening migration may add:

```sql
ALTER TABLE availability_calendar
ADD CONSTRAINT fk_availability_booking
FOREIGN KEY (booking_id) REFERENCES bookings(id);
```

Only add this if existing data is clean and the team wants strict FK enforcement.

---

## 6. Entity Design

### 6.1 `BookingStatus`

```java
public enum BookingStatus {
    HELD,
    PENDING_HOST_APPROVAL,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    REJECTED,
    EXPIRED
}
```

Active statuses for customer overlap detection:

```java
List.of(HELD, PENDING_HOST_APPROVAL, CONFIRMED)
```

Phase 5 only creates `HELD`, `CANCELLED`, and `EXPIRED`. `PENDING_HOST_APPROVAL` and `CONFIRMED` are included for forward compatibility with Phase 6/8B.

### 6.2 `Booking`

Use plain UUID fields instead of `@ManyToOne` relations.

| Field | Java type | Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | From `BaseEntity` or explicit |
| `customerId` | `UUID` | `customer_id` | Required |
| `hostId` | `UUID` | `host_id` | Denormalized from listing |
| `listingId` | `UUID` | `listing_id` | Required |
| `pickupDate` | `LocalDate` | `pickup_date` | Required |
| `returnDate` | `LocalDate` | `return_date` | Exclusive |
| `status` | `BookingStatus` | `status` | Enum string |
| `holdToken` | `UUID` | `hold_token` | Generated on create |
| `holdExpiresAt` | `Instant` | `hold_expires_at` | `now + 15 min` default |
| `hostApprovalExpiresAt` | `Instant` | `host_approval_expires_at` | Null in Phase 5 |
| `pickupLocation` | `String` | `pickup_location` | Optional |
| `returnLocation` | `String` | `return_location` | Optional |
| `priceSnapshot` | `String` or `JsonNode` | `price_snapshot` | JSONB |
| `policySnapshot` | `String` or `JsonNode` | `policy_snapshot` | JSONB |
| `cancellationReason` | `String` | `cancellation_reason` | Max 500 |
| `version` | `Long` | `version` | `@Version` |

### 6.3 JSONB mapping requirement

Do not rely on a pass-through `AttributeConverter<String, String>` alone unless the existing project has proven PostgreSQL JSONB support for it.

Preferred mapping for Spring Boot 3 / Hibernate 6:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "price_snapshot", columnDefinition = "jsonb", nullable = false)
private String priceSnapshot;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "policy_snapshot", columnDefinition = "jsonb", nullable = false)
private String policySnapshot;
```

If this does not work in the current project, use `JsonNode`/`Map<String,Object>` with `@JdbcTypeCode(SqlTypes.JSON)`, or a PostgreSQL `PGobject` converter. Verify with an integration test that inserted snapshots are valid JSONB.

### 6.4 `BookingExtra`

Use `@IdClass(BookingExtraId.class)`.

| Field | Java type | Column |
|---|---|---|
| `bookingId` | `UUID` | `booking_id` |
| `extraId` | `UUID` | `extra_id` |
| `quantity` | `int` | `quantity` |
| `priceSnapshot` | `BigDecimal` | `price_snapshot` |

Do not use lazy `@ManyToOne` relations in Phase 5.

### 6.5 `IdempotencyKey`

| Field | Java type | Column |
|---|---|---|
| `id` | `UUID` | `id` |
| `userId` | `UUID` | `user_id` |
| `scope` | `String` or enum | `scope` |
| `key` | `String` | `key` |
| `requestHash` | `String` | `request_hash` |
| `status` | `IdempotencyStatus` | `status` |
| `responseStatus` | `Integer` | `response_status` |
| `responseBody` | `String` or `JsonNode` | `response_body` |
| `lockedUntil` | `Instant` | `locked_until` |
| `expiresAt` | `Instant` | `expires_at` |

```java
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
```

---

## 7. Repository Design

### 7.1 `AvailabilityCalendarRepository`

Add a booking-specific half-open lock method. Do not use an existing `BETWEEN` method for booking.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT ac FROM AvailabilityCalendar ac
    WHERE ac.listingId = :listingId
      AND ac.availableDate >= :pickupDate
      AND ac.availableDate < :returnDate
    ORDER BY ac.availableDate ASC
    """)
List<AvailabilityCalendar> findForBookingRangeForUpdate(
    @Param("listingId") UUID listingId,
    @Param("pickupDate") LocalDate pickupDate,
    @Param("returnDate") LocalDate returnDate
);
```

Optional non-locking read:

```java
@Query("""
    SELECT ac FROM AvailabilityCalendar ac
    WHERE ac.listingId = :listingId
      AND ac.availableDate >= :pickupDate
      AND ac.availableDate < :returnDate
    ORDER BY ac.availableDate ASC
    """)
List<AvailabilityCalendar> findForBookingRange(
    UUID listingId,
    LocalDate pickupDate,
    LocalDate returnDate
);
```

### 7.2 `BookingRepository`

```java
@Query("""
    SELECT COUNT(b) > 0 FROM Booking b
    WHERE b.customerId = :customerId
      AND b.status IN :activeStatuses
      AND b.pickupDate < :returnDate
      AND b.returnDate > :pickupDate
    """)
boolean existsOverlappingActiveBooking(
    UUID customerId,
    LocalDate pickupDate,
    LocalDate returnDate,
    List<BookingStatus> activeStatuses
);
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM Booking b WHERE b.id = :id")
Optional<Booking> findByIdForUpdate(UUID id);
```

```java
@Query(value = """
    SELECT *
    FROM bookings
    WHERE status = 'HELD'
      AND hold_expires_at < :now
    ORDER BY id
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<Booking> findExpiredHeldBookingsForUpdate(
    @Param("now") Instant now,
    @Param("batchSize") int batchSize
);
```

```java
@Query("""
    SELECT COUNT(b) > 0 FROM Booking b
    JOIN Listing l ON l.id = b.listingId
    WHERE l.vehicleId = :vehicleId
      AND b.status IN :activeStatuses
    """)
boolean existsActiveBookingsForVehicle(
    UUID vehicleId,
    List<BookingStatus> activeStatuses
);
```

For list endpoints:

```java
Page<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
Page<Booking> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, BookingStatus status, Pageable pageable);
```

### 7.3 `IdempotencyKeyRepository`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT ik FROM IdempotencyKey ik
    WHERE ik.userId = :userId
      AND ik.scope = :scope
      AND ik.key = :key
    """)
Optional<IdempotencyKey> findByUserIdAndScopeAndKeyForUpdate(
    UUID userId,
    String scope,
    String key
);
```

Also provide standard `save()` and optional non-locking finder if needed.

---

## 8. Idempotency Design

### 8.1 Header validation

Header: `Idempotency-Key`

Required for:

- `POST /api/v1/bookings`
- `POST /api/v1/bookings/{id}/cancel`

UUID-v4 regex:

```text
^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$
```

Errors:

| Condition | HTTP | Code |
|---|---:|---|
| Missing header | 400 | `IDEMPOTENCY_KEY_REQUIRED` |
| Invalid UUID-v4 format | 400 | `VALIDATION_ERROR` |

Validation should happen before service execution, but idempotency resolution must be inside the business transaction.

### 8.2 Canonical JSON hashing

Algorithm:

1. Convert DTO to Jackson tree or `Map`.
2. Include null fields.
3. Sort object keys recursively.
4. Preserve array order.
5. Serialize to compact JSON.
6. Compute SHA-256 over UTF-8 bytes.
7. Return lowercase hex string.

Rules:

- Nulls are included.
- Dates are ISO-8601 strings.
- UUIDs are lowercase strings.
- Arrays are not sorted.
- Numeric serialization must be deterministic for DTO values.

### 8.3 Resolution behavior

| Existing record | Same hash | Different hash |
|---|---|---|
| No record | Insert PROCESSING and proceed | N/A |
| PROCESSING + `lockedUntil > now` | 409 `REQUEST_ALREADY_PROCESSING` | 409 `REQUEST_ALREADY_PROCESSING` |
| PROCESSING + `lockedUntil <= now` | Update lock and retry | Update lock and retry |
| COMPLETED | Return stored response | 409 `IDEMPOTENCY_KEY_CONFLICT` |
| FAILED + `lockedUntil <= now` | Update lock and retry | Update lock and retry |
| FAILED + `lockedUntil > now` | 409 `REQUEST_ALREADY_PROCESSING` | 409 `REQUEST_ALREADY_PROCESSING` |

### 8.4 Insert race handling — mandatory

A `SELECT ... FOR UPDATE` does not lock a missing row. Implement `resolve()` to avoid concurrent same-key insert races.

Required approach:

```text
resolve(userId, scope, key, requestHash):
  1. Try INSERT new PROCESSING row with locked_until = now + 30s, expires_at = now + 5d.
  2. If insert succeeds: return PROCEED with new idempotency row id.
  3. If insert fails due to unique constraint:
     a. SELECT existing row FOR UPDATE.
     b. Apply the resolution behavior table.
```

Equivalent native PostgreSQL `INSERT ... ON CONFLICT` strategy is acceptable if it still locks/serializes behavior correctly.

### 8.5 Resolution object

Do not make `resolve()` return only `IdempotencyKey`. It must communicate whether to proceed or replay.

```java
public sealed interface IdempotencyResolution {
    record Proceed(UUID idempotencyKeyId) implements IdempotencyResolution {}
    record Replay(int responseStatus, String responseBodyJson) implements IdempotencyResolution {}
}
```

### 8.6 Completion and failure

```java
void complete(UUID id, int responseStatus, String responseBodyJson);
void fail(UUID id);
```

`complete()` sets:

- `status = COMPLETED`
- `responseStatus`
- `responseBody`
- `lockedUntil = null`

`fail()` sets:

- `status = FAILED`
- `lockedUntil = now + 30s` or leave current lock until expiry, depending on existing style

### 8.7 Transaction rollback note

If a business exception rolls back the transaction that inserted/updated the idempotency row, the idempotency row changes also roll back. This is acceptable. Retries are allowed.

If an existing PROCESSING row remains from a crashed request, retry is allowed after `lockedUntil` expires.

---

## 9. Create Booking Transaction — TX-01

`BookingService.createBooking()` must be annotated `@Transactional`.

### 9.1 Required order

```text
1. Controller validates Idempotency-Key exists and is UUID-v4.
2. Service computes canonical request hash.
3. Service resolves idempotency with scope CREATE_BOOKING.
   - Replay completed response immediately if resolution is Replay.
   - Continue only if resolution is Proceed.
4. Validate caller has CUSTOMER role.
5. Load user profile.
6. If require-driver-verification=true and profile.driverVerificationStatus != APPROVED:
   throw 403 DRIVER_LICENSE_NOT_APPROVED.
7. Validate listing exists and listing.status = ACTIVE.
8. Validate vehicle exists and vehicle.status = ACTIVE.
9. Validate self-booking: listing.hostId must not equal customerId.
10. Validate dates:
    - pickupDate and returnDate required.
    - returnDate > pickupDate.
    - rentalDays = days between pickupDate and returnDate.
    - rentalDays between 1 and 30 inclusive.
    - pickupDate must not be in the past.
11. Validate no customer overlap with active statuses:
    HELD, PENDING_HOST_APPROVAL, CONFIRMED.
12. Lock availability rows for [pickupDate, returnDate), ordered by available_date ASC.
13. Validate locked rows count equals rentalDays.
14. Validate every locked row status is FREE.
15. Load and validate requested extras:
    - extra belongs to listing.
    - extra.active = true.
    - quantity >= 1.
16. Calculate price snapshot and policy snapshot.
17. Generate holdToken UUID.
18. Insert Booking:
    - status = HELD.
    - holdExpiresAt = now + configured hold duration.
    - hostApprovalExpiresAt = null.
    - priceSnapshot / policySnapshot populated.
19. Insert BookingExtra rows if any.
20. Update locked availability rows:
    - status = HOLD.
    - bookingId = booking.id.
    - holdToken = booking.holdToken.
    - holdExpiresAt = booking.holdExpiresAt.
21. Build BookingResponse.
22. Save idempotency response as COMPLETED with status 201 and response JSON.
23. Return response.
24. Transaction commits.
```

### 9.2 Error behavior

| Condition | HTTP | Code |
|---|---:|---|
| Missing/invalid JWT | 401 | existing auth code |
| Caller lacks CUSTOMER role | 403 | `ACCESS_DENIED` |
| Driver verification required but not approved | 403 | `DRIVER_LICENSE_NOT_APPROVED` |
| Self-booking | 403 | `ACCESS_DENIED` |
| Listing not found or not ACTIVE | 404 | `LISTING_NOT_FOUND` |
| Vehicle not found or not ACTIVE | 404 | `LISTING_NOT_FOUND` |
| Invalid dates | 400 | `VALIDATION_ERROR` |
| Overlap | 409 | `BOOKING_OVERLAP_CUSTOMER` |
| Availability rows missing or not all FREE | 409 | `LISTING_NOT_AVAILABLE` |
| Same idempotency key, different request body | 409 | `IDEMPOTENCY_KEY_CONFLICT` |
| Same idempotency key still processing | 409 | `REQUEST_ALREADY_PROCESSING` |

---

## 10. Date Rules

- Booking period is `[pickupDate, returnDate)`.
- `returnDate` is exclusive.
- Minimum rental duration: 1 day.
- Maximum rental duration: 30 days.
- Reject if `returnDate <= pickupDate`.
- Reject if `ChronoUnit.DAYS.between(pickupDate, returnDate) > 30`.
- Reject if `pickupDate < LocalDate.now(clock)`.

Example:

```text
pickupDate = 2026-06-01
returnDate = 2026-06-03
rentalDays = 2
occupied dates = 2026-06-01 and 2026-06-02
```

---

## 11. Price Calculation

### 11.1 Formula

```text
rentalDays = ChronoUnit.DAYS.between(pickupDate, returnDate)
baseAmount = listing.basePricePerDay * rentalDays

PER_DAY extra line amount = extra.price * quantity * rentalDays
PER_TRIP extra line amount = extra.price * quantity

extraAmount = sum(extra line amounts)
totalAmount = baseAmount + extraAmount
currency = listing.currency
```

### 11.2 Validation

- `extras = null` is treated as empty list.
- Unknown extra id: `400 VALIDATION_ERROR`.
- Extra not belonging to listing: `400 VALIDATION_ERROR`.
- Inactive extra: `400 VALIDATION_ERROR`.
- Quantity `< 1`: `400 VALIDATION_ERROR`.

### 11.3 `priceSnapshot` shape

```json
{
  "rentalDays": 2,
  "basePricePerDay": 700000,
  "baseAmount": 1400000,
  "extraAmount": 100000,
  "totalAmount": 1500000,
  "currency": "VND",
  "extras": [
    {
      "extraId": "00000000-0000-0000-0000-000000000001",
      "name": "GPS",
      "pricingType": "PER_DAY",
      "unitPrice": 50000,
      "quantity": 1,
      "lineAmount": 100000
    }
  ]
}
```

### 11.4 `policySnapshot` shape

```json
{
  "cancellationPolicy": "FLEXIBLE",
  "instantBook": true,
  "dailyKmLimit": 200
}
```

---

## 12. Basic HELD Cancellation

Phase 5 only supports HELD cancellation. Payment-aware cancellation is Phase 7.

### 12.1 Transaction

`BookingCancelService.cancelHeldBooking()` must be `@Transactional`.

```text
1. Controller validates Idempotency-Key exists and is UUID-v4.
2. Compute canonical hash of CancelBookingRequest plus booking id.
   - Include booking id in hash context so same body for different booking id is not treated ambiguously.
3. Resolve idempotency with scope CANCEL_BOOKING.
   - Replay completed response if applicable.
4. Load booking FOR UPDATE.
5. If not found: 404 BOOKING_NOT_FOUND.
6. Validate booking.customerId == currentUserId.
   - If not owner: 404 BOOKING_NOT_FOUND to avoid enumeration.
7. Validate booking.status == HELD.
   - If not HELD: 409 BOOKING_INVALID_STATUS.
8. Lock availability rows for [pickupDate, returnDate), ordered ASC.
9. Sanitize cancellation reason:
   - Strip HTML tags.
   - Trim.
   - Max 500 chars.
   - Blank becomes null.
10. Set booking.status = CANCELLED.
11. Set booking.cancellationReason.
12. Release only matching availability rows:
    - row.bookingId == booking.id
    - row.status == HOLD
    - row.holdToken == booking.holdToken when holdToken is present
13. For matching rows:
    - status = FREE
    - bookingId = null
    - holdToken = null
    - holdExpiresAt = null
14. Save response as idempotency COMPLETED.
15. Commit.
```

### 12.2 Response

```json
{
  "id": "booking-uuid",
  "status": "CANCELLED",
  "cancellationReason": "Change of plan"
}
```

---

## 13. Expire HELD Scheduler

### 13.1 Configuration

```yaml
rentflow:
  scheduler:
    expire-held-bookings:
      enabled: true
      batch-size: 100
      fixed-delay-ms: 60000
```

Inject a `Clock` bean. Do not use `Instant.now()` directly in services or scheduler logic.

### 13.2 Correct transaction structure

Do not select expired rows outside a transaction and then process them in a separate transaction. `FOR UPDATE SKIP LOCKED` must be in the same transaction as the state updates.

Recommended implementation:

```text
ExpireHeldBookingsJob.run()
  -> calls ExpireHeldBookingsProcessor.processBatch()

ExpireHeldBookingsProcessor.processBatch()
  @Transactional
  1. now = clock.instant()
  2. expired = bookingRepository.findExpiredHeldBookingsForUpdate(now, batchSize)
  3. for each booking in expired:
       process in same transaction:
       - re-check status == HELD
       - lock availability rows by date ASC
       - booking.status = EXPIRED
       - release matching HOLD rows
  4. commit batch
```

This keeps the `FOR UPDATE SKIP LOCKED` lock active until updates commit.

### 13.3 SQL

```sql
SELECT *
FROM bookings
WHERE status = 'HELD'
  AND hold_expires_at < :now
ORDER BY id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

### 13.4 Release rule

Only release rows that belong to the booking being expired:

```text
row.bookingId == booking.id
row.status == HOLD
row.holdToken == booking.holdToken when holdToken is present
```

### 13.5 Safety

- Running the job twice is safe.
- Multiple app instances are safe because of `FOR UPDATE SKIP LOCKED`.
- If any booking in the batch fails, either the whole batch rolls back or the implementation can use a smaller transaction boundary. For Phase 5, a batch transaction is acceptable if tests pass; per-booking transactions may be added later if needed.

---

## 14. API Contracts

### 14.1 `POST /api/v1/bookings`

Auth: required, CUSTOMER role.  
Headers: `Idempotency-Key: <uuid-v4>`.

Request:

```json
{
  "listingId": "00000000-0000-0000-0000-000000000001",
  "pickupDate": "2026-06-01",
  "returnDate": "2026-06-03",
  "pickupLocation": "Hanoi",
  "returnLocation": "Hanoi",
  "extras": [
    { "extraId": "00000000-0000-0000-0000-000000000002", "quantity": 1 }
  ]
}
```

Response `201`:

```json
{
  "id": "booking-uuid",
  "status": "HELD",
  "listingId": "listing-uuid",
  "listingTitle": "Toyota Vios 2022",
  "customerId": "customer-uuid",
  "hostId": "host-uuid",
  "pickupDate": "2026-06-01",
  "returnDate": "2026-06-03",
  "pickupLocation": "Hanoi",
  "returnLocation": "Hanoi",
  "holdExpiresAt": "2026-05-09T11:15:00Z",
  "totalAmount": 1500000,
  "currency": "VND",
  "priceSnapshot": {},
  "policySnapshot": {},
  "createdAt": "2026-05-09T11:00:00Z"
}
```

### 14.2 `GET /api/v1/bookings/me`

Auth: required, CUSTOMER role recommended. If other roles have CUSTOMER role too, return bookings for current user as customer.

Query params:

- `status` optional
- `page` default `0`
- `size` default `20`, max `100`

Response: `PageResponse<BookingSummaryResponse>`.

### 14.3 `GET /api/v1/bookings/{id}`

Auth: required.

Access:

- Customer can view own booking.
- Host can view booking for own listing.
- Admin can view any booking.
- Others receive `404 BOOKING_NOT_FOUND`.

### 14.4 `PATCH /api/v1/bookings/{id}`

Auth: required, booking owner customer only.

Allowed request fields only:

```json
{
  "pickupLocation": "New pickup location",
  "returnLocation": "New return location"
}
```

Rules:

- At least one allowed field must be present.
- Unknown fields such as `pickupDate`, `returnDate`, `status`, `listingId`, `priceSnapshot` must return `400 VALIDATION_ERROR`.
- Allowed booking statuses: `HELD`, `PENDING_HOST_APPROVAL`, `CONFIRMED`.
- Phase 5 can only create `HELD`, but forward-compatible statuses are allowed for later phases.

### 14.5 `POST /api/v1/bookings/{id}/cancel`

Auth: required, booking owner customer only.  
Headers: `Idempotency-Key: <uuid-v4>`.

Request:

```json
{
  "reason": "Change of plan"
}
```

Response `200`:

```json
{
  "id": "booking-uuid",
  "status": "CANCELLED",
  "cancellationReason": "Change of plan"
}
```

---

## 15. Security Rules

### 15.1 SecurityConfig

Add booking endpoints as authenticated before the catch-all rule:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/bookings").authenticated()
.requestMatchers(HttpMethod.GET, "/api/v1/bookings/me").authenticated()
.requestMatchers(HttpMethod.GET, "/api/v1/bookings/{id}").authenticated()
.requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/{id}").authenticated()
.requestMatchers(HttpMethod.POST, "/api/v1/bookings/{id}/cancel").authenticated()
```

Role and ownership checks belong in service layer.

### 15.2 Business authorization

| Operation | Allowed actor | Failure |
|---|---|---|
| Create booking | User with CUSTOMER role | 403 `ACCESS_DENIED` |
| Create own hosted listing booking | Not allowed | 403 `ACCESS_DENIED` |
| List my bookings | Current user as customer | Empty page or 403 depending existing role policy |
| View booking | Customer owner, host owner of listing, admin | 404 `BOOKING_NOT_FOUND` |
| Patch booking | Customer owner only | 404 `BOOKING_NOT_FOUND` |
| Cancel booking | Customer owner only | 404 `BOOKING_NOT_FOUND` |

---

## 16. Error Codes

Use existing error response shape.

| Code | HTTP | When |
|---|---:|---|
| `AUTH_INVALID_CREDENTIALS` or existing auth code | 401 | Missing/invalid JWT |
| `ACCESS_DENIED` | 403 | Missing role, self-booking |
| `DRIVER_LICENSE_NOT_APPROVED` | 403 | Driver gate enabled and not approved |
| `BOOKING_NOT_FOUND` | 404 | Not found or not authorized to know booking exists |
| `LISTING_NOT_FOUND` | 404 | Listing absent, non-ACTIVE, or vehicle non-ACTIVE |
| `VALIDATION_ERROR` | 400 | Invalid body, dates, unknown PATCH fields, invalid idempotency format |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Missing idempotency key |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | Same key, different body |
| `REQUEST_ALREADY_PROCESSING` | 409 | Same key still processing |
| `LISTING_NOT_AVAILABLE` | 409 | Availability rows missing or not FREE |
| `BOOKING_OVERLAP_CUSTOMER` | 409 | Customer already has active overlapping booking |
| `BOOKING_INVALID_STATUS` | 409 | Cancel/patch not allowed for current status |

If `BOOKING_NOT_FOUND` is not in the global error-code list, still use it for Phase 5 booking resource lookups.

---

## 17. Configuration

`application.yml`:

```yaml
rentflow:
  booking:
    require-driver-verification: ${REQUIRE_DRIVER_VERIFICATION:false}
    hold-duration-minutes: ${BOOKING_HOLD_DURATION_MINUTES:15}
  scheduler:
    expire-held-bookings:
      enabled: ${EXPIRE_HELD_BOOKINGS_ENABLED:true}
      batch-size: ${EXPIRE_HELD_BOOKINGS_BATCH_SIZE:100}
      fixed-delay-ms: ${EXPIRE_HELD_BOOKINGS_FIXED_DELAY_MS:60000}
```

`application-test.yml`:

```yaml
rentflow:
  booking:
    require-driver-verification: false
    hold-duration-minutes: 15
  scheduler:
    expire-held-bookings:
      enabled: false
```

Enable the scheduler explicitly only in scheduler integration tests.

---

## 18. Implementation Order

### Task 1 — Migration

Create `V7__bookings.sql`.

Acceptance:

- Fresh DB migration succeeds.
- `bookings`, `booking_extras`, `idempotency_keys` exist.
- Constraints and indexes exist.
- Existing integration tests still pass.

### Task 2 — Entities

Create:

- `Booking`
- `BookingExtra`
- `BookingExtraId`
- `BookingStatus`
- `IdempotencyKey`
- `IdempotencyStatus`

Acceptance:

- JPA validation passes.
- JSONB mapping verified with integration test or repository save/read test.

### Task 3 — Repositories

Create/modify:

- `BookingRepository`
- `BookingExtraRepository`
- `IdempotencyKeyRepository`
- `AvailabilityCalendarRepository.findForBookingRangeForUpdate`

Acceptance:

- Half-open date query verified.
- `FOR UPDATE SKIP LOCKED` native query compiles and runs.

### Task 4 — Canonical hasher and idempotency

Create:

- `CanonicalJsonHasher`
- `IdempotencyService`
- `IdempotencyResolution`

Acceptance:

- Hash stable across reordered object keys.
- Null fields included.
- Insert-race behavior implemented.
- Same key/body replays.
- Same key/different body conflicts.

### Task 5 — Price calculator

Create `BookingPriceCalculator`.

Acceptance:

- Base-only price passes.
- PER_DAY price passes.
- PER_TRIP price passes.
- Invalid extras fail.

### Task 6 — Create booking

Implement `BookingService.createBooking()` and `BookingController.POST /bookings`.

Acceptance:

- Happy path creates HELD booking.
- Availability becomes HOLD.
- Idempotency response saved.
- Invalid dates, self-booking, overlap, unavailable dates return correct errors.

### Task 7 — Read and patch endpoints

Implement:

- `GET /bookings/me`
- `GET /bookings/{id}`
- `PATCH /bookings/{id}`

Acceptance:

- Customer sees own booking.
- Host sees own listing booking.
- Unauthorized users get 404 for specific booking.
- PATCH rejects unknown fields.

### Task 8 — Cancel HELD

Implement `BookingCancelService.cancelHeldBooking()` and `POST /bookings/{id}/cancel`.

Acceptance:

- HELD → CANCELLED.
- Matching availability HOLD → FREE.
- Non-HELD cancellation returns `BOOKING_INVALID_STATUS`.
- Idempotent replay works.

### Task 9 — Expire HELD scheduler

Implement `ExpireHeldBookingsJob` and `ExpireHeldBookingsProcessor`.

Acceptance:

- Expired HELD → EXPIRED.
- Matching availability HOLD → FREE.
- `FOR UPDATE SKIP LOCKED` selection and updates happen in same transaction.
- Running twice is safe.

### Task 10 — Vehicle archive check

Modify `VehicleService.hasActiveBookings()` to query bookings.

Acceptance:

- Vehicle archive rejects if related listing has `HELD`, `CONFIRMED`, or `IN_PROGRESS` booking.
- Existing vehicle tests pass.

### Task 11 — Critical concurrent booking proof

Implement final concurrent test.

Acceptance:

- 10 concurrent customers.
- Same listing and same date range.
- Unique idempotency key per request.
- Exactly 1 HTTP 201.
- Exactly 9 HTTP 409 with `LISTING_NOT_AVAILABLE`.
- DB has exactly 1 HELD booking for the listing/date.
- Availability rows are HOLD and point to the winning booking.

---

## 19. Test Plan

### 19.1 Unit tests

`CanonicalJsonHasherTest`

- Same DTO => same hash.
- Reordered JSON keys => same hash.
- Null fields are included.
- Arrays preserve order.
- Different body => different hash.

`BookingPriceCalculatorTest`

- Base only.
- PER_DAY extra.
- PER_TRIP extra.
- Mixed extras.
- Quantity validation.
- Unknown/inactive/wrong-listing extra rejected.

`BookingDateValidationTest`

- `returnDate <= pickupDate` rejected.
- 1-day rental valid.
- 30-day rental valid.
- 31-day rental rejected.
- Past pickup date rejected.

`BookingOverlapDetectionTest`

- HELD overlap blocks.
- PENDING_HOST_APPROVAL overlap blocks.
- CONFIRMED overlap blocks.
- CANCELLED/REJECTED/EXPIRED do not block.
- Adjacent ranges do not overlap.

### 19.2 Integration tests

`BookingCreateIntegrationTest`

- Create booking success.
- Listing not active.
- Vehicle not active.
- Missing availability row.
- Date not FREE.
- Invalid date range.
- Self-booking.
- Customer overlap.
- Availability set to HOLD.
- Price snapshot stored as JSONB.

`BookingIdempotencyIntegrationTest`

- Missing key returns 400.
- Invalid key returns 400.
- Same key same body returns stored response.
- Same key different body returns 409.
- Concurrent same key does not create duplicate effect.

`BookingReadPatchIntegrationTest`

- Customer views own booking.
- Customer cannot view another customer booking.
- Host views own listing booking.
- Host cannot view other host listing booking.
- Admin views any booking.
- PATCH location fields succeeds.
- PATCH date/status fields rejected.

`BookingCancelIntegrationTest`

- Cancel HELD success.
- Availability released.
- Cancel non-owned booking returns 404.
- Cancel non-HELD booking returns 409.
- Same cancel key/body replays.
- Same cancel key/different body conflicts.

`BookingExpireJobIntegrationTest`

- Expired HELD becomes EXPIRED.
- Not-yet-expired HELD remains HELD.
- Availability released only for matching booking id and hold token.
- Running job twice is safe.
- Two processor calls do not double-process rows.

`BookingConcurrentIntegrationTest`

- 10 simultaneous booking requests.
- Exactly 1 success.
- Exactly 9 unavailable conflicts.

### 19.3 Test implementation notes

- Use Testcontainers PostgreSQL for all integration tests.
- Prefer real HTTP with `SpringBootTest(webEnvironment = RANDOM_PORT)` for the concurrent proof.
- Use `CountDownLatch` to align concurrent starts.
- Use unique idempotency keys for the 10 booking attempts in the availability race test.
- Keep scheduler disabled by default in test config; invoke processor manually in scheduler tests.

---

## 20. Implementation Handoff Rules

When using this spec for implementation:

1. Give one task section at a time.
2. Do not implement controllers, services, repositories, migrations, and tests in a single oversized change.
3. After every task, run tests or at least compile before moving on.
4. For TX-01, follow section 9 exactly.
5. For idempotency, follow section 8 exactly.
6. For expiry scheduler, follow section 13 exactly.
7. For concurrent tests, follow Task 11 and section 19.2.
8. Keep payment, timeline, audit, notification, outbox, and host approval outside Phase 5.

---

## 21. Definition of Done

Phase 5 is complete only when all are true:

- `POST /api/v1/bookings` creates HELD bookings correctly.
- Idempotency same key/body returns same response.
- Idempotency same key/different body returns `409 IDEMPOTENCY_KEY_CONFLICT`.
- Availability rows are locked in date ASC order and set to HOLD.
- Missing/non-FREE availability returns `409 LISTING_NOT_AVAILABLE`.
- Customer overlap returns `409 BOOKING_OVERLAP_CUSTOMER`.
- Self-booking returns `403 ACCESS_DENIED`.
- HELD cancel sets booking to CANCELLED and releases availability.
- Expire HELD job sets booking to EXPIRED and releases availability.
- Scheduler uses `FOR UPDATE SKIP LOCKED` inside the same transaction as processing.
- Vehicle archive precondition uses actual booking table.
- Critical concurrent booking test passes consistently.
- No Phase 6/7/8/9 features were implemented accidentally.
