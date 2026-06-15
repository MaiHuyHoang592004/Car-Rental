# RentFlow API Contracts

Version: 2026-05-30  
Source: current backend code plus `docs/srs.md`, `docs/error-codes.md`, and phase docs.  
Base URL: `/api/v1`  
Status: current backend/frontend alignment document.

## Ground Rules

- API style is REST JSON.
- Protected endpoints require `Authorization: Bearer <accessToken>`.
- Public endpoints in current backend: auth register/login/refresh/logout, health, Swagger/OpenAPI, public listing search/detail, and public listing availability.
- Host endpoints require role `HOST`.
- Admin endpoints require role `ADMIN`.
- Booking endpoints require authentication; service layer enforces customer/host/admin ownership rules.
- Dates use ISO date strings: `YYYY-MM-DD`.
- Instants use ISO-8601 UTC strings.
- Booking date range is half-open: `[pickupDate, returnDate)`.
- Page size defaults to `20` and is clamped to max `100`.

## Common Headers

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <accessToken>
X-Correlation-Id: <client-generated-or-empty>
```

The shared frontend API client generates `X-Correlation-Id` for `/api/v1` calls when callers do not provide one, and preserves caller-provided values. Backend still generates and echoes a correlation id if an external client omits the header.

`Idempotency-Key` is required by current code for:

```http
POST /api/v1/bookings
POST /api/v1/bookings/{id}/cancel
POST /api/v1/bookings/{id}/payments/authorize
POST /api/v1/payments/{paymentId}/capture
POST /api/v1/payments/{paymentId}/void
POST /api/v1/payments/{paymentId}/refund
POST /api/v1/host/bookings/{id}/approve
POST /api/v1/host/bookings/{id}/reject
```

The value must be UUID-v4. Reuse the same key only for retrying the exact same request body.

## Common Response Shapes

### Standard Error

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    { "field": "email", "message": "Email must be valid" }
  ],
  "correlationId": "req-id"
}
```

### Custom PageResponse

Used by public listing search, booking list, host/admin list endpoints, and host booking list.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "pageNumber": 0
}
```

## Enums

```ts
type Role = "CUSTOMER" | "HOST" | "ADMIN";
type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";
type DriverVerificationStatus = "NOT_SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED";
type VehicleStatus = "DRAFT" | "ACTIVE" | "MAINTENANCE" | "SUSPENDED" | "ARCHIVED";
type VehicleCategory = "SEDAN" | "SUV" | "HATCHBACK" | "PICKUP" | "VAN" | "MINIVAN" | "SPORTS" | "LUXURY" | "ECONOMY" | "MPV";
type TransmissionType = "MANUAL" | "AUTO";
type FuelType = "PETROL" | "DIESEL" | "ELECTRIC" | "HYBRID" | "LPG" | "GASOLINE" | "EV";
type ListingStatus = "DRAFT" | "PENDING_APPROVAL" | "ACTIVE" | "SUSPENDED" | "ARCHIVED";
type CancellationPolicy = "FLEXIBLE" | "MODERATE" | "STRICT";
type PricingType = "PER_DAY" | "PER_TRIP";
type AvailabilityStatus = "FREE" | "HOLD" | "BOOKED" | "BLOCKED";
type BookingStatus = "HELD" | "PENDING_HOST_APPROVAL" | "CONFIRMED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "REJECTED" | "EXPIRED";
type PaymentMethod = "BANK_TRANSFER_QR" | "COREBANK_TRANSFER" | "STUB";
type PaymentProviderType = "VIETQR_MANUAL" | "COREBANK" | "STUB";
type PaymentStatus = "UNPAID" | "PENDING_TRANSFER" | "AUTHORIZED" | "CAPTURED" | "PARTIALLY_REFUNDED" | "REFUNDED" | "VOIDED" | "FAILED" | "RECONCILIATION_REQUIRED";
```

## Auth

### Register

`POST /api/v1/auth/register`

Request:

```json
{
  "email": "customer@example.com",
  "password": "Password123",
  "fullName": "Nguyen Van A",
  "roles": ["CUSTOMER"]
}
```

Response `201`: current controller returns the auth service response. Current DTOs indicate token response shape:

```json
{
  "tokenType": "Bearer",
  "accessToken": "jwt",
  "accessTokenExpiresAt": "2026-05-11T10:00:00Z",
  "refreshToken": "refresh-token",
  "refreshTokenExpiresAt": "2026-05-18T10:00:00Z",
  "user": {
    "id": "uuid",
    "email": "customer@example.com",
    "roles": ["CUSTOMER"],
    "fullName": "Nguyen Van A",
    "phone": null,
    "dateOfBirth": null,
    "addressLine": null,
    "driverVerificationStatus": "NOT_SUBMITTED"
  }
}
```

### Login

`POST /api/v1/auth/login`

Request:

```json
{
  "email": "customer@example.com",
  "password": "Password123"
}
```

Response `200`: `TokenResponse`.

### Refresh

`POST /api/v1/auth/refresh`

Request:

```json
{ "refreshToken": "refresh-token" }
```

Response `200`:

```json
{
  "tokenType": "Bearer",
  "accessToken": "new-jwt",
  "accessTokenExpiresAt": "2026-05-11T10:15:00Z",
  "refreshToken": "new-refresh-token",
  "refreshTokenExpiresAt": "2026-05-18T10:15:00Z"
}
```

### Logout

`POST /api/v1/auth/logout`

Request:

```json
{ "refreshToken": "refresh-token" }
```

Response `204`.

### Email Verification

Email verification links are sent by backend mail delivery when enabled.

Runtime configuration for real SMTP email delivery:

```env
RENTFLOW_MAIL_ENABLED=true
RENTFLOW_MAIL_FROM=no-reply@rentflow.example
RENTFLOW_FRONTEND_BASE_URL=http://localhost:3000
RENTFLOW_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3001,http://localhost:3002
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

Provider examples:

- SendGrid SMTP: `SPRING_MAIL_HOST=smtp.sendgrid.net`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME=apikey`, `SPRING_MAIL_PASSWORD=<sendgrid-api-key>`.
- Mailgun SMTP: use the SMTP hostname, username, and password from the verified Mailgun domain.
- AWS SES SMTP: use the SES SMTP endpoint for the selected region, plus SES SMTP username/password, not the AWS access key directly.
- Local dev with Mailpit/Mailhog: point `SPRING_MAIL_HOST` and `SPRING_MAIL_PORT` at the local SMTP container and set SMTP auth to `false`.

Local Mailpit flow:

```powershell
# Option A: use the repo compose service
docker compose up -d mailpit

# Option B: run Mailpit ad hoc
docker run --rm -p 1025:1025 -p 8025:8025 axllent/mailpit

# Start backend in the same terminal so mail env applies to that process
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend-mailpit.ps1
```

Mailpit SMTP listens on `localhost:1025`; the inbox UI is `http://localhost:8025`.
The backend script writes logs to `%TEMP%\rentflow-backend.log` and starts `http://localhost:8087`.
If port `8087` is already in use, stop the owning process first.
Environment variables set in one terminal do not affect a backend process launched by another terminal, agent, or script; set the mail env vars in the same process that runs `mvn spring-boot:run`.
The `local` profile now defaults to Mailpit (`localhost:1025`) with `rentflow.mail.enabled=true`; production must still opt in explicitly through environment variables.
The `local` and `docker` profiles also default `rentflow.payment.sandbox-transfer-confirmation.enabled=true` so manual transfer demo flows can move from `PENDING_TRANSFER` to host review without affecting production.

Raw verification tokens are never stored in the database and must not be logged. The backend stores only the token hash and sends the raw token inside the frontend link.

`POST /api/v1/auth/verify-email` remains public and accepts:

```json
{ "token": "raw-token-from-email" }
```

Invalid, expired, or reused tokens return `409 INVALID_TOKEN`. If authenticated resend cannot deliver email, API returns `409 EMAIL_DELIVERY_FAILED`.

## User

### Current Profile

`GET /api/v1/users/me`

Response `200`:

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "emailVerified": false,
  "roles": ["CUSTOMER"],
  "fullName": "Nguyen Van A",
  "phone": "0900000000",
  "dateOfBirth": "1999-01-01",
  "addressLine": "Ho Chi Minh City",
  "driverVerificationStatus": "NOT_SUBMITTED"
}
```

When `emailVerified=false`, current-state policy blocks `POST /api/v1/bookings` and `POST /api/v1/bookings/{id}/payments/authorize` with `403 EMAIL_NOT_VERIFIED`.

### Update Profile

`PATCH /api/v1/users/me`

Request:

```json
{
  "fullName": "Nguyen Van B",
  "phone": "0911111111",
  "dateOfBirth": "1999-01-01",
  "addressLine": "Hanoi"
}
```

Response `200`: `UserProfileResponse`.

## Public Listings

### Search Listings

`GET /api/v1/listings`

Query params:

| Param | Type | Notes |
|---|---|---|
| `city` | string | Optional |
| `categories` | array/string list | `VehicleCategory` values; DTO field is plural |
| `pickupDate` | date | Optional, pair with `returnDate` |
| `returnDate` | date | Optional, pair with `pickupDate` |
| `minPrice` | decimal | Optional |
| `maxPrice` | decimal | Optional |
| `seats` | int | Optional |
| `transmission` | enum | Optional |
| `fuelType` | enum | Optional |
| `page` | int | Default `0` |
| `size` | int | Default `20`, max `100` |

Response `200`:

```json
{
  "content": [
    {
      "id": "listing-id",
      "title": "Toyota Vios 2022",
      "city": "Hanoi",
      "category": "SEDAN",
      "basePricePerDay": 700000,
      "currency": "VND",
      "seats": 5,
      "transmission": "AUTO",
      "fuelType": "GASOLINE",
      "coverPhotoUrl": null,
      "ratingAverage": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "pageNumber": 0
}
```

### Listing Detail

`GET /api/v1/listings/{id}`

Response `200`:

```json
{
  "id": "listing-id",
  "title": "Toyota Vios 2022",
  "description": "Clean sedan",
  "city": "Hanoi",
  "address": "District 1",
  "basePricePerDay": 700000,
  "currency": "VND",
  "dailyKmLimit": 200,
  "instantBook": false,
  "cancellationPolicy": "FLEXIBLE",
  "photos": [],
  "vehicleSummary": {
    "category": "SEDAN",
    "make": "Toyota",
    "model": "Vios",
    "year": 2022,
    "transmission": "AUTO",
    "fuelType": "GASOLINE",
    "seats": 5,
    "status": "ACTIVE"
  },
  "extras": []
}
```

### Public Availability

`GET /api/v1/listings/{listingId}/availability?from=2026-06-01&to=2026-06-30`

Current response DTO:

```json
{
  "listingId": "listing-id",
  "availability": {
    "2026-06-01": "FREE",
    "2026-06-02": "UNAVAILABLE"
  }
}
```

Public response must not expose booking IDs, hold tokens, customer data, or payment data.

## Host Vehicles

### Create Vehicle

`POST /api/v1/host/vehicles`

Request:

```json
{
  "category": "SEDAN",
  "make": "Toyota",
  "model": "Vios",
  "year": 2022,
  "plateNumber": "30A-12345",
  "vin": "optional",
  "transmission": "AUTO",
  "fuelType": "GASOLINE",
  "seats": 5,
  "status": "ACTIVE"
}
```

Response `201`: `VehicleResponse`.

### List Vehicles

`GET /api/v1/host/vehicles?status=ACTIVE&page=0&size=20`

Response `200`: `PageResponse<VehicleResponse>`.

### Get Vehicle

`GET /api/v1/host/vehicles/{id}`

Response `200`: `VehicleResponse`.

### Update Vehicle

`PATCH /api/v1/host/vehicles/{id}`

Patchable body:

```json
{
  "category": "SUV",
  "make": "Toyota",
  "model": "Fortuner",
  "year": 2023,
  "transmission": "AUTO",
  "fuelType": "DIESEL",
  "seats": 7,
  "status": "MAINTENANCE"
}
```

Response `200`: `VehicleResponse`.

### Archive Vehicle

`DELETE /api/v1/host/vehicles/{id}`

Response `204`. Backend rejects archive when active related bookings exist.

## Host Listings

### Create Listing

`POST /api/v1/host/listings`

Request:

```json
{
  "vehicleId": "vehicle-id",
  "title": "Toyota Vios 2022",
  "description": "Clean sedan",
  "city": "Hanoi",
  "address": "District 1",
  "latitude": 10.762622,
  "longitude": 106.660172,
  "basePricePerDay": 700000,
  "currency": "VND",
  "dailyKmLimit": 200,
  "instantBook": false,
  "cancellationPolicy": "FLEXIBLE"
}
```

Response `201`: `ListingResponse`.

### List Host Listings

`GET /api/v1/host/listings?status=DRAFT&page=0&size=20`

Response `200`: `PageResponse<ListingSummaryResponse>`.

### Get, Update, Submit, Archive, Reactivate

```http
GET   /api/v1/host/listings/{id}
PATCH /api/v1/host/listings/{id}
POST  /api/v1/host/listings/{id}/submit
POST  /api/v1/host/listings/{id}/archive
POST  /api/v1/host/listings/{id}/reactivate
```

Update request accepts listing fields except vehicle, host, status, and extras.

## Host Availability

### Full Availability

`GET /api/v1/host/listings/{id}/availability?from=2026-06-01&to=2026-06-30`

Response `200`:

```json
{
  "listingId": "listing-id",
  "from": "2026-06-01",
  "to": "2026-06-30",
  "dates": [
    {
      "date": "2026-06-01",
      "status": "HOLD",
      "bookingId": ["booking-id"],
      "expiresAt": "2026-06-01T12:15:00Z"
    }
  ]
}
```

Current DTO field is `bookingId` but type is `List<UUID>`.

### Block / Unblock Dates

```http
POST /api/v1/host/listings/{id}/availability/block
POST /api/v1/host/listings/{id}/availability/unblock
```

Request:

```json
{ "dates": ["2026-06-10", "2026-06-11"] }
```

Response:

```json
{ "updatedCount": 2 }
```

## Admin Listings

```http
GET  /api/v1/admin/listings?status=PENDING_APPROVAL&hostId={uuid}&city=Hanoi&page=0&size=20
GET  /api/v1/admin/listings/{id}
POST /api/v1/admin/listings/{id}/approve
POST /api/v1/admin/listings/{id}/reject
POST /api/v1/admin/listings/{id}/suspend
POST /api/v1/admin/listings/{id}/reactivate
```

Reject request:

```json
{ "reason": "Missing required information" }
```

Suspend request:

```json
{ "reason": "Policy violation" }
```

Admin detail response:

```json
{
  "listing": {},
  "host": {
    "id": "host-id",
    "fullName": "Host Name",
    "email": "host@example.com"
  },
  "bookingSummary": {
    "activeBookings": 0
  }
}
```

## Admin Users

`GET /api/v1/admin/users?status=ACTIVE&role=CUSTOMER&page=0&size=20`

Response `200`: `PageResponse<UserSummaryResponse>`.

## Host Bookings

```http
GET  /api/v1/host/bookings?status=PENDING_HOST_APPROVAL&page=0&size=20
POST /api/v1/host/bookings/{id}/approve
POST /api/v1/host/bookings/{id}/reject
```

`GET` response `200`: `PageResponse<BookingSummaryResponse>`.

`POST /approve` and `POST /reject` both require:

```http
Idempotency-Key: UUID-v4
```

Response `200`: `BookingResponse`.

## Bookings

### Create Booking Hold

`POST /api/v1/bookings`

Headers:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

Request:

```json
{
  "listingId": "listing-id",
  "pickupDate": "2026-06-01",
  "returnDate": "2026-06-03",
  "pickupLocation": "Hanoi",
  "returnLocation": "Hanoi",
  "extras": [
    { "extraId": "extra-id", "quantity": 1 }
  ]
}
```

Response `201`:

```json
{
  "id": "booking-id",
  "status": "HELD",
  "listingId": "listing-id",
  "listingTitle": "Toyota Vios 2022",
  "customerId": "customer-id",
  "hostId": "host-id",
  "pickupDate": "2026-06-01",
  "returnDate": "2026-06-03",
  "pickupLocation": "Hanoi",
  "returnLocation": "Hanoi",
  "holdExpiresAt": "2026-05-11T10:15:00Z",
  "totalAmount": 1500000,
  "currency": "VND",
  "priceSnapshot": {
    "rentalDays": 2,
    "basePricePerDay": 700000,
    "baseAmount": 1400000,
    "extraAmount": 100000,
    "totalAmount": 1500000,
    "currency": "VND",
    "extras": []
  },
  "policySnapshot": {
    "cancellationPolicy": "FLEXIBLE",
    "instantBook": false,
    "dailyKmLimit": 200
  },
  "createdAt": "2026-05-11T10:00:00Z"
}
```

### My Bookings

`GET /api/v1/bookings/me?status=HELD&page=0&size=20`

Response `200`: `PageResponse<BookingSummaryResponse>`.

### Booking Detail

`GET /api/v1/bookings/{id}`

Access: customer owner, host owner, or admin.

Response `200`: `BookingResponse`.

### Patch Booking Locations

`PATCH /api/v1/bookings/{id}`

Only allowed fields:

```json
{
  "pickupLocation": "New pickup",
  "returnLocation": "New return"
}
```

At least one non-null allowed field is required. Unknown fields return validation error. Allowed statuses are `HELD`, `PENDING_HOST_APPROVAL`, and `CONFIRMED`.

### Cancel Booking

`POST /api/v1/bookings/{id}/cancel`

Headers:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

Request:

```json
{ "reason": "Change of plan" }
```

Current-state behavior depends on booking status:

- `HELD` -> cancel immediately, release held availability, return `200`
- `PENDING_HOST_APPROVAL` -> attempt payment void, then cancel booking:
  - void success -> `200`
  - void provider failure -> booking still becomes `CANCELLED`, availability is released, and API returns `202` with retry metadata
- `CONFIRMED` before pickup -> apply cancellation policy:
  - full refund path -> void authorization, then `200`
  - partial/no-refund path -> capture penalty, void remainder, then `200`
  - if provider void fails after capture/void attempt -> booking still becomes `CANCELLED` and API returns `202` with retry metadata
- `CONFIRMED` after pickup time, or any unsupported status -> `409 BOOKING_INVALID_STATUS`

Example response `200`:

```json
{
  "id": "booking-id",
  "status": "CANCELLED",
  "cancellationReason": "Change of plan",
  "cancelled": true,
  "voidRetryRequired": false,
  "code": null,
  "paymentRetryState": null
}
```

Example response `202` when provider void must be retried asynchronously:

```json
{
  "id": "booking-id",
  "status": "CANCELLED",
  "cancellationReason": "Late cancel",
  "cancelled": true,
  "voidRetryRequired": true,
  "code": "PAYMENT_VOID_RETRY_REQUIRED",
  "paymentRetryState": "VOID_RETRY_REQUIRED"
}
```

`Idempotency-Key` remains required. Replays with the same key/body return the same terminal response, including the accepted/retry-required shape when cancellation has already been persisted.

## Payments

### Payment Bank Catalog

`GET /api/v1/payment-banks`

Response `200`:

```json
{
  "items": [
    {
      "id": "bank-id",
      "code": "COREBANK",
      "bin": null,
      "shortName": "CoreBank Demo Bank",
      "fullName": "CoreBank Demo Bank",
      "paymentMethod": "COREBANK_TRANSFER",
      "provider": "COREBANK",
      "active": true
    }
  ]
}
```

### Authorize Booking Payment

`POST /api/v1/bookings/{id}/payments/authorize`

Headers:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

Request:

```json
{
  "bankId": "bank-id",
  "paymentMethod": "COREBANK_TRANSFER"
}
```

Response `200`:

```json
{
  "booking": {
    "id": "booking-id",
    "status": "CONFIRMED",
    "pickupDate": "2026-06-01",
    "returnDate": "2026-06-03",
    "totalAmount": 1400000,
    "currency": "VND"
  },
  "payment": {
    "id": "payment-id",
    "status": "AUTHORIZED",
    "paymentMethod": "COREBANK_TRANSFER",
    "provider": "COREBANK",
    "externalOrderRef": "rentflow:booking:booking-id",
    "providerPaymentOrderId": "provider-order-id",
    "providerHoldId": "provider-hold-id",
    "authorizedAmount": 1400000,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND",
    "transferInstruction": null
  }
}
```

For generic bank-transfer flows, `payment.status` remains `PENDING_TRANSFER` and `transferInstruction` is populated; QR/instruction generation does not imply money has moved.

### Get Booking Payment Detail

`GET /api/v1/bookings/{id}/payments`

Response `200`:

```json
{
  "booking": {
    "id": "booking-id",
    "customerId": "customer-id",
    "hostId": "host-id",
    "status": "CONFIRMED",
    "pickupDate": "2026-06-01",
    "returnDate": "2026-06-03"
  },
  "payment": {
    "id": "payment-id",
    "selectedBankId": "bank-id",
    "paymentMethod": "COREBANK_TRANSFER",
    "provider": "COREBANK",
    "status": "AUTHORIZED",
    "authorizedAmount": 1400000,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND",
    "externalOrderRef": "rentflow:booking:booking-id",
    "providerPaymentOrderId": "provider-order-id",
    "providerHoldId": "provider-hold-id",
    "providerStatus": "AUTHORIZED",
    "transferInstruction": null
  },
  "transactions": []
}
```

If no payment exists yet, current backend returns `404`.

### Capture Payment

`POST /api/v1/payments/{paymentId}/capture`

Headers:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

Request:

```json
{
  "amount": 700000
}
```

Response `200`: `PaymentDetailResponse`.

### Void Payment

`POST /api/v1/payments/{paymentId}/void`

Headers:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

Response `200`: `PaymentDetailResponse`.

### Refund Payment

`POST /api/v1/payments/{paymentId}/refund`

Headers:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

Request:

```json
{
  "amount": 300000,
  "reason": "Customer cancellation refund"
}
```

Response `200`: `PaymentDetailResponse`.

### Payment Reconciliation

`GET /api/v1/payments/{paymentId}/reconciliation`

Response `200`:

```json
{
  "local": {
    "paymentId": "payment-id",
    "bookingId": "booking-id",
    "provider": "COREBANK",
    "paymentStatus": "AUTHORIZED",
    "providerStatus": "AUTHORIZED",
    "externalOrderRef": "rentflow:booking:booking-id",
    "providerPaymentOrderId": "provider-order-id",
    "providerHoldId": "provider-hold-id",
    "authorizedAmount": 1400000,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND"
  },
  "provider": {
    "status": "AUTHORIZED",
    "paymentOrderId": "provider-order-id",
    "holdId": "provider-hold-id",
    "authorizedAmount": 1400000,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND"
  },
  "mismatchFlags": {
    "statusMismatch": false,
    "amountMismatch": false,
    "referenceMismatch": false
  },
  "requiresReconciliation": false
}
```

## Error Codes Important For Frontend

| Code | Typical UI handling |
|---|---|
| `AUTH_INVALID_CREDENTIALS` | Show login error |
| `AUTH_TOKEN_EXPIRED` | Try refresh token, then logout if refresh fails |
| `ACCESS_DENIED` | Hide action or show forbidden |
| `USER_EMAIL_EXISTS` | Show account already exists |
| `DRIVER_LICENSE_NOT_APPROVED` | Route to driver verification |
| `VEHICLE_NOT_FOUND` | Show not found |
| `VEHICLE_ARCHIVE_NOT_ALLOWED` | Explain active listing/booking constraint |
| `LISTING_NOT_FOUND` | Show listing unavailable/not found |
| `LISTING_NOT_AVAILABLE` | Refresh calendar and ask user to choose dates again |
| `BOOKING_OVERLAP_CUSTOMER` | Link to my bookings |
| `BOOKING_INVALID_STATUS` | Refresh booking status; cancel is only blocked for unsupported states or invalid time windows |
| `PAYMENT_VOID_RETRY_REQUIRED` | Show cancellation accepted, explain payment void will be retried in background |
| `EMAIL_NOT_VERIFIED` | Route user to profile and resend verification before retrying booking/payment |
| `IDEMPOTENCY_KEY_REQUIRED` | Client bug; regenerate request flow |
| `IDEMPOTENCY_KEY_CONFLICT` | Do not retry with same key |
| `REQUEST_ALREADY_PROCESSING` | Keep submit disabled and poll/fetch status |
| `VALIDATION_ERROR` | Map `details[]` to fields |
| `TOO_MANY_REQUESTS` | Back off |
| `INTERNAL_ERROR` | Generic retry/support message |

## Planned But Not Implemented In Current Backend

These are in the SRS/phase docs but should not be wired in frontend until backend endpoints exist:

- Driver license submission and admin verification endpoints.
- File metadata and signed upload/download.
- Listing photo management.
- Notifications.
- Audit logs.
- Reports, payouts, reviews, disputes, trip check-in/out.
