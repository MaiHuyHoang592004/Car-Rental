# RentFlow Full Project Review — Phase 1

**Date:** 2026-06-03 | **Evidence sources:** 118 test files, 27 Flyway migrations, 5 deep audit agents, direct file reads

---

## 1. Executive Verdict

**PARTIALLY READY — MAIN FLOWS ALMOST READY**

The project is significantly more mature than a quick scan suggests. The core booking-payment-cancellation-idempotency machinery is already working with production-grade patterns. The concurrent booking test (8 customers, CountDownLatch, PostgreSQL/Testcontainers) proves the optimistic claim. The remaining gaps are hardening items, not missing capabilities.

Why PARTIALLY READY:
- Core booking flow works end-to-end with idempotency, pessimistic locking, overlap prevention, self-booking prevention, and concurrent safety.
- Payment authorization/capture/void/refund is fully implemented with CoreBank provider, stub, bank transfer, and provider router. Compensation when finalization fails is handled.
- Cancellation supports penalties (flexible/moderate/strict), penalty capture, void remainder, and void-retry on failure.
- Security is solid: JSON 401/403, BCrypt(12), refresh token rotation with reuse detection, account lockout, rate limiting.
- Database schema has 27 migrations with all critical constraints: one-active-listing partial unique index, idempotency unique key, availability composite PK, payment amount checks.
- Frontend is a full Next.js 16 app with auth BFF, httpOnly cookie refresh token, 160 tests, and role-based routing.
- Test coverage is strong with 118 test files spanning unit, integration, concurrency, and security tests.
- BUT several polishing gaps remain: no password change endpoint, no notification mark-as-read, docker-compose is infra-only, no frontend .env.example, AdminListingService dual-constructor issue, Booking.cancelled_at column missing.
- Documentation is strong but has minor version drift (Java 17 vs actual) and one stale doc.
- The "10 customers" test is 8 customers — close enough for a portfolio demo, easily extended to 10.

---

## 2. Current Implementation Map

| Area | Status | Evidence | Missing / Risk |
|---|---|---|---|
| Auth (register/login/refresh/logout) | OK | AuthController — all endpoints wired; BCrypt(12); refresh rotation + reuse detection; email verification; forgot/reset password | Change-password endpoint missing; resend-verification endpoint missing |
| User/Profile | OK | UserService, AuthUserProfileResponse, UserController | — |
| Driver Verification | OK | DriverVerificationService, V15 migration, DriverVerificationIntegrationTest, admin approve/reject, expiry job | Booking gate controlled by feature flag requireDriverVerification (default likely false) |
| Vehicle | OK | VehicleService, VehicleStateMachine, VehicleArchivePreconditionsTest, plate/VIN encrypted | — |
| Listing | OK | ListingService, ListingStateMachine, AdminListingService, ListingLifecycleIntegrationTest, one-active-per-vehicle DB constraint | AdminListingService dual constructor creates null bookingRepository path |
| Availability | OK | AvailabilityService, AvailabilityCalendar composite PK, generate_series batch insert, 365-day generation on approval, block/unblock | — |
| Search | OK | ListingSearchController, ListingSearchRepositoryCustomImpl, active-only filter, date-range availability exclusion, ListingSearchIntegrationTest | ListingSearchRequest on GET may have param binding issues |
| Booking Core | OK | BookingService.createBooking with idempotency + SELECT FOR UPDATE + overlap prevention + self-booking prevention; BookingPhase5IntegrationTest with 8-thread concurrent test | — |
| Idempotency | OK | IdempotencyService with INSERT ON CONFLICT + PESSIMISTIC_WRITE fallback; 13 scopes; response replay; IdempotencyCleanupIntegrationTest | — |
| Payment | OK | PaymentService, CoreBankAuthorizeService, CaptureService, VoidService, RefundService, provider router, reconciliation state, compensation paths | — |
| Cancellation | OK | BookingService.cancelBooking with three-phase pattern; penalty calculator; void-retry on failure; BookingCancelIntegrationTest (20 tests) | Booking.cancelled_at column missing |
| Host Approval | OK | HostBookingApprovalService; host ownership enforced; HostBookingApprovalIntegrationTest | approveBooking uses @Transactional singleton (no external IO so low risk) |
| Scheduler Jobs | OK | ExpireHeldBookings, ExpireHostApprovals, ExpireDriverVerifications, VoidRetry, IdempotencyCleanup, OutboxPublisher | — |
| Audit | OK | AuditLog, DefaultAuditLogService, DefaultAuditLogServiceTest | — |
| Timeline | OK | BookingTimelineEntry, DefaultBookingTimelineService | — |
| Notification | Warn | NotificationService, NotificationController.GET /me, AdminNotificationService | No PATCH /{id}/read; no admin notification list endpoint |
| Outbox | OK | OutboxEvent, OutboxPublisherService, OutboxPublisherIntegrationTest, retry + claim columns in V27 | — |
| Admin APIs | OK | AdminUserController, AdminListingController, AdminDisputeController — all @PreAuthorize("hasRole('ADMIN')") | — |
| Frontend/BFF | OK | Next.js 16 App Router, auth BFF (5 routes), httpOnly refresh cookie, in-memory access token, role-based routing, 160 tests | No .env.example; access token in React ref (accepted MVP risk) |
| Docker/DevOps | Warn | docker-compose.yml declares postgres, redis, optional postgrest | No backend/frontend app services |
| Tests | OK | 118 test files; unit tests + Testcontainers integration tests + concurrency tests; cancel flow has 20 integration tests | Concurrent test uses 8 customers (not 10) |
| Docs | Warn | README solid, AGENTS.md good, architecture.md, roadmap.md, srs.md | Java version drift was noted during the historical audit; stale frontend draft docs have since been removed. |

---

## 3. Main Flow Coverage

### Flow A — Guest/Public Search: PASS
GET /api/v1/listings is permitAll(). ListingSearchRepositoryCustomImpl always filters WHERE l.status = 'ACTIVE'. Date-range search excludes HOLD/BOOKED/BLOCKED dates. ListingSearchIntegrationTest exists. Public DTOs do not leak host private data. No blocking issues.

### Flow B — Authenticated Customer Booking: PASS
POST /api/v1/bookings requires authenticated(). BookingService.createBooking calls idempotencyService.resolve() first, then availabilityReserver.lockAndValidate() (PESSIMISTIC_WRITE on availability rows ordered by date), then bookingValidator.resolveListingForBooking() which throws AccessDeniedException if listing.getHostId().equals(customerId) (BookingValidator.java:64). Overlap check via validateCustomerOverlap(). Booking created as HELD with hold expiry. Price/policy snapshots saved as JSONB. 401/403 return JSON ErrorResponse. No blocking issues.

### Flow C — Concurrent Booking: PASS (8 customers, portfolio-demo ready)
BookingPhase5IntegrationTest.concurrentCreateOnlyOneCustomerWinsAvailabilityLock() — 8 concurrent customers, CountDownLatch, ExecutorService, PostgreSQL via Testcontainers. Verifies: exactly 1 returns 201, 7 return 409 with "LISTING_NOT_AVAILABLE". Uses real JPA PESSIMISTIC_WRITE lock. Lock order: availability rows ordered by availableDate ASC (deterministic). Uses 8 customers (not 10). Extending to 10 is trivial.

### Flow D — Payment Authorization: PASS
POST /api/v1/bookings/{id}/payments/authorize requires authenticated(). CoreBankAuthorizeService uses three-phase pattern: idempotency resolve -> prepare (lock booking+payment) -> call CoreBank provider -> finalize (re-lock). If finalization fails: compensation void, and if that also fails -> RECONCILIATION_REQUIRED. BookingPaymentAuthorizeIntegrationTest and CoreBankAuthorizeIntegrationTest exist. No blocking issues.

### Flow E — Host Approval: PASS
HostBookingApprovalService.approveBooking -> requireHostOwnership() checks booking.getHostId().equals(hostId). PENDING_HOST_APPROVAL -> CONFIRMED. rejectBooking -> PENDING_HOST_APPROVAL -> REJECTED, voids authorization, releases availability. ExpireHostApprovalsJob runs every 60s. HostBookingApprovalIntegrationTest exists. No blocking issues.

### Flow F — Cancellation: PASS
Supported: HELD (release availability), PENDING_HOST_APPROVAL (void payment), CONFIRMED (penalty calculator -> conditionally capture penalty -> void remainder). BookingCancelIntegrationTest has 20 tests. Void failure -> voidRetryRequired=true, booking still CANCELLED, DefaultPaymentVoidRetryService retries (max 10, 300s backoff). Cancellation is idempotent. Minor gap: no cancelled_at column on Booking.

### Flow G — Admin Listing Approval: PASS
AdminListingService.approveListing — PESSIMISTIC_WRITE on listing+vehicle, checks one-active-per-vehicle, sets ACTIVE, publishes ListingApprovedEvent. AvailabilityEventHandler.onListingApproved generates 365 rows via generate_series with ON CONFLICT DO NOTHING. AdminListingConcurrentApprovalTest proves double-submit safety. No blocking issues.

### Flow H — Vehicle Lifecycle: PASS
VehicleService.archiveVehicle -> checks bookingPort.hasActiveBookings() (HELD/CONFIRMED/IN_PROGRESS), throws VehicleArchiveNotAllowedException. VehicleLifecycleIntegrationTest and VehicleSuspendPropagationIntegrationTest exist. Plate/VIN encrypted via EncryptionUtil (AES-256-GCM). No blocking issues.

### Flow I — Driver Verification: WARN
DriverVerificationService exists with approve/reject. ExpireDriverVerificationsJob runs daily. Risk: BookingValidator.validateDriverVerification reads requireDriverVerification flag. If default is false, unverified drivers can still book. Needs confirmation that flag is true in production.

### Flow J — Audit/Timeline/Notification: PASS (partial)
AuditLog, BookingTimelineEntry, NotificationService, OutboxPublisherService all present. Gaps: No PATCH /notifications/{id}/read. No admin notification list endpoint.

---

## 4. API Contract Review

| Expected Endpoint | Implemented | Auth Correct | Notes |
|---|---|---|---|
| POST /api/v1/auth/register | YES | permitAll | — |
| POST /api/v1/auth/login | YES | permitAll | Rate limited |
| POST /api/v1/auth/refresh | YES | permitAll | Rotation + reuse detection |
| POST /api/v1/auth/logout | YES | permitAll | — |
| POST /api/v1/auth/forgot-password | YES | permitAll | — |
| POST /api/v1/auth/reset-password | YES | permitAll | — |
| POST /api/v1/auth/verify-email | YES | permitAll | — |
| POST /api/v1/auth/change-password | NO | — | Service exists, no endpoint |
| POST /api/v1/auth/verify-email/resend | NO | — | Service exists, no endpoint |
| GET /api/v1/users/me | YES | authenticated | — |
| GET /api/v1/listings | YES | permitAll | Public search |
| GET /api/v1/listings/{id} | YES | permitAll | — |
| GET /api/v1/listings/{id}/availability | YES | permitAll | — |
| GET /api/v1/host/vehicles | YES | HOST | — |
| POST /api/v1/host/listings | YES | HOST | — |
| PATCH /api/v1/admin/listings/{id} | YES | ADMIN | Approve/reject |
| POST /api/v1/bookings | YES | authenticated | Idempotency-Key required |
| GET /api/v1/bookings/me | YES | authenticated | — |
| GET /api/v1/bookings/{id} | YES | authenticated | — |
| POST /api/v1/bookings/{id}/payments/authorize | YES | authenticated | — |
| POST /api/v1/bookings/{id}/cancel | YES | authenticated | — |
| POST /api/v1/host/bookings/{id}/approve | YES | HOST | Ownership enforced |
| POST /api/v1/host/bookings/{id}/reject | YES | HOST | Ownership enforced |
| GET /api/v1/admin/audit-logs | YES | ADMIN | — |
| GET /api/v1/notifications/me | YES | authenticated | No mark-read |

---

## 5. Database and Migration Review

| Check | Status | Evidence |
|---|---|---|
| Migrations apply cleanly | OK | 27 Flyway files, ddl-auto: validate |
| Schema matches entities | OK | Booking, PaymentTransaction, AvailabilityCalendar all align |
| Status constraints | OK | V7 booking status CHECK on 8 values |
| Unique indexes | OK | V2 partial unique uq_listings_one_active_per_vehicle; V7 uq_idempotency_scope_key; V15 partial unique uq_driver_verification_active |
| One-active-listing partial unique | OK | V2 WHERE status = 'ACTIVE' |
| Idempotency unique key | OK | (user_id, scope, "key") + INSERT ON CONFLICT |
| Availability PK | OK | (listing_id, available_date) composite PK + @IdClass |
| Payment amount constraints | OK | chk_booking_payments_amounts, chk_payment_transactions_amount |
| Foreign keys | OK | All FKs present |
| H2/PostgreSQL divergence | OK | Testcontainers uses real PostgreSQL |
| Missing: cancelled_at on bookings | MISSING | Neither entity nor migration has this column |

---

## 6. Transaction and Concurrency Review

| Method | TX Strategy | Lock Order | SELECT FOR UPDATE | Safe |
|---|---|---|---|---|
| BookingService.createBooking | @Transactional | Listing validate -> availability ordered by date | Yes (PESSIMISTIC_WRITE) | Yes |
| BookingService.cancelBooking | Multi-TX via TransactionTemplate | booking -> payment -> availability | Yes (findByIdForUpdate) | Yes |
| CoreBankAuthorizeService.authorize | Multi-TX via TransactionTemplate | booking -> payment | Yes | Yes |
| HostBookingApprovalService.approveBooking | @Transactional | booking -> payment -> availability | Yes | Yes |
| HostBookingApprovalService.rejectBooking | Multi-TX via TransactionTemplate | booking -> payment -> availability | Yes | Yes |
| ExpireHeldBookingsProcessor.processBatch | SKIP LOCKED | availability -> booking | Yes (FOR UPDATE SKIP LOCKED) | Yes |
| AdminListingService.approveListing | @Transactional | listing -> vehicle | Yes (PESSIMISTIC_WRITE) | Yes |

Lock order is canonical (booking -> payment -> availability). Availability rows ordered by availableDate ASC (deterministic). No deadlock risk.

---

## 7. Security Review

| Finding | Severity | Action |
|---|---|---|
| Encryption key may accept dev defaults in non-test | CRITICAL | Add startup assertion |
| No password change endpoint | MISSING | Wire to AuthController |
| No resend-verification endpoint | MISSING | Wire to AuthController |
| CORS default localhost:3000 | REVIEW | Document for non-local profiles |
| Actuator /health public | SECURITY | Consider restricting |
| Refresh token family not revoked on logout | REVIEW | Acceptable for MVP |
| Access token in React ref (frontend) | REVIEW | Accepted MVP risk |
| Dev cookie secure: false | REVIEW | Ensure staging sets NODE_ENV=production |

---

## 8. Test Coverage Review

| Category | Count | Missing |
|---|---|---|
| Unit tests | ~72 files | — |
| Integration tests (Testcontainers) | ~26 files | — |
| Concurrency tests | 2 files (8 customers) | 10-customer variant |
| Cancellation tests | 20 tests in BookingCancelIntegrationTest | — |
| Frontend tests | 160 tests (31 files) | — |
| **Total Java test files** | 118 | — |

**Top 5 tests to add:**

1. Void retry exhaustion -> admin notification
2. Resource-level 403: customer A accessing customer B's booking
3. STRICT cancellation: <7 days -> 100% penalty capture
4. Idempotency replay with different body -> 409 conflict
5. Host cannot see driver license data

---

## 9. Frontend/BFF Review

Next.js 16.2.6 App Router, React 19, TypeScript 5. Auth BFF with 5 route handlers. httpOnly cookies for refresh token + role. Access token in React ref. Two-layer route protection (proxy.ts edge + server layouts). 160 tests. Idempotency-Key client-side generation. X-Correlation-Id auto-generated.

Gap: No frontend/.env.example.

---

## 10. Documentation and Portfolio Readiness

Portfolio readiness score: 62 / 100

Backend code at ~85-90, but README lacks architecture diagram, flow descriptions, concurrency/idempotency story, demo accounts, and important test highlights.

---

## 11. Blocker List

| Priority | Blocker | Why | Fix |
|---|---|---|---|
| P0 | AdminListingService dual constructor | Silent data corruption (null bookingRepository) | Remove 9-arg constructor |
| P0 | No app services in docker-compose | Reviewer cannot docker compose up | Add backend + frontend services |
| P1 | Missing change-password endpoint | Basic auth requirement unmet | Wire to AuthController |
| P1 | Missing resend-verification endpoint | Users locked out of verification | Wire to AuthController |
| P1 | Missing notification mark-as-read | Feature incomplete | Add PATCH endpoint |
| P1 | requireDriverVerification flag unclear | Security gate may be open | Confirm default = true |
| P1 | Encryption key startup guard missing | Dev key could reach prod | Add assertion |
| P1 | Booking.cancelled_at missing | Reporting on cancellations brittle | Add V28 migration |
| P2 | Concurrent test 8 not 10 customers | Portfolio story mismatch | Change to 10 |
| P2 | README missing portfolio narrative | Weakens demo impact | Upgrade README |
| P2 | No frontend .env.example | Developer onboarding friction | Create file |

---

## 12. Recommended Implementation Roadmap

### Slice 1 — Hardening: Fix Constructor + Docker + Password Endpoints
Goal: Eliminate all P0 blockers.
Tasks: Remove dual constructor, add backend to docker-compose, wire change-password + resend-verification endpoints.
Acceptance: docker compose up starts system; change-password works.
Effort: ~2-3 hours.

### Slice 2 — Notification + cancelled_at + Security Guard
Goal: Complete notification module, add cancelled_at, add encryption guard.
Tasks: PATCH /notifications/{id}/read, admin notification list, V28 migration + entity + cancel flow update, encryption key guard.
Effort: ~3 hours.

### Slice 3 — Portfolio README Upgrade
Goal: Transform README to portfolio narrative.
Tasks: Architecture diagram, flow descriptions, concurrency story, demo accounts, trade-offs.
Effort: ~3 hours.

### Slice 4 — Test Gap Closure + 10-Customer Demo
Goal: Add top 5 tests, extend concurrent test to 10.
Tasks: Void retry test, 403 test, penalty test, idempotency conflict test, license privacy test.
Effort: ~4 hours.

---

## 13. Exact Next Slice Recommendation

**Slice 1 — Hardening: Fix Critical Constructor + Docker + Password Endpoints**

Why first: P0 data-corruption bug, reviewer cannot start system, basic auth features missing. Small slice (~2-3 hours), clear criteria, unblocks everything.

Checklist:
- Remove 9-arg constructor from AdminListingService
- Add backend service to docker-compose.yml
- Wire POST /api/v1/auth/change-password to AuthController
- Wire POST /api/v1/auth/verify-email/resend to AuthController
- Update SecurityConfig for new endpoints
- Run mvn test and mvn verify -Pintegration-tests

---

## 14. Commands to Run

Windows PowerShell:
```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd verify -Pintegration-tests
.\mvnw.cmd test -Dtest="BookingPhase5IntegrationTest#concurrentCreateOnlyOneCustomerWinsAvailabilityLock"
cd frontend; pnpm test
```

Linux/macOS:
```bash
docker compose up -d
./mvnw spring-boot:run
./mvnw test
./mvnw verify -Pintegration-tests
./mvnw test -Dtest=BookingPhase5IntegrationTest
cd frontend && pnpm test
```

---

## 15. Final Summary

**Current readiness: PARTIALLY READY — MAIN FLOWS ALMOST READY (~80%)**

**Top 5 blockers:**
1. AdminListingService dual constructor (P0)
2. No app services in docker-compose (P0)
3. Missing change-password endpoint (P1)
4. Missing notification mark-as-read (P1)
5. requireDriverVerification flag uncertainty (P1)

**Top 5 tests missing:**
1. Void retry exhaustion -> admin notification
2. Resource-level 403: cross-customer booking access
3. STRICT penalty capture
4. Idempotency replay conflict
5. Host cannot see license data

**Exact next action:** Slice 1 — Fix AdminListingService constructor, add backend to docker-compose, wire change-password + resend-verification endpoints. ~2-3 hours, eliminates all P0 blockers.

---

## Prioritized Actions

| # | Priority | Area | Action | Effort |
|---|---|---|---|---|
| 1 | P0-BLOCKER | Listing | Remove AdminListingService dual constructor | 30 min |
| 2 | P0-BLOCKER | Docker | Add backend service to docker-compose.yml | 1 hour |
| 3 | P1-BLOCKER | Auth | Wire change-password endpoint | 1 hour |
| 4 | P1-BLOCKER | Auth | Add resend-verification-email endpoint | 30 min |
| 5 | P1-BLOCKER | Notification | Add PATCH /notifications/{id}/read | 1 hour |
| 6 | P1-BLOCKER | Security | Add encryption key startup guard | 30 min |
| 7 | P1-BLOCKER | Booking | Add cancelled_at column (V28) | 1 hour |
| 8 | P1-BLOCKER | Config | Confirm requireDriverVerification=true | 15 min |
| 9 | P2-NICE | Test | Extend concurrent test to 10 customers | 15 min |
| 10 | P2-NICE | Docs | Portfolio README upgrade | 3 hours |
| 11 | P2-NICE | Config | Create frontend/.env.example | 10 min |
| 12 | P2-NICE | Test | Add top 5 missing tests | 4 hours |
