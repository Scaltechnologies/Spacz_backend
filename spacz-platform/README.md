# SPACZ Platform — Study Hall Management Microservices

The SPACZ backend. Students find study halls, choose their exam, book seats (daily or monthly) and
belong to halls. Vendors and institutes list and run their halls. Admins approve and oversee
everything. It is **exactly five Spring Boot services**, each with its own PostgreSQL database.

| Service | Port | Database | Owns |
|---|---|---|---|
| [`spacz-gateway-service`](spacz-gateway-service/README.md) | **8080** | — | routing, CORS, JWT pre-check, correlation IDs, Swagger aggregation |
| [`spacz-auth-service`](spacz-auth-service/README.md) | 8081 | `spacz_auth` | accounts, email/password + **phone OTP** login, JWT, refresh tokens, account status |
| [`spacz-user-service`](spacz-user-service/README.md) | 8082 | `spacz_user` | student profiles, exam/course choices, activity |
| [`spacz-studyhall-service`](spacz-studyhall-service/README.md) | 8083 | `spacz_studyhall` | vendors & approval, halls, blocks, seats, amenities, **program catalog**, bookings, **enrollments**, legacy Partner API |
| [`spacz-admin-service`](spacz-admin-service/README.md) | 8084 | `spacz_admin` | admin profiles, dashboard, approval workflows, audit log |

This replaces `../spacz-partner-service` and the partner/auth/aspirant parts of the `../spacz`
monolith. Their functionality lives in studyhall-service (including the unchanged Partner-app API)
and auth-service. `scripts/legacy-migration` moves their MySQL data here. The old projects are kept
until you sign off; see [docs/MIGRATION_PLAN.md](docs/MIGRATION_PLAN.md).

**Docs:** [Architecture](docs/ARCHITECTURE.md) (responsibilities, database ownership, ER diagram, API
list, inter-service calls, flows, state machines) · [Frontend integration](docs/FRONTEND_INTEGRATION.md) ·
[Migration plan & outcome](docs/MIGRATION_PLAN.md) · [Postman collection](postman/SPACZ.postman_collection.json) ·
Swagger: http://localhost:8080/swagger-ui.html

## Quick start (Docker)

```bash
cd spacz-platform
cp .env.example .env             # replace every secret (openssl rand -base64 48)
docker compose up -d --build     # postgres + 5 services; first build takes a few minutes
docker compose ps                # wait until all six are "healthy"
node scripts/smoke-test.mjs      # optional: 37-step end-to-end check through the gateway (Node 18+)
```

* The first admin comes from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` in `.env`.
* **Phone OTP in development:** codes are written to the auth-service log
  (`docker compose logs auth-service | grep "DEV SMS"`). With `OTP_EXPOSE_CODE=true` they are also
  returned by the API; the smoke test and Postman need this. Never enable it in production.
* Any host port already in use can be changed in `.env` (`GATEWAY_HOST_PORT`, `AUTH_HOST_PORT` …).
* Stop with `docker compose down`; add `-v` to wipe the database.

**Startup order** (enforced by compose health checks): postgres → user → studyhall → auth →
admin → gateway. Each service applies its own Flyway migrations and then validates its JPA
mapping against the schema.

## Environment variables

All of them are in [.env.example](.env.example):

| Variable | Used by | Purpose |
|---|---|---|
| `JWT_SECRET`, `JWT_ISSUER` | all + gateway | HS256 signing key (≥ 32 chars) and issuer |
| `INTERNAL_API_KEY` | all | service-to-service key for `/internal/**` |
| `POSTGRES_PASSWORD`, `AUTH_DB_PASSWORD`, `USER_DB_PASSWORD`, `STUDYHALL_DB_PASSWORD`, `ADMIN_DB_PASSWORD` | postgres + services | one login role per service database |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | auth | first admin (password ≥ 12 chars) |
| `OTP_SMS_PROVIDER`, `OTP_EXPOSE_CODE`, `OTP_DEFAULT_COUNTRY_CODE` | auth | phone OTP (`log` provider, dev exposure, `+91`) |
| `CORS_ALLOWED_ORIGINS` | gateway | frontend origins |
| `STUDYHALL_APPROVAL_REQUIRED`, `BOOKING_AUTO_CONFIRM`, `BOOKING_HOLD_DURATION`, `BUSINESS_TIME_ZONE` | studyhall | business rules |
| `*_HOST_PORT` | compose | published ports |

## Running one service outside Docker

```bash
docker compose up -d postgres          # plus any other services it calls
cd spacz-studyhall-service && ../mvnw spring-boot:run   # or run Spacz*ServiceApplication in the IDE
```

`application.yml` has dev defaults (`localhost`, dev secrets). Export the variables from `.env` so the
secrets match the containers. Each service README lists its own variables.

## Build & test

```bash
./mvnw clean verify                               # all five services
cd spacz-auth-service && ../mvnw clean test       # one service (each builds on its own)
```

**86 tests**, all green:

| Service | Tests | Highlights |
|---|---|---|
| auth | 24 | registration (email + OTP), lockout, JWT validation, refresh rotation/reuse, OTP attempt/rate limits, legacy import |
| user | 6 | `/me`, ownership, catalog-validated exam choices, enrollments, 503 on outage |
| studyhall | 34 | state machines, pricing inheritance, daily/monthly bookings, enrollments and walk-ins, catalogs, **ported partner-service tests**, **PostgreSQL: migrations + 12 concurrent bookings → 1 + exclusion constraint** |
| admin | 13 | role enforcement, audited workflows, audit ingestion, composed user detail, dashboard degradation |
| gateway | 9 | edge security, routing rules, internal block, correlation ID, CORS |

The PostgreSQL test uses Testcontainers 1.21.4 (required for Docker Engine 29) and is skipped when
Docker is not running.

## Migrating the legacy MySQL data

```bash
cd scripts/legacy-migration && npm install
MYSQL_HOST=localhost MYSQL_PORT=3306 node migrate.mjs --dry-run    # validate + report, writes nothing
MYSQL_HOST=localhost MYSQL_PORT=3306 node migrate.mjs              # migrate (safe to re-run)
```

It imports through the services' internal APIs:
- owners → vendors (APPROVED)
- properties → halls (ACTIVE)
- blocks, seats (auto-placed on the grid), amenity flags and images
- aspirants → students
- bookings → confirmed bookings and enrollments

It writes a legacy→new ID report to `reports/`. Aadhaar numbers and addresses are deliberately not
migrated. Migrated vendors and students log in with phone OTP on their existing numbers.

## Folder tree

```
spacz-platform/
├── pom.xml                      aggregator (each service also builds alone)
├── docker-compose.yml · .env.example · docker/postgres/init-databases.sh
├── docs/                        ARCHITECTURE.md · FRONTEND_INTEGRATION.md · MIGRATION_PLAN.md
├── postman/                     SPACZ.postman_collection.json (66 requests)
├── scripts/                     smoke-test.mjs · legacy-migration/ (migrate.mjs, test fixtures)
└── spacz-<name>-service/        Dockerfile · pom.xml · README.md · src/
    └── src/main/java/com/spacz/<name>/
        config/ controller/ (+ internal/, legacy/) dto/ entity/ repository/
        service/ service/impl/ mapper/ client/ security/ exception/ audit/
        resources/db/migration/  Flyway V1..Vn
```

## Known limitations / next steps

* **SMS:** only the `log` sender exists. Implement `SmsSender` for your SMS gateway (MSG91, Twilio …).
* **Payments** are not integrated: `POST /api/bookings/{id}/confirm` is the payment-success hook
  (`BOOKING_AUTO_CONFIRM=true` skips the hold).
* **Images** are URLs; upload files to object storage from the frontend.
* Audit and activity events are delivered asynchronously and best effort. Use an outbox + broker if
  they must never be lost.
* An access token stays valid for up to 15 minutes after a suspension.
* No gateway rate limiting yet (Spring Cloud Gateway `RequestRateLimiter` + Redis). Scheduled jobs
  assume a single instance per service (add ShedLock to scale out).
