# RentFlow — Software Requirements Specification

> Consolidated source-of-truth requirements for the RentFlow portfolio backend.

## Document Metadata

| Field | Value |
|---|---|
| Document type | Software Requirements Specification |
| Project | RentFlow — Car Rental Booking System |
| Version | 5.1 |
| Target architecture | Modular monolith REST API |
| Primary stack | Java 17, Spring Boot, PostgreSQL, Redis, MinIO, Testcontainers |
| Optional extension | Kafka / outbox publisher |
| Database | PostgreSQL |
| API style | REST + JSON |
| Main goal | Production-style backend portfolio project |
| Document date | 2026-05-09 |

---

## Core Decisions

| ID | Decision | Rationale |
|---|---|---|
| D-01 | Use REST API-first architecture. | Easy to demo with Swagger/Postman and reusable by frontend/mobile clients. |
| D-02 | Use modular monolith. | Easier to build than microservices while still demonstrating clean boundaries. |
| D-03 | Use PostgreSQL. | Strong transactions, row locks, JSONB, indexes, Testcontainers support. |
| D-04 | MVP uses day-based rental. | Keeps booking and availability deterministic. |
| D-05 | Booking period is `[pickupDate, returnDate)`. | Clear date-range semantics. |
| D-06 | Use pessimistic locking for booking availability. | Best fit for double-booking prevention. |
| D-07 | Booking state and payment state are separate. | Avoids mixed lifecycle logic. |
| D-08 | Payment is authorized upfront and captured at checkout. | Supports rental final charges and cancellation penalties. |
| D-09 | Payment stub supports partial capture. | Required for cancellation penalty. |
| D-10 | Idempotency is required for critical state-changing operations. | Prevents duplicate booking/payment/cancel/approval effects. |
| D-11 | Driver verification is enforced in final portfolio behavior. | Makes customer eligibility meaningful. |
| D-12 | Driver verification expiry is automatic. | Prevents stale approved licenses. |
| D-13 | Vehicle delete is soft delete/archive. | Preserves booking history. |
| D-14 | Files are stored in MinIO/S3; database stores metadata only. | Avoids app-local/static upload folders. |
| D-15 | Kafka is optional. | Scheduled outbox publisher is sufficient before Kafka. |

---

## P0 Scope (Must Complete First)

| Area | Scope |
|---|---|
| Auth | Register, login, refresh token, logout, RBAC |
| User | Basic profile |
| Vehicle | Host creates and manages own vehicles |
| Listing | Host creates listing, submits for approval; admin approves/rejects |
| Search | Public listing search with filters and pagination |
| Availability | Generate 365-day availability rows; block/unblock dates |
| Booking | Create booking hold with idempotency and pessimistic locking |
| Scheduler | Expire HELD bookings after 15 minutes using bounded batches |
| API docs | Swagger/OpenAPI |
| DB | PostgreSQL + Flyway |
| Tests | Testcontainers concurrency test: 10 requests, exactly 1 success |
| DevOps | Docker Compose for API + PostgreSQL + Redis |

## P1 Scope (After P0)

| Area | Scope |
|---|---|
| Payment Stub | Authorize, void, capture, partial capture, refund |
| Cancellation | Cancellation policy and refund/void/capture-penalty behavior |
| Driver Verification | Upload license, admin approve/reject, daily expiry job, booking gate |
| Audit | Audit log for important actions |
| Timeline | Booking timeline |
| Notification | Database notifications |
| Rate limiting | Login and booking attempt limits |
| Resource-level security | Strong ownership checks |
| Files metadata | `files` table and listing photo metadata |

## P2 Scope (Portfolio Extensions)

| Area | Scope |
|---|---|
| MinIO full flow | Signed URLs, file permission rules |
| Trip lifecycle | Check-in, check-out, odometer, fuel, photos |
| Reviews | Completed-booking reviews |
| Disputes | Complaint/dispute handling |
| Reports | Revenue, host earnings, utilization |
| Payouts | Host payout calculation |
| Outbox | Domain events and scheduled publisher |
| Kafka | Optional event bus integration |
| CI | Automated tests in CI |
| Observability | Actuator metrics, structured logs |

---

## Rental Model

- **Day-based rental**: `pickupDate` and `returnDate` are DATE fields.
- **Booking period**: `[pickupDate, returnDate)` — returnDate is exclusive.
- **Example**: pickupDate=2026-06-01, returnDate=2026-06-03 means occupied dates are 2026-06-01 and 2026-06-02.
- **Minimum rental**: 1 day
- **Maximum rental**: 30 days
- **Reject**: `returnDate <= pickupDate` or `returnDate - pickupDate > 30 days`

---

## Vehicle Lifecycle

### Statuses

```
DRAFT -> ACTIVE
ACTIVE -> MAINTENANCE, SUSPENDED, ARCHIVED (if preconditions pass)
MAINTENANCE -> ACTIVE, SUSPENDED
SUSPENDED -> ACTIVE, MAINTENANCE
Any -> ARCHIVED (if preconditions pass)
```

### Key Rules

- `DELETE /api/v1/host/vehicles/{id}` is soft delete (sets ARCHIVED).
- Vehicle can be archived only when: all listings ARCHIVED AND no HELD/CONFIRMED/IN_PROGRESS bookings.
- If vehicle becomes MAINTENANCE/SUSPENDED: ACTIVE listings become SUSPENDED.
- If vehicle becomes ARCHIVED: all non-ARCHIVED listings become ARCHIVED.
- CONFIRMED/IN_PROGRESS bookings remain valid when vehicle/listing is suspended.
- New vehicles default to ACTIVE unless host explicitly saves as DRAFT.
- DRAFT vehicles cannot be linked to ACTIVE listings.

---

## Availability Model

### Statuses

| Status | Meaning |
|---|---|
| FREE | Date can be booked |
| HOLD | Date is temporarily held for a booking |
| BOOKED | Date is confirmed/booked |
| BLOCKED | Host/admin manually blocked the date |

### Rules

- Primary key: `(listing_id, available_date)`
- When listing becomes ACTIVE: generate 365 availability rows.
- Missing availability row means unavailable.
- Lock order: always `available_date ASC`.
- HELD booking expires after 15 minutes.
- Host cannot block dates that are HOLD or BOOKED.

### Booking-to-Availability Mapping

| Booking Status | Availability Status |
|---|---|
| HELD | HOLD |
| PENDING_HOST_APPROVAL | HOLD |
| CONFIRMED | BOOKED |
| IN_PROGRESS | BOOKED |
| COMPLETED | BOOKED |
| CANCELLED before check-in | FREE |
| REJECTED | FREE |
| EXPIRED | FREE |

---

## Listing Lifecycle

### Statuses

```
DRAFT -> PENDING_APPROVAL
PENDING_APPROVAL -> ACTIVE, DRAFT
ACTIVE -> SUSPENDED
SUSPENDED -> ACTIVE
DRAFT/PENDING_APPROVAL/ACTIVE/SUSPENDED -> ARCHIVED
```

### Key Rules

- Admin approval required before ACTIVE.
- One ACTIVE listing per vehicle.
- Vehicle must be ACTIVE before listing can be ACTIVE.
- Listing cannot be archived with HELD, CONFIRMED, or IN_PROGRESS bookings.
- Approval to ACTIVE generates 365 availability rows synchronously.
- Submit is valid only from DRAFT → PENDING_APPROVAL (double submit returns conflict).

---

## Booking State Machine

### Statuses

| Status | Meaning | Allowed Next |
|---|---|---|
| HELD | Availability temporarily held | CONFIRMED, PENDING_HOST_APPROVAL, CANCELLED, EXPIRED |
| PENDING_HOST_APPROVAL | Payment authorized; host approval required | CONFIRMED, REJECTED, CANCELLED, EXPIRED |
| CONFIRMED | Booking accepted | IN_PROGRESS, CANCELLED |
| IN_PROGRESS | Customer picked up the car | COMPLETED |
| COMPLETED | Trip completed | None (terminal) |
| CANCELLED | Booking cancelled | None (terminal) |
| REJECTED | Host rejected | None (terminal) |
| EXPIRED | Hold or approval window expired | None (terminal) |

### Active Booking Definition (for overlap detection)

Active: HELD, PENDING_HOST_APPROVAL, CONFIRMED
Not active: COMPLETED, CANCELLED, REJECTED, EXPIRED

### Booking-to-Availability

- HELD: availability = HOLD
- PENDING_HOST_APPROVAL: availability = HOLD
- CONFIRMED: availability = BOOKED
- CANCELLED/REJECTED/EXPIRED before check-in: availability = FREE

---

## Payment State Machine

### Booking Payment Statuses

```
UNPAID
AUTHORIZED
CAPTURED
PARTIALLY_REFUNDED
REFUNDED
VOIDED
FAILED
```

### Payment Transactions

| Type | Status |
|---|---|
| AUTHORIZE | PENDING, SUCCEEDED, FAILED |
| CAPTURE | PENDING, SUCCEEDED, FAILED |
| VOID | PENDING, SUCCEEDED, FAILED |
| REFUND | PENDING, SUCCEEDED, FAILED |

### Partial Capture Rule

```
captureAmount <= authorizedAmount - capturedAmount
```

### Cancellation with Penalty Order

1. CAPTURE penalty amount
2. VOID remaining authorized amount
3. If VOID fails after CAPTURE: record failed VOID, schedule retry, alert admin

---

## Cancellation Policies

### FLEXIBLE

| Condition | Refund |
|---|---|
| Cancel at least 24 hours before pickup | 100% |
| Cancel less than 24 hours before pickup | 80% |
| Cancel after check-in | No automatic cancellation; dispute required |

### MODERATE

| Condition | Refund |
|---|---|
| Cancel at least 72 hours before pickup | 100% |
| Cancel 24-72 hours before pickup | 50% |
| Cancel less than 24 hours before pickup | 0% |
| Cancel after check-in | No automatic cancellation; dispute required |

### STRICT

| Condition | Refund |
|---|---|
| Cancel at least 7 days before pickup | 100% |
| Cancel less than 7 days before pickup | 0% |
| Cancel after check-in | No automatic cancellation; dispute required |

---

## Idempotency

### Required Operations

- CREATE_BOOKING
- AUTHORIZE_PAYMENT
- CAPTURE_PAYMENT
- VOID_PAYMENT
- REFUND_PAYMENT
- CANCEL_BOOKING
- HOST_APPROVE_BOOKING
- HOST_REJECT_BOOKING

### Header Format

```
Idempotency-Key: UUID-v4
```

### Behavior

| State | Same request hash | Different request hash |
|---|---|---|
| PROCESSING + lock active | 409 REQUEST_ALREADY_PROCESSING | 409 REQUEST_ALREADY_PROCESSING |
| PROCESSING + lock expired | retry allowed | retry allowed |
| COMPLETED | return stored response | 409 IDEMPOTENCY_KEY_CONFLICT |
| FAILED | retry allowed if lock expired | retry allowed if lock expired |

---

## Driver Verification

### Statuses

```
NOT_SUBMITTED
PENDING
APPROVED
REJECTED
EXPIRED
```

### Rules

- Booking gate: APPROVED driver verification required before new booking (P0 dev flag: `rentflow.booking.require-driver-verification=false`).
- Expiry: APPROVED/PENDING verification expires when `license_expiry_date < current_date`.
- Expiry job: daily at 00:00 UTC.
- Duplicate: at most one PENDING or APPROVED verification per customer.
- Re-submission allowed only after REJECTED or EXPIRED.
- Sensitive data: host never sees license number or license document.

---

## Business Rules Summary

| ID | Rule |
|---|---|
| BR-01 | Only ACTIVE listings are visible in public search. |
| BR-02 | Customer books by listingId, not vehicleId. |
| BR-03 | Host may manage only own vehicles/listings. |
| BR-04 | Listing must be admin-approved before becoming ACTIVE. |
| BR-05 | Vehicle must be ACTIVE before its listing can be ACTIVE. |
| BR-06 | A vehicle may have at most one ACTIVE listing. |
| BR-07 | Approval to ACTIVE generates 365 availability rows synchronously. |
| BR-08 | Missing availability row means unavailable. |
| BR-09 | Booking period is `[pickupDate, returnDate)`. |
| BR-10 | Minimum rental is 1 day; maximum is 30 days. |
| BR-11 | Customer cannot have overlapping active bookings. |
| BR-12 | Active booking statuses for overlap: HELD, PENDING_HOST_APPROVAL, CONFIRMED. |
| BR-13 | Customer cannot book own hosted listing. |
| BR-14 | APPROVED driver verification required before booking (dev flag can disable). |
| BR-19 | Create booking requires Idempotency-Key. |
| BR-22 | Booking creation locks availability rows using SELECT FOR UPDATE. |
| BR-24 | Availability rows locked in ascending available_date order. |
| BR-25 | If any requested date is not FREE: return LISTING_NOT_AVAILABLE. |
| BR-26 | HELD booking expires after 15 minutes. |
| BR-27 | PENDING_HOST_APPROVAL booking expires after 24 hours. |
| BR-36 | Vehicle archive: all listings must be ARCHIVED first, no HELD/CONFIRMED/IN_PROGRESS bookings. |
| BR-37 | If vehicle becomes MAINTENANCE or SUSPENDED: ACTIVE listings become SUSPENDED. |
| BR-43 | Audit logs must not store password hash, token hash, encrypted PII, or payment-sensitive data. |
| BR-47 | Cancellation with partial penalty: execute CAPTURE before VOID remaining authorization. |
| BR-48 | If CAPTURE succeeds but VOID fails: record failed VOID, schedule retry, alert admin. |
| BR-49 | Expiry jobs must use bounded batches and FOR UPDATE SKIP LOCKED. |
| BR-55 | cancellation_reason must be sanitized and limited to 500 characters. |

---

## Actors and Roles

| Actor | Description | Main Permissions |
|---|---|---|
| Guest | Unauthenticated user | Search active listings, view listing details, register, login |
| Customer | Registered renter | Manage profile, create bookings, pay, cancel, view own bookings |
| Host | Vehicle owner | Manage own vehicles/listings, manage availability, handle own booking requests |
| Admin | System operator | Manage users, approve listings, verify licenses, view all bookings, audit logs |
| Scheduler/Worker | Background actor | Expire holds, expire licenses, generate availability, publish events |

Roles: `CUSTOMER`, `HOST`, `ADMIN`. A user may have multiple roles through `user_roles`.

---

## Standard Error Codes

| Code | HTTP | Meaning |
|---|---|---|
| AUTH_INVALID_CREDENTIALS | 401 | Email or password invalid |
| AUTH_TOKEN_EXPIRED | 401 | Access token expired |
| ACCESS_DENIED | 403 | User lacks permission |
| USER_EMAIL_EXISTS | 409 | Email already registered |
| DRIVER_LICENSE_NOT_APPROVED | 403 | Customer is not eligible to book |
| ALREADY_SUBMITTED | 409 | Verification already pending/approved |
| VEHICLE_NOT_FOUND | 404 | Vehicle not found |
| VEHICLE_ARCHIVE_NOT_ALLOWED | 409 | Vehicle cannot be archived |
| LISTING_NOT_FOUND | 404 | Listing not found or not visible |
| LISTING_NOT_AVAILABLE | 409 | Listing unavailable for selected dates |
| BOOKING_OVERLAP_CUSTOMER | 409 | Customer has overlapping booking |
| BOOKING_INVALID_STATUS | 409 | Action not allowed for current booking status |
| IDEMPOTENCY_KEY_REQUIRED | 400 | Idempotency-Key required |
| IDEMPOTENCY_KEY_CONFLICT | 409 | Same key used with different body |
| REQUEST_ALREADY_PROCESSING | 409 | Request with same key is still processing |
| PAYMENT_FAILED | 402 | Payment operation failed |
| PAYMENT_VOID_RETRY_REQUIRED | 202 | Cancellation done but void retry required |
| VALIDATION_ERROR | 400 | Request validation failed |
| TOO_MANY_REQUESTS | 429 | Rate limit exceeded |
| INTERNAL_ERROR | 500 | Unexpected server error |

---

## Portfolio Demo Priority

The most important demo scenario:

```
10 customers try to book the same car for the same date range.
Only one booking succeeds.
The other 9 requests return 409 LISTING_NOT_AVAILABLE.
The test runs automatically with Testcontainers and PostgreSQL.
```

This proves: database transaction skill, pessimistic locking, idempotency behavior, business rule correctness, API error handling, production-style backend thinking.
