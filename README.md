# Fraud Detection Database Deliverables

This package is the database-design and SQL contribution for the COMP9120 fictional, rule-based fraud-review prototype.

## Files

| File | Use |
|---|---|
| `database-design.md` | Report-ready conceptual design, relational model, normalisation analysis, constraints, index and transaction rationale. |
| `conceptual-erd.mmd` | Renderable ERD source, including the intended cardinalities and optional/mandatory participation. |
| `final-eer-diagram.svg` | Final Chen/EER conceptual ER diagram using the COMP9120 lecture notation: rectangles, ellipses, diamonds, underlined keys, and min..max labels. |
| `schema.sql` | Authoritative PostgreSQL DDL. |
| `seed.sql` | Compact deterministic demo dataset. It resets only tables in the `fraud_detection` schema. |
| `seed-large.sql` | Larger analytics dataset: `seed.sql` plus 40 customers, 64 cards and about 6,400 transactions over June-October 2026. Every assessment, rule match and alert was produced by the real rule engine. Use it for report query outputs; use `seed.sql` for the demo video. |
| `queries.sql` | Eight documented report/API queries, including the five specified analytical queries. |
| `test-cases.md` | Seed-query expectations, constraint checks, and service-level transaction tests. |
| `transaction-demo.sql` | `psql` walk-through of the multi-step intake and review transactions: one COMMIT, then two forced failures that ROLLBACK with zero rows changed. |
| `concurrency-demo-session-a.sql`, `concurrency-demo-session-b.sql` | Two-terminal demo of the card-row lock: session B cannot see A's uncommitted attempt, then waits for A's lock before reading history. |
| `tools/generate_large_seed.py` | Rebuilds `seed-large.sql` deterministically by submitting transactions through the running API. |
| `backend/src/test/.../TransactionIntegrityTests.java` | JUnit integration tests T1-T8 (rule boundaries, rollback, idempotency, concurrency, review workflow, constraints). |
| `install-local.ps1` | Safe local PostgreSQL 18 installer. It prompts for the password on your device and protects existing databases by default. |

## Run order

Create an empty PostgreSQL database and run:

```bash
psql -d fraud_demo -f schema.sql
psql -d fraud_demo -f seed.sql
```

On this device, the simplest setup is to open PowerShell in this folder and run:

```powershell
.\install-local.ps1
```

The installer creates `fraud_detection_demo`, loads the schema and seed data, then prints the row counts. It prompts for the PostgreSQL password locally and does not write the password to a file. If the database already exists, it makes no changes unless you explicitly run `./install-local.ps1 -ResetDemoData` and confirm the reset.

For a quick inspection after seeding:

```sql
SET search_path TO fraud_detection;
SELECT transaction_id, amount, occurred_at FROM payment_transaction ORDER BY transaction_id;
SELECT alert_id, status, created_at FROM fraud_alert ORDER BY alert_id;
```

`queries.sql` contains `$1`, `$2` placeholders for application use. In `psql`, replace them with literal values or prepare the query before executing it. The Spring Boot application should use parameterised statements, not string concatenation.

## Important implementation agreement

Keep `schema.sql` as the sole schema authority. If the team adopts Flyway, split this schema into ordered migrations instead of allowing Hibernate/JPA to generate a second competing schema. Do not commit database credentials.

The `ANALYST` entity is an intentional refinement of the initial architecture: it enables accountable review history and the promised analyst-workload query.

## Running the full prototype

1. Load `schema.sql` and `seed.sql` as above.
2. Backend: `cd backend`, set the variables from `env.example.sh`, then `./mvnw spring-boot:run` (port 8080).
3. Frontend: `cd frontend && npm install && npm run dev`, then open http://localhost:5173.

See `frontend/README.md` for the screens and demo presets.

## Transaction evidence for the report

All commands run from the project root against a freshly seeded database.

**Integration tests (T1-T8).** They need a separate database whose name ends in `_test`; each test drops and reloads the schema, and the suite refuses to touch any other database. Without it the tests are skipped, not failed.

```bash
createdb fraud_test
cd backend
DB_USER=$USER sh mvnw test -Dtest=TransactionIntegrityTests
```

Override `TEST_DB_NAME`, `DB_HOST`, `DB_PORT`, `DB_USER` or `DB_PASSWORD` if needed.

**In the web app.** The **Transaction demo** page (`/demo`) shows the intake boundary, a forced failure with before/after row counts, simultaneous same-card attempts queuing on the lock, and a duplicate request. See `frontend/README.md`.

**Rollback demonstration in psql.**

```bash
psql -d fraud_demo -f seed.sql
psql -d fraud_demo -f transaction-demo.sql
```

**Concurrency demonstration.** Open two terminals:

```bash
psql -d fraud_demo -f concurrency-demo-session-a.sql   # terminal 1: takes the lock, then pauses
psql -d fraud_demo -f concurrency-demo-session-b.sql   # terminal 2: blocks until you press Enter in terminal 1
```

Both demos add a row or two; reload `seed.sql` before recording.

## Larger dataset

```bash
psql -d fraud_demo -f schema.sql      # only if the schema is not loaded yet
psql -d fraud_demo -f seed-large.sql  # replaces the demo data
```

Reload `seed.sql` to return to the compact demo data. To regenerate `seed-large.sql`, see the instructions at the top of `tools/generate_large_seed.py`; the output is deterministic.
