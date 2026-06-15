# Roadmap — RentFlow Implementation & Refactor

Roadmap này thay bản phase-only cũ. Mục tiêu là giữ cả **implementation track** và **refactor/hardening track** dựa trên review kỹ thuật đã được đối chiếu lại với code hiện tại.

Quy ước trạng thái:

- **Confirmed**: có evidence trong code thật.
- **Suspected**: nhận định đúng hướng nhưng cần kiểm thử/đọc thêm.
- **Spec-only**: chỉ có trong SRS/docs, chưa thấy code implement.

---

## 0. Current Project State

### Backend

Code hiện tại không còn ở Phase 1-5 thuần. Backend đã có:

- Auth: register, login, refresh, logout, JWT, refresh token DB, BCrypt.
- User/profile basics.
- Vehicle/listing lifecycle.
- Availability generation, public/host availability, block/unblock.
- Booking core: create booking, customer cancellation across `HELD` / `PENDING_HOST_APPROVAL` / `CONFIRMED` (pre-pickup) paths, patch location, idempotency, customer overlap, availability locking.
- Payment baseline: authorize/capture/void/refund flows, provider routing, reconciliation state.
- Phase 9 baseline slices: files metadata, trip lifecycle, reviews, disputes, reports, outbox publisher, CI/observability.

=> Current practical state: **Phase 1–9 baseline implemented; current work is hardening, contract cleanup, and release evidence.**

### Frontend

Frontend đã tồn tại trong `frontend/` với:

- Next.js + React + TypeScript.
- Auth BFF `/api/auth/*`.
- `AuthProvider` + `api-client.ts`.
- Listings/host/bookings UI.
- Public listing, host vehicle/listing/availability, booking-create, profile và host dashboard flows đã nối API.

### Recent hardening completed

- Critical refactor batch `C01-C07`: idempotency/auth hardening, production frontend API wiring, docs/code drift cleanup.
- Important cleanup batch: not-found handling, SecurityContext annotation, vehicle archive update, phone validation, ADMIN role rejection, optimistic lock handling.
- Security hardening batch: Swagger disabled in prod, explicit CORS origins, typed JWT auth errors, unauthenticated requests return 401 while wrong-role requests return 403.
- Rate-limit contract hardening `8B.2`: public GET endpoints now have test evidence for `429 + Retry-After + {code,message,correlationId}` and path normalization edge cases.
- Rate-limit integration hardening `8B.1R`: login and booking throttling now have integration evidence for `429 + Retry-After + RATE_LIMIT_EXCEEDED`.
- Phase 7 hardening updates: audit detail sanitization and listing approve/reject outbox emission are now implemented with test evidence.
- Cancellation reliability update `7.2R`: void-retry metadata persistence and idempotent 202 replay behavior are covered by integration tests.
- Phase 9 foundation `9.1-9.5`: files metadata + signed URL, trip lifecycle, reviews, disputes, reporting/payout baseline are implemented with unit/WebMvc evidence and integration tests in place.
- Outbox publisher `9.6`: scheduler + retry/backoff/max-attempt persistence has been implemented with unit coverage and integration evidence for retry progression/idempotent send behavior.
- CI/observability baseline `9.7`: GitHub Actions CI (`unit/package` + `integration profile`) and actuator metrics/prometheus exposure with secured access are now wired.
- Release stabilization `9.8`: CoreBank external-order query param assertion is now semantic (decoded key/value), and `mvn clean verify` gate is green.
- Frontend auth hardening `FE-AUTH-2`: `api-client` runtime ownership is now instance-first via `AuthProvider`, BFF refresh re-syncs `rentflow_role` via `/users/me`, and the Next.js proxy treats missing/empty role cookie as an invalid session.
- Email verification enforcement `AUTH-VERIFY-3`: booking creation and payment authorization now reject unverified accounts with `403 EMAIL_NOT_VERIFIED`, and frontend maps that state to profile/resend-verification UX.
- Frontend UX/integration hardening: mobile nav drawer, Vietnamese-first copy, real listing/profile API wiring, and local startup preflight/one-command backend scripts are now in place.
- Listing suspension metadata hardening: admin and vehicle-driven listing suspensions now expose persisted reason/source guidance for host/admin review.
- Transaction correctness hardening `TX-HARDEN-1`: host reject, trip checkout capture, void retry, host-approval expiry, and booking cancellation now use `prepare -> provider call outside TX -> finalize` with finalize-time revalidation and `PAYMENT_FINALIZATION_UNSAFE` evidence on drift.
- Cancellation release-correctness gap is closed for `BookingService.cancelBooking()`: CoreBank capture/void work is outside DB transactions, partial-penalty capture finalizes before void, and drift/void-retry behavior is covered by integration tests.
- Integration gate recovery `TX-HARDEN-1A`: `BookingMapper` bean wiring was normalized to a single runtime constructor, restoring Spring app-context boot for booking/trip integration tests and unblocking full `mvn test` gate.
- Rental Experience Layer Phase 1: trip condition reports, trip photos, damage items, and customer check-in/check-out pages are implemented. Existing trip lifecycle now requires matching condition reports before `CONFIRMED -> IN_PROGRESS` and `IN_PROGRESS -> COMPLETED`. Lower-priority rental experience phases are documented as deferred slices in `docs/rental-experience-layer-plan.md`.

### Docs/code drift

- README/roadmap cũ nói Phase 1, nhưng code đã có SecurityConfig/AuthService/BookingService/frontend.
- Roadmap cũ chưa có refactor/hardening track.
- Java version cần đồng bộ giữa `pom.xml`, README và SRS.

### Open Audit Table

| Issue | Category | Severity | Evidence | Status | Suggested phase |
|---|---|---:|---|---|---|
| Docs/code drift | Docs | Critical | README/roadmap cũ nói Phase 1 nhưng code có auth/booking/frontend | Confirmed | Immediate |
| JWT key rotation/JWK | Security | Nice | Chỉ thấy HS/JWT config, chưa thấy multi-key/JWK | Spec-only | Future |
| Kafka/outbox publisher | Backend/Ops | Nice | SRS/roadmap có nhưng chưa cần trước DB outbox | Spec-only | Phase 9 |
| Full i18n | UX | Nice | Cần sau khi UI text được thống nhất | Spec-only | Later |

---

## 1. Remaining Work Before New Features

### Closed Critical Refactors (Release Evidence)

#### C1. Idempotency failure handling

**Status**: Done in code; kept here only as release-evidence reference.

**Problem**: `fail()` chưa dùng transaction riêng; `BookingService` đã có TODO nhưng chưa đóng.

**Evidence**:

- `src/main/java/com/rentflow/common/idempotency/service/IdempotencyService.java`
- `src/main/java/com/rentflow/booking/service/BookingService.java`

**Risk**: outer transaction rollback kéo theo failure marker rollback; retry behavior có thể sai ở edge case.

**Fix direction**:

- Thêm method/service `markFailedRequiresNew(...)` dùng `Propagation.REQUIRES_NEW`.
- Gọi marker trong catch của create/cancel booking.
- Giữ replay COMPLETED và conflict different body.

**Test/verification**:

- Simulate business exception sau khi idempotency row được tạo.
- Verify row vẫn được mark FAILED dù business transaction rollback.

**Definition of Done**:

- Không còn TODO `REQUIRES_NEW failure marker`.
- Test idempotency failure/retry pass.

### Active Remaining Gaps

#### C2. Auth security baseline

**Status**: Done in code; remaining auth work has moved to future enhancements such as key rotation/MFA rather than baseline security gaps.

**Problem**: auth thiếu một số hardening tối thiểu.

**Evidence**:

- `AuthService.login()` timing risk.
- `RefreshTokenService.findActiveByToken()` chưa detect reuse revoked token.
- Register set email unverified nhưng login không enforce.
- Không có forgot/change/reset password flow.

**Risk**: brute force/timing enumeration, không detect stolen refresh token, UX/support gap.

**Fix direction**:

- Dummy BCrypt for unknown email.
- Redis rate limit login + booking create.
- Refresh token reuse detection + revoke active tokens for user/session family.
- Forgot/reset/change password minimal flow.
- Email verification gate for booking/payment is now enforced; unverified users receive `403 EMAIL_NOT_VERIFIED`.

**Test/verification**:

- Login wrong email/wrong password timing roughly comparable.
- Reuse old refresh token revokes active session.
- Password change revokes old refresh tokens.

**Definition of Done**:

- Auth hardening tests pass.
- README documents current auth limitations.

### C4. Align docs with code

**Problem**: docs say Phase 1, code is already beyond Phase 1.

**Evidence**:

- Old roadmap/README vs code modules.

**Risk**: AI tools and reviewers misunderstand project maturity.

**Fix direction**:

- Update README current state.
- Keep this roadmap as source of truth.
- Optionally create `docs/technical-debt.md` if backlog grows.

**Definition of Done**:

- README, roadmap, SRS do not contradict current code.

---

## 2. Backend Hardening Roadmap

### Auth/security

- Future security enhancements: JWT key rotation/JWK, MFA/2FA, OAuth/social login.
- Current baseline already includes constant-time login behavior, login/booking rate limiting, refresh token reuse detection, forgot/reset/change password, email verification enforcement for booking/payment, Swagger prod gating, CORS origin validation, and typed JWT auth errors.

### Idempotency

- `REQUIRES_NEW` failure marker.
- Split idempotency validation/conflict exception or add HTTP status to exception.
- Add idempotency cleanup job later.

### Transaction boundaries and state machine

- Current backend already supports cancel `HELD`, `PENDING_HOST_APPROVAL`, and `CONFIRMED` before pickup with payment void/capture/retry behavior.
- Current frontend customer detail already exposes cancel for those supported states with a coarse pickup-date gate; remaining work is timeline/audit breadth and UX polish rather than cancellation capability.
- Decide whether booking PATCH needs idempotency once timeline/audit exists.

### Exception handling

- Reduce per-entity exception handlers.
- Prefer one `RentFlowException` handler with exception-owned HTTP status.
- Stop parsing DB exception messages by substring where possible.

### Search/availability performance

- Current backend has already converged on native listing search, `generate_series` availability generation, and batch listing archive updates.
- Remaining search/availability work is contract stability and UX refinement, not known backend performance debt.

### Scheduler / audit / timeline / outbox

- Verify HELD expiry job uses bounded batches and `SKIP LOCKED`.
- Add audit/timeline in Phase 7 only for important state changes.
- DB outbox publishing is at-least-once: rows are claimed as `PROCESSING`, dispatched after claim commit, then finalized as `SENT`, `RETRY`, or `FAILED`; consumers must be idempotent before Kafka is considered.
- Rental handover events currently appended through the existing timeline/audit/outbox services:
  - `TRIP_CONDITION_CHECK_IN_SUBMITTED`
  - `TRIP_CONDITION_CHECK_OUT_SUBMITTED`
  - `TRIP_DAMAGE_ITEM_REPORTED`

---

## 3. Frontend Integration Roadmap

### Mock -> real API migration

This migration is complete for the current MVP surface:

- Public listings, host vehicles/listings/availability, bookings, and profile flows are wired to real APIs.
- Production page views no longer depend on `@/mocks/*` imports.
- React Query + feature API modules are the current pattern for server state.

### Auth state

`api-client.ts` now remains feature-facing through the stable `api` export, but runtime auth state ownership is instance-first and supplied by `AuthProvider` through the active client.

### Route protection

- Short term: backend remains authoritative.
- Medium: central route policy table shared by server layouts and `proxy.ts`.
- The Next.js proxy now treats refresh-cookie-without-role-cookie as an invalid session, not authenticated state.
- Avoid fetching sensitive SSR data before role validation.

### Form validation

- Standardize new forms on Zod + React Hook Form.
- Migrate host vehicle form first.
- Keep backend Bean Validation as final authority.

### Loading/error UX

- Add shared `EmptyState`, `FormError`, `PageSkeleton`, `FilterBar`.
- Centralize `ApiError` mapping from backend code to UI behavior.

### BFF vs direct API

Decision for MVP: keep hybrid.

- Auth uses BFF because refresh token cookie is server-only.
- Other APIs can continue using direct `/api/v1` + Bearer access token.
- Revisit full BFF/proxy only when deployment/CORS becomes painful.

### UX consistency

- Vietnamese primary UI for now.
- Keep remaining copy review incremental; large-scale i18n remains deferred.
- Do not add full i18n framework yet.

---

## 4. Feature Completion Roadmap

### Phase 1 — Foundation

**Status**: Implemented; docs need update.

Exit criteria:

- App starts.
- Swagger works in dev/local.
- Docker infra starts.
- README reflects real current phase.

### Phase 2 — Auth + User

**Status**: Implemented with baseline hardening complete.

Remaining:

- JWT key rotation/JWK if production key management requirements expand.
- MFA/SSO/OAuth only after current auth model is considered stable enough to broaden.

### Phase 3 — Vehicle + Listing

**Status**: Implemented enough for P0; remaining work is feature expansion rather than known baseline cleanup.

Remaining:

- Verify state machine tests.
- Normalize response/pagination where needed.
- Extend admin/host flows only when product scope requires new behavior.

### Phase 4 — Search + Availability

**Status**: Backend exists and public listings frontend is wired; remaining work is doc cleanup and UX refinement.

Remaining:

- Keep public search/detail frontend aligned with current API contract.

### Phase 5 — Booking Core

**Status**: Core exists; needs hardening.

Remaining:

- Verify concurrent booking test.
- Keep cancellation docs/frontend exposure aligned with the backend contract that already covers `PENDING_HOST_APPROVAL` and `CONFIRMED`.

### Phase 6 — Payment Stub

Do after Critical Refactor.

Scope:

- `booking_payments`.
- `payment_transactions`.
- authorize/void/capture/refund stub.
- payment idempotency.
- TX-02 availability locking.

### Phase 7 — Cancellation + Audit + Timeline

Scope:

- Cancellation policy calculator.
- Extend timeline/audit/UX around the cancellation flows that already exist in backend code.
- Void/refund/capture penalty behavior.
- Booking timeline.
- Audit logs.
- Minimal outbox event creation if useful.

### Phase 8 — Driver Verification + Hardening

Merge old 8A/8B.

Scope:

- Driver license submission.
- Admin approve/reject.
- Daily expiry job.
- Booking gate.
- Sensitive-data filtering.
- Notifications DB.
- Rate limits.

### Phase 9 — P2 Extensions

Status: core slices `9.1` through `9.7` are implemented in the current codebase; remaining work is provider-grade hardening and release validation.

Scope:

- Files/listing photos.
- MinIO signed URLs.
- Trip check-in/check-out.
- Reviews.
- Disputes.
- Reports.
- Payouts.
- Outbox scheduler/Kafka optional.
- CI polish.

---

## 5. Portfolio Demo Priority

1. Auth flow: register/login/refresh/protected endpoint.
2. Listing approval: host creates vehicle/listing, admin approves, availability generated.
3. Search: active listings + date availability filter.
4. Create booking: Idempotency-Key, HELD booking, HOLD availability, snapshot price/policy.
5. Concurrent booking test: 10 requests -> exactly 1 success.
6. Idempotency replay: same key/body returns same response; same key/different body conflicts.
7. Payment authorization stub: HELD -> CONFIRMED, HOLD -> BOOKED.
8. Cancellation policy: HELD and payment-backed cancellation paths already exist; demo focus is validating confirmed/pending flows and provider retry handling.

---

## 6. Do Not Build Yet

Avoid these until Phase 5 hardening + basic frontend API migration are stable:

- Real payment gateway.
- Kafka before DB outbox.
- Microservices split.
- Full event sourcing.
- OAuth/social login before auth baseline is stable.
- MFA/SSO/SAML.
- Complex admin analytics.
- Payout automation.
- Surge pricing/advanced pricing engine.
- Full i18n before Vietnamese copy is consistent.
- Storybook expansion before shared components are stable.

---

## Working Rule for Technical Reviews

For future technical reviews:

1. Require file path + method/component evidence.
2. Mark each issue Confirmed/Suspected/Spec-only.
3. Only Confirmed issues enter Critical.
4. Every refactor item needs test/verification.
5. Prefer small testable PRs over giant rewrites.
