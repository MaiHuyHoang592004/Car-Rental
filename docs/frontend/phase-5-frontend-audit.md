# RentFlow Frontend Audit (Phase 5)

> Historical / audit snapshot only. Tài liệu này mô tả assumptions ở thời điểm 2026-05-12 và không còn là source-of-truth current-state cho frontend hiện tại.

Date: 2026-05-12

## Scope Baseline

- Frontend app does not exist yet.
- Backend is Spring Boot and remains the source of API truth.
- Visual reference comes from archived design notes.
- Production UI must be implemented as React components; do not paste raw exported HTML.

## Canonical Inputs

- API contract: `docs/api-contracts.md`
- Design tokens and UI style: archived design reference notes.
- Route/state inventory: archived screen inventory notes.
- Frontend product specs: consolidated into current route and API contract docs.

## Locked Implementation Decisions (historical)

- Frontend location: `frontend/`
- Stack:
  - Next.js App Router
  - TypeScript
  - Tailwind CSS
  - shadcn/ui-compatible components
  - pnpm
- Static UI with mock data first.
- API integration only after static UI is complete and validated.
- Generated API client goes in `frontend/src/generated/` and is never edited by hand.

## Active Route Surface (Phase 5)

- Public/auth: `/`, `/login`, `/register`, `/listings`, `/listings/[id]`
- Customer: `/listings/[id]/book`, `/me/profile`, `/me/bookings`, `/bookings/[id]`
- Host: `/host` (redirect), `/host/dashboard`, `/host/vehicles`, `/host/vehicles/new`, `/host/vehicles/[id]`, `/host/listings`, `/host/listings/new`, `/host/listings/[id]`, `/host/listings/[id]/availability`
- Admin: `/admin`, `/admin/listings`, `/admin/listings/[id]`, `/admin/users`
- Global: `not-found.tsx`, route `error.tsx`, `/forbidden`, loading/skeleton/empty/validation/access-denied states

## Explicit Non-Goals

- Payment routes and payment lifecycle
- Full driver verification flow
- Notifications
- Audit logs
- Reports/revenue analytics
- File/photo upload management
- Reviews/disputes
- Trip lifecycle
- Host booking approval/rejection flow

## Backend Verification Snapshot

- Base URL: `/api/v1`
- OpenAPI endpoint: `/api-docs`
- Local backend port profile: `8087`
- Public listing availability surface differs by role:
  - Public: `FREE`, `BLOCKED`, `UNAVAILABLE`
  - Host: `FREE`, `HOLD`, `BOOKED`, `BLOCKED`
- Idempotency key required for:
  - `POST /api/v1/bookings`
  - `POST /api/v1/bookings/{id}/cancel`

## Known Gaps / Risks

- `DESIGN.md` at repo root is absent; design source is kept in archived design notes.
- `docs/frontend/api-contracts.md` is absent; canonical API source is `docs/api-contracts.md`.
- Historical design inventory includes some routes without dedicated exported HTML (for example `/me/bookings`, `/host/listings`, `/admin/users`).
- Historical design references contain future/off-scope screens that must remain inactive.
- No dedicated CORS config was found in backend Java config; frontend should use Next proxy/rewrite for local integration.
