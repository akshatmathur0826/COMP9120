# Frontend: Fraud Review Console

A React and TypeScript UI built with Vite for the fraud detection prototype. It is a thin presentation layer: every score, rule match and alert comes from the Spring Boot API, and through it from PostgreSQL.

## Run

1. Start PostgreSQL with `schema.sql` and `seed.sql` loaded (see the root README).
2. Start the backend from `/backend` (port 8080 by default).
3. Start the UI:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

The Vite dev server proxies `/api` to `http://localhost:8080`, so no CORS setup is needed. To use another backend port, set `API_TARGET`, for example `API_TARGET=http://localhost:9090 npm run dev`.

## Screens

| Route | Purpose | API |
|---|---|---|
| `/` | Dashboard: KPIs, risk and alert-status counts, rule hits, analyst workload, high-risk merchants, alerts needing review | `GET /api/dashboard`, `GET /api/alerts` |
| `/alerts` | Alert queue with status tabs, risk filter, search and priority sort (matches query Q6 ordering) | `GET /api/alerts` |
| `/alerts/:id` | Score breakdown, rule evidence, transaction context, review actions and history | `GET /api/alerts/{id}`, `GET /api/alerts/{id}/reviews`, `PATCH /api/alerts/{id}/status` |
| `/transactions` | Ledger with card, risk and outcome filters | `GET /api/transactions` |
| `/transactions/:id` | Full context plus the stored assessment | `GET /api/transactions/{id}` |
| `/simulate` | Submit transactions, with demo presets | `GET /api/reference`, `POST /api/transactions` |

## Demo presets (against a freshly seeded database)

| Preset | Card | Intended result |
|---|---|---|
| Normal purchase | 1001 Alice | LOW, no alert |
| Large amount | 1003 Chloe (fewer than 5 approved, so R2 cannot fire) | R1, MEDIUM, alert |
| New device + large | 1003 Chloe, AUD 6,200 on an unseen device | R1 + R4, HIGH, alert |
| Velocity burst | 1002 Ben, 5 sequential attempts | 5th attempt matches R3 |
| Repeated declines | 1003 Chloe, 3 declined attempts | 3rd attempt matches R5 |

Presets share cards, so running several within 10 minutes can also trigger R3. Reset with `seed.sql` before recording the demo video. The **Resend last request ID** button demonstrates duplicate `requestId` rejection (HTTP 409).
