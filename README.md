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
| `queries.sql` | Eight documented report/API queries, including the five specified analytical queries. |
| `test-cases.md` | Seed-query expectations, constraint checks, and service-level transaction tests. |
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
