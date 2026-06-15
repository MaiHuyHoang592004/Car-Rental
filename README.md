# RentFlow

RentFlow is a production-style car rental platform built as a Spring Boot modular monolith with a Next.js web client. The project focuses on the backend problems that matter in a real booking system: authentication, listing approval, availability locking, idempotent booking/payment mutations, auditability, and release-ready deployment.

## Live Demo

| Surface | URL |
|---|---|
| Web app | https://rentflow-web.onrender.com |
| API base | https://rentflow-api-2czk.onrender.com |
| Health check | https://rentflow-api-2czk.onrender.com/api/v1/health |

Render free services can need a short cold start before the first response.

## Portfolio Walkthrough

Use the live web app to review the main product loop:

1. Browse seeded public listings with photos, pricing, cities, ratings, and availability-aware search.
2. Register a customer account or sign in with the demo customer account when demo seed is enabled.
3. Open the notification bell and `/notifications` to review unread badges, support updates, verification reminders, and read/read-all actions.
4. Create a booking from an active listing, then continue through booking detail, payment instruction/authorization, and trip lifecycle screens.
5. Review host-facing flows from a separate host account: vehicle/listing management, availability, dashboard, and payout-related operational states.

Demo customer account for the public Render environment:

```text
Email: demo-customer@rentflow.local
Password: RentFlowDemo!2026
Role: CUSTOMER only
```

No public admin account is seeded.

## Highlights

- JWT auth with refresh-token rotation, logout, role-based access, and resource ownership checks.
- Host vehicle/listing workflow with admin approval before public search exposure.
- Public listing search with filters, pagination, ratings, availability-aware date filtering, and listing photos.
- Booking lifecycle with day-based availability, pessimistic locking, idempotency keys, hold expiry, cancellation, and host approval paths.
- Payment abstraction with CoreBank demo provider, bank-transfer instruction flow, authorization/capture/void/refund state, and reconciliation metadata.
- Rental experience layer: trip check-in/check-out, condition reports, photos, reviews, disputes, notifications, reports, and payout baseline.
- Transactional outbox with scheduled retry support and optional Kafka dispatcher.
- PostgreSQL/Flyway schema ownership, Redis-backed runtime concerns, Testcontainers integration tests, and Render Blueprint deployment.

## Architecture

```text
Next.js Web App
      |
      v
Spring Boot REST API
      |
      +-- PostgreSQL + Flyway
      +-- Redis
      +-- Scheduled jobs
      +-- File metadata / signed URL layer
      +-- Optional Kafka outbox dispatcher
```

The backend is a modular monolith under `com.rentflow`. Business modules own their controllers, services, DTOs, entities, repositories, policies, and events. Shared infrastructure stays in `common`.

Important modules:

| Module | Responsibility |
|---|---|
| `auth`, `user` | Accounts, roles, JWT, refresh tokens, profile and verification state |
| `vehicle`, `listing` | Host fleet, listing lifecycle, admin approval, search projection |
| `availability`, `booking` | Calendar rows, locking, holds, state transitions, idempotency |
| `payment` | Provider routing, authorization/capture/void/refund, reconciliation |
| `trip`, `review`, `dispute` | Rental handover, condition reports, post-trip feedback and support |
| `notification`, `audit`, `outbox`, `report` | Operational events, audit trail, publishing, metrics and reporting |

## Tech Stack

| Area | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Security, Maven |
| Database | PostgreSQL 16, Flyway |
| Cache/runtime | Redis |
| API docs | SpringDoc OpenAPI in local/dev profiles |
| Frontend | Next.js 16, React 19, TypeScript, pnpm |
| Testing | JUnit 5, Mockito, Testcontainers, Vitest |
| Deployment | Docker, Render Blueprint |

## Local Setup

Prerequisites:

- Java 17
- Docker Desktop
- Node.js 22
- pnpm 9+

Start backend infrastructure:

```powershell
docker compose up -d
```

Run the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Or use the helper script on Windows:

```powershell
.\scripts\dev-backend.ps1
```

Backend local endpoints:

| URL | Description |
|---|---|
| http://localhost:8087/api/v1/health | API health |
| http://localhost:8087/actuator/health | Actuator health |
| http://localhost:8087/swagger-ui.html | Swagger UI in local/dev |
| http://localhost:8087/api-docs | OpenAPI JSON in local/dev |

Run the frontend:

```powershell
cd frontend
pnpm install
pnpm dev
```

By default the frontend proxies API calls to `http://localhost:8087`. Override with `API_BACKEND_URL` when needed.

## Tests

Backend unit tests:

```powershell
.\mvnw.cmd test
```

Backend integration tests requiring Docker/Testcontainers:

```powershell
.\mvnw.cmd verify -Pintegration-tests
```

Frontend tests and production build:

```powershell
cd frontend
pnpm test
pnpm build
```

## Demo Data

The public Render deployment can seed a small catalog of active vehicle listings, a restricted demo customer account, and sample notifications so the web app is not empty after a fresh database provision.

```text
RENTFLOW_DEMO_SEED_ENABLED=true
```

The seeder is idempotent and creates public listing photos through the file metadata layer using public image URLs. It does not create admin credentials or expose operational secrets.

## Deployment

`render.yaml` defines the API service, web service, PostgreSQL database, and Redis instance for Render. Secrets are managed through Render environment variables and generated values; the repository does not store production secrets.

For production-like deployments:

- Keep `SPRING_PROFILES_ACTIVE=prod`.
- Provide explicit JWT, encryption, and signed-URL secrets through the platform.
- Keep CORS restricted to the deployed frontend origin.
- Use the logging outbox dispatcher by default; enable Kafka only when a broker is provisioned.

## Documentation

Current source-of-truth docs:

- `AGENTS.md`
- `docs/architecture.md`
- `docs/roadmap.md`
- `docs/srs.md`
- `docs/payment-provider-architecture.md`

Historical audit docs are kept only where they explain past implementation decisions.
