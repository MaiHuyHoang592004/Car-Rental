# Release Gate Evidence (2026-06-03)

## Scope under validation

- FE-PROXY-1 Next.js 16 middleware-to-proxy convention migration.
- Frontend route guard behavior after moving from `src/middleware.ts` to `src/proxy.ts`.
- Frontend production build compatibility after removing the deprecated middleware convention.

## Commands executed

1. `cd frontend && pnpm test -- proxy.test.ts`
2. `cd frontend && pnpm test`
3. `cd frontend && pnpm build`

Executed at: `2026-06-03 +07:00`

## Results

- Targeted proxy Vitest: `1` test file, `17` tests passed.
- Frontend Vitest full gate: `31` test files, `160` tests passed.
- Frontend production build: passed on Next.js `16.2.6`.
- The previous middleware-to-proxy deprecation warning is no longer emitted.

## Key regression intents covered

- Public routes such as `/verify-email` and API rewrite paths such as `/api/v1/auth/verify-email` remain unprotected by the frontend route guard.
- Protected customer, host, and admin routes keep the existing role/cookie redirect behavior.
- Next.js production build no longer uses the deprecated `middleware.ts` convention.

---

# Release Gate Evidence (2026-06-02)

## Scope under validation

- TX-HARDEN cancellation release-correctness changes.
- Backend full unit and integration gates after cancellation split-transaction hardening.
- Frontend payment/manual-transfer type/build compatibility.
- Docs alignment after closing the cancellation transaction gap.

## Commands executed

1. `mvn test`
2. `mvn verify -Pintegration-tests`
3. `mvn "-Dtest=AvailabilityIntegrationTest,BookingPhase5IntegrationTest" test`
4. `mvn verify -Pintegration-tests`
5. `cd frontend && pnpm test`
6. `cd frontend && pnpm build`
7. `cd frontend && pnpm test`

Executed at: `2026-06-02 20:18-20:30 +07:00`

## Results

- Backend unit gate: `440` tests passed, `0` failures, `0` errors, `0` skipped.
- Backend integration gate first run: failed due date-dependent test fixtures, not TX-HARDEN production logic.
- Targeted rerun after fixture fix: `26` tests passed, `0` failures.
- Backend integration gate final run: `183` tests passed, `0` failures, `0` errors, `0` skipped.
- Frontend Vitest final run: `31` test files, `160` tests passed.
- Frontend production build: passed.

## Regression fixes made during gate

- `BookingPhase5IntegrationTest` no longer uses a fixed past pickup date once the calendar moves past `2026-06-01`.
- `AvailabilityIntegrationTest` no longer hardcodes a through-date whose expected inserted count changes with the current date.
- Frontend `PaymentStatus` now includes `PENDING_TRANSFER`, matching the manual bank-transfer payment UI state used by `VIETQR_MANUAL`.

## Key regression intents covered

- TX-HARDEN cancellation targeted coverage remains green for provider calls outside DB transactions and drift handling.
- Phase 5 booking create/idempotency/concurrency tests remain stable with future dates.
- Availability extend tests remain stable across calendar dates.
- Payment page builds with the sandbox/manual transfer status contract.

## Limits

- Manual browser smoke was not rerun in this gate session; validation here is automated backend/frontend gate evidence.
- Superseded by the 2026-06-03 FE-PROXY-1 validation session, which migrates the frontend route guard to the Next.js `proxy.ts` convention.

---

# Release Gate Evidence (2026-05-29)

## Branch under validation

- `release-hardening-gate`

## Commands executed

1. `.\mvnw.cmd -q test`
2. `.\mvnw.cmd -q verify -Pintegration-tests`

Executed at: `2026-05-29 15:47:15 +07:00`

## Results

- Surefire summary: `tests=450 failures=0 errors=0 skipped=0`
- Failsafe summary: `completed=155 failures=0 errors=0 skipped=0`
- Integration DB stability: no `FATAL: sorry, too many clients already` observed in this gate run.

## Commit map (stacked PR packaging)

- PR-A (`hardening(security)`): `2f3be84`
- PR-B (`test(integration)`): `e4e7f5a`
- PR-C (`feat(auth)`): `bdc4536`
- PR-D (`feat(api)`): `eb14ee5`

## Key regression intents covered by gate

- Invalid enum filters return `400 VALIDATION_ERROR`.
- Auth/security/rate-limit integration suite still green.
- Booking/payment cancellation policy integration suite still green.
- Containerized integration suite runs without DB connection exhaustion.

---

# Frontend Evidence Addendum (2026-05-30)

## Scope under validation

- Customer payment page: `/bookings/{id}/payment`
- Admin pages: `/admin`, `/admin/listings`, `/admin/listings/{id}`, `/admin/users`
- Frontend production type/build gate after the admin/payment UX slice

## Commands executed

1. `cd frontend && npm test`
2. `cd frontend && npm run build`

Executed at: `2026-05-30 +07:00`

## Results

- Vitest summary: `21` test files, `125` tests passed, `0` failed
- Next.js production build: passed

## Browser smoke coverage

Executed with a real headless browser against local `localhost:3000` using mocked auth/API responses because backend infra was not running in this evidence session.

Validated:

- `/bookings/:id/payment` renders bank selection and reaches authorized state
- `/admin` renders dashboard
- `/admin/listings` renders queue
- `/admin/listings/:id` renders detail and approve flow remains healthy
- `/admin/users` renders filtered user table

## Limits of this addendum

- No backend Maven tests were rerun in this session
- No full end-to-end backend-integrated browser smoke was possible because local Docker/PostgreSQL/Redis/backend were unavailable
