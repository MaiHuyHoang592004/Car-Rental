# RentFlow Web

Next.js web client for the RentFlow car rental platform.

## Live Demo

| Surface | URL |
|---|---|
| Web app | https://rentflow-web.onrender.com |
| Backend API | https://rentflow-api-2czk.onrender.com |

## Stack

- Next.js 16 App Router
- React 19
- TypeScript
- pnpm
- TanStack Query
- React Hook Form + Zod
- Vitest + Testing Library

## Local Development

Install dependencies:

```powershell
pnpm install
```

Start the app:

```powershell
pnpm dev
```

Open:

```text
http://localhost:3000
```

The app proxies `/api/v1/*` to the backend through `next.config.ts`.

Default backend:

```text
http://localhost:8087
```

Override when needed:

```powershell
$env:API_BACKEND_URL = "https://rentflow-api-2czk.onrender.com"
pnpm dev
```

## Scripts

```powershell
pnpm test
pnpm build
pnpm lint
```

## Notes

- Auth routes use the Next.js BFF layer under `/api/auth/*` to keep refresh-token handling server-side.
- Product routes use feature API modules under `src/features/**/api.ts`.
- Public listing pages are wired to the real backend API and rely on backend demo seed data for the hosted portfolio environment.
