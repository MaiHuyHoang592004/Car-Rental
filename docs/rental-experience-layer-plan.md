# Rental Experience Layer Plan

## Status

Implemented in this slice:

- Phase 1: Trip condition reports and trip photos.

Active implementation loop:

- Phase 2-12: damage claims, deposits, protection plans, booking modification/late fee, support/messages, rental documents, host payout, notification center/admin queue, saved listings/search UX, two-sided reviews, vehicle documents.

Reason: this benchmark run continues after Phase 1 and attempts Phase 2 through Phase 12 sequentially. Each phase must be marked COMPLETE or PARTIAL with exact implementation and validation evidence after the phase loop runs.

## New Modules

- `com.rentflow.tripcondition.entity`
- `com.rentflow.tripcondition.repository`
- `com.rentflow.tripcondition.dto`
- `com.rentflow.tripcondition.service`
- `com.rentflow.tripcondition.controller`

Extended modules:

- `file`: trip-photo upload intent, finalize support for `TRIP_PHOTO`, attach validation.
- `trip`: check-in/check-out now require a matching condition report before state transition.
- `booking`, `audit`, `outbox`: condition report and damage item events are appended through existing timeline/audit/outbox services.

## New Tables

- `trip_condition_reports`
- `trip_condition_photos`
- `trip_damage_items`

Migration:

- `V29__trip_condition_reports.sql`

## New Endpoints

- `POST /api/v1/bookings/{bookingId}/trip-photos/upload-intent`
- `POST /api/v1/bookings/{bookingId}/condition-reports`
- `GET /api/v1/bookings/{bookingId}/condition-reports`
- `GET /api/v1/bookings/{bookingId}/condition-reports/{reportId}`

Frontend pages:

- `/bookings/[id]/check-in`
- `/bookings/[id]/check-out`

## State Machines

Condition report:

```text
none
  -> CHECK_IN submitted when booking = CONFIRMED
  -> linked to trip_record when check-in creates trip_records row

none
  -> CHECK_OUT submitted when booking = IN_PROGRESS and trip_record exists
  -> linked to existing trip_record at submission time
```

Trip lifecycle integration:

```text
CONFIRMED
  requires CHECK_IN report by current actor with matching odometer/fuel
  -> IN_PROGRESS

IN_PROGRESS
  requires CHECK_OUT report by current actor with matching odometer/fuel
  requires checkout odometer >= check-in odometer
  -> COMPLETED
```

Damage item:

```text
submitted with condition report only
no separate approval/claim state in this slice
```

## Permission Model

- Trip photo upload: booking customer or booking host only.
- Condition report create: booking customer or booking host only.
- Condition report read: booking customer, booking host, or admin.
- Unrelated users receive not found behavior for booking-scoped reads/writes.
- Trip check-in/check-out still follow the existing customer-only trip lifecycle behavior.

## Idempotency

Required and validated:

- `POST /api/v1/bookings/{bookingId}/trip-photos/upload-intent`
- `POST /api/v1/bookings/{bookingId}/condition-reports`

Scopes:

- `CREATE_TRIP_PHOTO_UPLOAD_INTENT`
- `SUBMIT_TRIP_CONDITION_REPORT`

Known limitation:

- Existing `POST /api/v1/bookings/{id}/check-in` and `POST /api/v1/bookings/{id}/check-out` still do not enforce `Idempotency-Key`. Check-out includes CoreBank capture outside the DB transaction; adding idempotency there needs a provider-safe design so retries cannot double-capture. This should be handled before broad external client usage.

## Timeline, Audit, Outbox Events

- `TRIP_CONDITION_CHECK_IN_SUBMITTED`
- `TRIP_CONDITION_CHECK_OUT_SUBMITTED`
- `TRIP_DAMAGE_ITEM_REPORTED`

## Manual Testing Steps

1. Create or seed a confirmed booking with authorized payment.
2. Open `/bookings/{bookingId}/check-in`.
3. Enter odometer/fuel, upload FRONT/REAR/LEFT/RIGHT images, submit.
4. Verify the booking moves to `IN_PROGRESS`.
5. Open `/bookings/{bookingId}/check-out`.
6. Enter odometer greater than or equal to check-in, upload required images, submit.
7. Verify booking moves to `COMPLETED` and payment capture behavior remains unchanged.
8. Attempt check-in without a condition report through API and verify `TRIP_CONDITION_REPORT_REQUIRED`.
9. Attempt a duplicate condition report with a different idempotency key and verify `TRIP_CONDITION_REPORT_ALREADY_EXISTS`.

## Active Phase Loop

### Slice 2 - Damage Claims

Objective: create dedicated damage claim workflow backed by submitted checkout reports.

Tables/endpoints planned:

- `damage_claims`
- `damage_claim_evidence`
- customer, host, admin damage claim APIs from the source request.

Definition of Done: claim creation/respond/approve/reject/charge covered by backend tests and admin/host/customer UI.

### Slice 3 - Deposit / Security Hold

Objective: separate security deposit hold from rental revenue and allow approved claims to deduct from held deposit.

Definition of Done: deposit snapshot on booking, authorize/release/deduct APIs, damage charge integration, idempotent provider/stub operations.

### Slice 4 - Protection Plans

Objective: customer selects a protection plan during booking and booking snapshots deductible/liability values.

Definition of Done: seeded plans, booking request extension, price snapshot update, frontend selector, liability calculation tests.

### Slice 5 - Booking Modification / Late Return

Objective: request extensions/date/location changes and detect late returns.

Definition of Done: availability locking for extensions, host approval flow, late fee scheduler, deposit charge/waive paths.

### Slice 6 - Support Tickets / Booking Messages

Objective: booking-scoped support cases and participant messages.

Definition of Done: participant/admin visibility rules, internal admin notes hidden from users, notification/timeline events.

### Slice 7 - Rental Documents

Objective: generate HTML snapshots for agreement, receipts, refund receipts, damage invoices.

Definition of Done: document generation hooks and printable frontend view.

### Slice 8 - Host Payout

Objective: host payout account and payout queue after completed captured booking.

Definition of Done: payout creation, hold rules, manual admin status transitions, host/admin UI.

### Slice 9 - Notification Center / Admin Operations

Objective: readable notification center and admin queue counts.

Definition of Done: read/read-all APIs, operations count API, frontend bell/pages.

### Slice 10 - Search UX / Saved Listings

Objective: richer public listing filters and saved listings.

Definition of Done: backend filters, save/unsave APIs, frontend filter bar and saved listing page.

### Slice 11 - Two-Sided Reviews

Objective: host reviews customer after completed trip.

Definition of Done: host customer review table/API and admin user drill-down.

### Slice 12 - Vehicle Documents / Compliance

Objective: vehicle registration/insurance/inspection upload and approval gating before listing approval.

Definition of Done: vehicle document upload/review APIs, expiry scheduler, listing approval blocker.

## Phase 2-12 Completion Evidence

### Slice 2 - Damage Claims - COMPLETE

- Added `damage_claims` and `damage_claim_evidence` tables in `V30__damage_claims.sql`.
- Added host/customer/admin damage claim APIs for create, list, respond, approve, reject, and charge.
- Added timeline/audit/outbox events: `DAMAGE_CLAIM_CREATED`, `DAMAGE_CLAIM_CUSTOMER_RESPONDED`, `DAMAGE_CLAIM_APPROVED`, `DAMAGE_CLAIM_REJECTED`, `DAMAGE_CLAIM_CHARGED`.
- Validation: `DamageClaimServiceTest` passed.

### Slice 3 - Deposit / Security Hold - COMPLETE

- Added booking deposit aggregate and deposit transactions in `V31__booking_deposits.sql`.
- Booking creation now creates a deposit requirement snapshot.
- Added customer authorize and admin release/deduct APIs.
- Damage claim charge can deduct from held deposit.
- Validation: `DepositServiceTest` passed.

### Slice 4 - Protection Plans - COMPLETE

- Added protection plan catalog and booking protection snapshots in `V32__protection_plans.sql`.
- Booking request accepts `protectionPlanCode`; price snapshot includes protection fee/deductible/coverage values.
- Damage approval caps liability using the booking protection snapshot.
- Frontend booking form includes protection plan selection.
- Validation: `ProtectionPlanServiceTest` and `BookingPriceCalculatorTest` passed.

### Slice 5 - Booking Modification / Late Return - COMPLETE

- Added booking modification requests and late return fees in `V33__booking_modification_late_return.sql`.
- Added customer modification request/cancel/list APIs, host approve/reject APIs, admin late-fee waive/charge APIs.
- Added late-return scheduler and system event emission.
- Extension approval locks and books added availability rows.
- Validation: `BookingModificationServiceTest` passed.

### Slice 6 - Support Tickets / Booking Messages - COMPLETE

- Added booking-scoped support cases and messages in `V34__support_cases_messages.sql`.
- Participant visibility filters out admin internal notes; admin sees full thread.
- Added support case create/list/get/message/close APIs.
- Added notification/timeline/audit/outbox events for support cases and messages.
- Validation: `SupportCaseServiceTest` passed.

### Slice 7 - Rental Documents - COMPLETE

- Added HTML rental document snapshots in `V35__rental_documents.sql`.
- Added agreement, payment receipt, refund receipt, and damage invoice generation APIs.
- Added printable HTML endpoint and frontend `/documents/[id]/print` view.
- Validation: `RentalDocumentServiceTest` passed.

### Slice 8 - Host Payout - COMPLETE

- Added host payout accounts and payout queue in `V36__host_payouts.sql`.
- Added host payout account/list APIs and admin payout queue/approve/hold/mark-paid/fail APIs.
- Added payout queue scheduler for completed captured bookings.
- Validation: `HostPayoutServiceTest` passed.

### Slice 9 - Notification Center / Admin Operations - COMPLETE

- Notification center now supports unread count, mark-read, and mark-all-read APIs.
- Added admin operations queue counts for damage claims, booking modifications, late fees, support cases, payouts, disputes, and payment void retries.
- Added frontend notification page and API proxies.
- Validation: `NotificationServiceTest` and `AdminOperationsServiceTest` passed.

### Slice 10 - Search UX / Saved Listings - COMPLETE

- Listing search now accepts `instantBook` and `minRating` filters.
- Added saved listings table and save/unsave/list APIs in `V37__saved_listings.sql`.
- Added frontend saved listings page and richer listing filters.
- Validation: `ListingSearchServiceTest` and `SavedListingServiceTest` passed.

### Slice 11 - Two-Sided Reviews - COMPLETE

- Added host-to-customer review table in `V38__host_customer_reviews.sql`.
- Added host create/list APIs, customer self list API, and admin user drill-down API.
- Validation: `HostCustomerReviewServiceTest` passed.

### Slice 12 - Vehicle Documents / Compliance - COMPLETE

- Added vehicle document table in `V39__vehicle_documents.sql`.
- Added host submit/list APIs and admin list/approve/reject APIs.
- Listing approval now requires approved, unexpired registration, insurance, and inspection documents.
- Validation: `VehicleComplianceServiceTest` and `VehicleDocumentServiceTest` passed.
