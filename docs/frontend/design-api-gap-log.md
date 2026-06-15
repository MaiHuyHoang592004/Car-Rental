# RentFlow Design/API Gap Log

## Resolved Baseline Decisions

- Use the design reference as visual guidance only.
- Use `docs/api-contracts.md` as canonical API contract.
- Build static screens first, then API integration.

## Design Source Gaps

1. Missing root `DESIGN.md`
- Expected by some planning notes, but file does not exist at repo root.
- Replacement source: archived design reference notes.

2. Incomplete route-to-screen coverage in the design reference
- Route inventory includes `/me/bookings`, `/host/listings`, `/admin/users`, and `403`.
- No dedicated HTML export exists for some of these pages.
- Resolution: compose pages from component kit + adjacent screens + requirements docs.

3. Mixed visual language in historical screen references
- Some pages use different icon sets and spacing styles.
- Resolution: normalize to one internal design system from `design-system.md` and shared components.

## API Contract Gaps

1. Missing `docs/frontend/api-contracts.md`
- Resolution: `docs/api-contracts.md` is canonical.

2. Availability status mismatch by audience
- Public availability returns `FREE`, `BLOCKED`, `UNAVAILABLE`.
- Host availability uses `FREE`, `HOLD`, `BOOKED`, `BLOCKED`.
- Resolution: separate UI status adapters.

3. Pagination shape inconsistency
- Some endpoints use custom `PageResponse<T>`.
- Some endpoints return Spring `Page<T>`.
- Resolution: shared pagination normalization layer before route-level usage.

4. Booking status expectations vs current backend
- Backend currently centers booking lifecycle on `HELD` and limited follow-up states.
- Resolution: render unsupported statuses as read-only placeholders in Phase 5 UI.

## Integration Risks

1. CORS not explicitly configured in backend Java config
- Resolution: use Next rewrite/proxy for local integration and isolate frontend work from backend edits.

2. Generated client discipline
- Orval output must remain machine-owned.
- Resolution: place generated files under `frontend/src/generated/` and wrap behavior in non-generated adapter modules.
