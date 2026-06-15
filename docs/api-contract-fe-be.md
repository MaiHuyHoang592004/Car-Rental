# API Contract FE ↔ BE — Deep-Dive Review

**Date:** 2026-06-03 | **Scope:** Every frontend API call cross-referenced against every backend endpoint

---

## Verdict

**COMPLETE — No path/method mismatches. 2 real implementation gaps were present during review: payment idempotency on sandbox confirmation and missing profile change-password UI.**

The frontend and backend are broadly aligned. All frontend API paths in scope resolve to valid backend endpoints, and the main contract issues found in auth/session were stale by the time of verification. The remaining actionable gaps were: (1) missing idempotency handling on `simulate-transfer-confirmation`, and (2) no frontend consumer for `PATCH /api/v1/users/me/password`.

---

## Flow Map

```
┌──────────────── FRONTEND API CALLS (75 total) ────────────────┐
│                                                                │
│  Auth BFF (5 routes)  ──proxies──▶  Backend Auth (8 endpoints)│
│  Auth direct (1 call) ───────────▶  Backend Auth              │
│  Profile (3 calls)    ───────────▶  Backend User (6 endpoints)│
│  Public Listings (4)  ───────────▶  Backend Listing (3)       │
│  Customer Bookings (7)───────────▶  Backend Booking (6)       │
│  Payments (4)         ───────────▶  Backend Payment (8)       │
│  Host Vehicles (8)    ───────────▶  Backend Vehicle (8)       │
│  Host Listings (12)   ───────────▶  Backend Listing (12)      │
│  Host Bookings (4)    ───────────▶  Backend HostBooking (4)   │
│  Host Availability (6)───────────▶  Backend Availability (6)  │
│  Host Reports (1)     ───────────▶  Backend Report (3)        │
│  Admin Listings (6)   ───────────▶  Backend AdminListing (6)  │
│  Admin Users (3)      ───────────▶  Backend AdminUser (4)     │
│  Admin Disputes (3)   ───────────▶  Backend AdminDispute (3)  │
│  Admin DriverVerif (3)───────────▶  Backend DriverVerif (3)   │
│  Files (2)            ───────────▶  Backend File (3)          │
│                                                                │
│  ❌ No FE consumer   ←────────────  Backend Trips (2)         │
│  ❌ No FE consumer   ←────────────  Backend Notifications (1) │
│  ❌ No FE consumer   ←────────────  Backend AdminRevenue (1)  │
│  ❌ No FE consumer   ←────────────  Backend PaymentCapture (4)│
│  ❌ No FE consumer   ←────────────  Backend PhotoCRUD (6)     │
│  ❌ No FE consumer   ←────────────  Backend Auth extra (3)    │
└────────────────────────────────────────────────────────────────┘
```

**Match rate: 54 of 75 backend endpoints consumed by frontend (72%). 21 unconsumed.**

---

## Findings

### F1 — ✅ VERIFIED: Frontend already sends `extras: []` for empty bookings

**Files:** Frontend `src/features/bookings/api.ts:219`, Backend `CreateBookingRequest.java`

Backend `CreateBookingRequest.extras` is `@NotNull @Valid List<RequestedExtra>`, but the current frontend payload builder already always includes the key and maps an empty selection to `extras: []`.

**Current state:** This is not a live FE-BE mismatch with the current code. The risk would only exist if a future caller bypasses `buildCreateBookingPayload(...)` and constructs the JSON body manually.

**Verdict:** Keep a regression test if desired, but do not treat this as an active blocker.

---

### F2 — 🔴 BLOCKER AT REVIEW TIME: No idempotency on `simulate-transfer-confirmation`

**Files:** Frontend `booking-payment-page-view.tsx`, Backend `BookingPaymentController.java:41-44`

Frontend sends `Idempotency-Key` header for `POST /bookings/{id}/payments/simulate-transfer-confirmation`. At review time, backend controller did not read or validate this header, so duplicate confirmation could execute twice.

**How to reproduce:** Confirm bank transfer, click twice rapidly → two confirmations processed.

**Why it matters:** Double confirmation could mark payment as confirmed twice, create duplicate events, or trigger side effects.

**Status:** Fixed in code. The endpoint now validates `Idempotency-Key` and routes through idempotency resolution before running sandbox confirmation.

**Test to add:** Backend integration test: send two identical confirmation requests with same idempotency key → second returns replay response.

---

### F3 — ✅ VERIFIED: `CreateBookingRequest` structure matches

**Files:** Frontend `features/bookings/api.ts`, Backend `CreateBookingRequest.java`

Frontend sends: `{ listingId, pickupDate, returnDate, pickupLocation?, returnLocation?, extras: [{ extraId, quantity }] }`

Backend expects `CreateBookingRequest`:
- `@NotNull UUID listingId` ✅
- `@NotNull LocalDate pickupDate` ✅
- `@NotNull LocalDate returnDate` ✅
- `String pickupLocation` ✅ (optional)
- `String returnLocation` ✅ (optional)
- `@NotNull @Valid List<RequestedExtra> extras` where `RequestedExtra = { @NotNull UUID extraId, @Min(1) @Max(5) int quantity }` ✅

No structural mismatch. Fields and types match. The earlier concern reduced to a future-regression note only.

---

### F4 — 🟡 HIGH AT REVIEW TIME: Backend had `PATCH /users/me/password` but frontend had no UI

**Files:** Backend `UserController.java:2.3`, Frontend `features/profile/api.ts`

Backend has `PATCH /api/v1/users/me/password` with `ChangePasswordRequest(currentPassword, newPassword)`. At review time, the frontend profile page only had `PATCH /api/v1/users/me` for name/profile fields and no password change form.

**How to reproduce:** Log in as customer → go to profile page → no "change password" section.

**Why it matters:** Backend built the endpoint but frontend never wired it. Users can only reset password via forgot-password email flow.

**Status:** Fixed in code. The profile page now exposes a change-password form that calls the canonical endpoint and maps `401 AUTH_INVALID_CREDENTIALS` to a user-facing error.

**Test to add:** Frontend test: change-password form renders, calls correct endpoint, handles wrong-current-password error.

---

### F5 — 🟡 MEDIUM: `displayOrder` is a UI capability gap, not a contract mismatch

**Files:** Frontend `features/host/vehicles/api.ts`, Backend `AddVehiclePhotoRequest.java`

Frontend types and API client already support `displayOrder`, but the current create-vehicle UI flow does not populate it when uploading photos. Backend accepts it as optional.

**Why it matters:** Frontend cannot control photo ordering at upload time. Low risk since backend allows it to be nullable.

**Recommended fix:** Treat this as backlog/UI parity work, not a contract break.

---

### F6 — 🟡 MEDIUM: `checksum` is a usage gap, not a schema mismatch

**Files:** Frontend upload-intent calls, Backend `CreatePhotoUploadIntentRequest.java`

Frontend upload-intent types already allow `checksum`, but current upload flows only send `{ contentType, sizeBytes }`. Backend treats `checksum` as optional.

**Why it matters:** No functional impact. But file integrity verification is lost without it.

**Recommended fix:** Keep as optional hardening work.

---

## Contract Mismatches

| # | Area | Frontend | Backend | Severity | Detail |
|---|---|---|---|---|---|
| 1 | Payment simulation | Sends Idempotency-Key | Now consumed and replay-safe | FIXED | Was a real bug at review time |
| 2 | Booking extras | Sends `extras: []` via payload builder | `@NotNull` field | VERIFIED | No active mismatch |
| 3 | Change password | Profile UI now calls endpoint | `PATCH /users/me/password` exists | FIXED | Was missing consumer at review time |
| 4 | Vehicle photo displayOrder | Type supported, UI not using it | Optional field in DTO | MEDIUM | Capability gap |
| 5 | Upload intent checksum | Type supported, flow not using it | Optional field in DTO | MEDIUM | Hardening gap |
| 6 | Host report earnings | Calls `overview` | Also has `earnings` endpoint | LOW | Extra endpoint not consumed |

**No path mismatches. No method mismatches. No response shape mismatches found.**

---

## Backend Endpoints With No Frontend Consumer (21 total)

| # | Endpoint | Method | Controller | Likely Intent |
|---|---|---|---|---|
| 1 | `/api/v1/auth/logout-all` | POST | AuthController | Implemented — logout everywhere |
| 2 | `/api/v1/auth/forgot-password` | POST | AuthController | Planned — forgot password UI |
| 3 | `/api/v1/auth/reset-password` | POST | AuthController | Planned — reset password UI |
| 4 | `/api/v1/users/me/password` | PATCH | UserController | **Missing UI** — change password |
| 5-7 | `/api/v1/host/listings/{id}/photos` GET/PATCH/DELETE | 3 | HostListingPhotoController | Planned — listing photo gallery |
| 8-10 | `/api/v1/host/vehicles/{id}/photos` GET/PATCH/DELETE | 3 | HostVehiclePhotoController | Planned — vehicle photo gallery |
| 11 | `/api/v1/host/reports/earnings` | GET | HostReportController | Planned — detailed earnings |
| 12 | `/api/v1/admin/reports/revenue` | GET | AdminReportController | Planned — admin revenue dashboard |
| 13 | `/api/v1/admin/users/{id}/bookings` | GET | AdminUserController | Planned — user booking drill-down |
| 14-15 | `/api/v1/bookings/{id}/check-in` + `check-out` | POST x2 | TripController | Planned — trip lifecycle UI |
| 16 | `/api/v1/notifications/me` | GET | NotificationController | **Missing UI** — notification center |
| 17 | `/api/v1/files/{fileId}/signed-url` | GET | FileController | Internal — might be server-to-server |
| 18-21 | `/api/v1/payments/{id}/capture`, `void`, `refund`, `reconciliation` | POSTx3 + GET | PaymentController | Admin only — admin payment panel needed |

**Verdict on unconsumed endpoints:** Most are clearly planned features (trips, photo galleries, notification center, admin payment management). After wiring change-password, `GET /notifications/me` is the most visible remaining UI gap in this list.

---

## Security/Permission Issues

| Issue | Severity | Detail |
|-------|----------|--------|
| Payment simulate-transfer-confirmation lacked idempotency at review time | HIGH | Fixed by requiring and resolving `Idempotency-Key` |
| `PATCH /users/me/password` exists but no RBAC check beyond authenticated | MEDIUM | Any authenticated user can change their own password — correct |
| Backend endpoints with no frontend consumer are still accessible | MEDIUM | API surface larger than necessary. Swagger or direct curl can access all 96 endpoints. Consider documenting intent. |
| Frontend sends `Idempotency-Key` on 7 mutation endpoints | EXCELLENT | Consistent idempotency pattern |
| Frontend `skipAuth: true` only on public endpoints | EXCELLENT | All authenticated endpoints have auth header |
| No path traversal or injection vectors | OK | UUID path variables validated |

---

## Missing Tests

| # | Test Scenario | Priority | Layer |
|---|---|---|---|
| 1 | Simulate transfer confirmation with duplicate idempotency key → replay | P0 | Backend integration |
| 2 | Change password: correct old password → 204 | P1 | Backend integration |
| 3 | Change password: wrong old password → 401 | P1 | Backend integration |
| 4 | Frontend profile page renders change-password form | P1 | Frontend unit |
| 5 | Frontend profile page maps wrong-current-password error | P1 | Frontend unit |
| 6 | Vehicle photo upload with displayOrder → field is sent | P2 | Frontend unit |
| 7 | Response contract: BookingResponse has all fields consumed by frontend | P2 | Contract test |

---

## Recommended Fix Slice

### Slice 1 — Fix the 2 real gaps found in review (P0/P1)

**Goal:** Eliminate the only two mismatches that can cause runtime failures.

**Tasks:**
1. **Backend:** Add idempotency validation and replay handling to `simulateTransferConfirmation()`.
2. **Frontend:** Add profile change-password form wired to `PATCH /api/v1/users/me/password`.

**Acceptance:**
- Two identical `POST /bookings/{id}/payments/simulate-transfer-confirmation` calls with same idempotency key → second returns 200 with first result, not duplicate.
- Profile change-password form calls the canonical endpoint and handles wrong-current-password cleanly.

---

### Slice 2 — Triage remaining UI gaps (P2)

**Goal:** Close the most visible missing UI gaps.

---

## Rental Experience Layer Update — 2026-06-07

### Phase 1 endpoints added

| Frontend consumer | Backend endpoint | Method | Notes |
|---|---|---|---|
| `frontend/src/features/trips/api.ts` | `/api/v1/bookings/{bookingId}/trip-photos/upload-intent` | POST | Requires `Idempotency-Key`; returns signed upload URL for private `TRIP_PHOTO`. |
| `frontend/src/features/trips/api.ts` | `/api/v1/files/{fileId}/finalize` | POST | Existing file finalize endpoint now supports `TRIP_PHOTO`. |
| `frontend/src/features/trips/api.ts` | `/api/v1/bookings/{bookingId}/condition-reports` | POST | Requires `Idempotency-Key`; creates CHECK_IN/CHECK_OUT report with required photo slots. |
| `frontend/src/features/trips/api.ts` | `/api/v1/bookings/{bookingId}/condition-reports` | GET | Lists reports visible to booking participants/admin. |
| `frontend/src/features/trips/api.ts` | `/api/v1/bookings/{bookingId}/condition-reports/{reportId}` | GET | Returns report detail with signed trip photo URLs. |
| `frontend/src/features/trips/api.ts` | `/api/v1/bookings/{bookingId}/check-in` | POST | Existing endpoint; frontend now calls it after CHECK_IN report submission. |
| `frontend/src/features/trips/api.ts` | `/api/v1/bookings/{bookingId}/check-out` | POST | Existing endpoint; frontend now calls it after CHECK_OUT report submission. |

### Frontend routes added

- `/bookings/[id]/check-in`
- `/bookings/[id]/check-out`

### Contract notes

- Required condition report photo angles are `FRONT`, `REAR`, `LEFT`, and `RIGHT`.
- Damage items use `photoFileId` in create requests because frontend only knows uploaded file IDs before report photos are persisted.
- Check-in/check-out request odometer and fuel must match the previously submitted condition report.
- Existing check-in/check-out endpoints are still not idempotency-protected because check-out includes external capture; this remains a documented follow-up in `docs/rental-experience-layer-plan.md`.

**Tasks:**
1. Add notification center page → calls `GET /api/v1/notifications/me`
2. Decide whether `displayOrder` and `checksum` should be exposed in host upload flows or left as internal capability

**Acceptance:**
- Notification bell icon shows count and opens notification list.
- Photo/upload optional fields are either wired or documented as intentional backlog.

---

### Slice 3 — Keep endpoint inventory current

**Goal:** Decide what to do with 21 unconsumed endpoints.

**Tasks:**
1. Mark endpoints as `planned`, `will-remove`, `admin-internal`, or `server-to-server` in a doc or annotation
2. Remove any dead endpoints that won't be implemented
3. Add Swagger `@Hidden` or `@Tag("planned")` annotations to planned-but-not-done endpoints

**Acceptance:** Every backend endpoint has a documented intent. No reviewer can say "this endpoint is unused."

---

## Commands to Run

```powershell
# Verify all backend endpoints compile
.\mvnw.cmd compile

# Run backend tests to verify contract
.\mvnw.cmd test -Dtest="BookingControllerTest,BookingPaymentControllerTest,UserControllerTest,VehicleControllerTest"

# Run backend integration tests
.\mvnw.cmd verify -Pintegration-tests -Dtest="AuthIntegrationTest,BookingPhase5IntegrationTest"

# Check frontend compiles
cd frontend; pnpm build

# Run frontend tests
cd frontend; pnpm test
```
