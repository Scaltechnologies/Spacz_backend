# SPACZ Backend — Overview

SPACZ is a platform for booking study halls. Students find a hall and book a seat by the day or
by the month. Vendors (institutes or hall owners) list their halls, blocks and seats. Admins
approve vendors and halls and oversee the platform.

This page gives the whole backend at a glance and records the role-based access review of
2026-11-10. For full details, see [ARCHITECTURE.md](ARCHITECTURE.md) and the per-client API docs
listed at the end.

---

## 1. At a glance

| | |
|---|---|
| **Style** | 5 Spring Boot microservices behind an API gateway, with one PostgreSQL database per service |
| **Stack** | Java 17 · Spring Boot 3.4.2 · Spring Cloud Gateway 2024.0.0 · Spring Security (OAuth2 resource server, HS256 JWT) · JPA/Hibernate 6 · Flyway · PostgreSQL 16 · springdoc (Swagger) |
| **Roles** | `USER` (student) · `VENDOR` (hall owner) · `ADMIN` |
| **Clients** | Student app, Vendor app, Admin console, legacy Partner app |
| **Entry point** | Gateway on `:8080`; Swagger UI at `/swagger-ui.html` (all services combined) |
| **Build / deploy** | Maven multi-module; GitHub Actions builds the images, pushes them to GHCR and deploys to the VPS with Docker Compose |

```
 Student / Vendor / Admin / Partner apps
                    │  HTTPS + Bearer JWT
                    ▼
        ┌────────────────────────┐
        │ gateway-service :8080  │  routing · CORS · JWT + role pre-check · correlation ID
        └───────────┬────────────┘
   ┌────────────┬───┴────────┬──────────────┐
   ▼            ▼            ▼              ▼
 auth :8081   user :8082   studyhall :8083  admin :8084
   │            │            │              │
 spacz_auth   spacz_user   spacz_studyhall  spacz_admin     (PostgreSQL 16, one DB + login role each)
```

## 2. Services

| Service | Port | Owns | Key endpoints |
|---|---|---|---|
| **gateway-service** | 8080 | Routing, CORS, early JWT and role rejection, `X-Correlation-Id`, Swagger aggregation. Strips `X-Internal-Api-Key` from client requests. No database. | — |
| **auth-service** | 8081 | Accounts, roles, account status, email+password login, phone OTP, JWT access tokens (15 min), rotating refresh tokens (7 days), lockout | `/api/auth/register[/user\|/vendor]`, `/login`, `/otp/request`, `/otp/verify`, `/refresh`, `/logout`, `/me`, `/me/password` |
| **user-service** | 8082 | Student profiles, exam/course choices, activity history | `/api/users/me[/programs\|/enrollments\|/activity]`, `/api/users/{id}/…` |
| **studyhall-service** | 8083 | Vendor profiles and approval status, halls, images, hours, blocks, seats, program and amenity catalogs, bookings, enrollments, legacy Partner API | `/api/vendors/**`, `/api/studyhalls/**`, `/api/programs/**`, `/api/amenities/**`, `/api/bookings/**`, legacy `/api/owners\|properties\|blocks\|seats`, `/amenities`, `/images` |
| **admin-service** | 8084 | Admin profiles, dashboard aggregation, approval and suspension workflows, append-only audit log | `/api/admin/dashboard\|me\|users\|vendors\|studyhalls\|bookings\|audit-logs` |

The old `spacz/` monolith and `spacz-partner-service/` at the repo root are **legacy code**. Their
features now live in `spacz-platform`. They are not Maven modules of the platform and are not part
of `deploy/docker-compose.prod.yml`.

### Communication between services
- REST calls through `RestClient` to `/internal/**` endpoints, authenticated with
  `X-Internal-Api-Key` (compared in constant time). The gateway **never** routes `/internal/**`
  (`denyAll`).
- Synchronous calls: auth → user/studyhall at registration (same transaction), admin → all
  services (dashboard and moderation), user ↔ studyhall reads.
- Asynchronous, best-effort calls made after commit: audit events → admin, booking activity → user.
- No foreign keys or JPA relations cross services. Other services' data is referenced by plain IDs
  plus name snapshots.

## 3. Request lifecycle and security model

1. The client sends `Authorization: Bearer <access JWT>`. The JWT comes from auth-service, is
   signed HS256, has issuer `spacz-auth`, and carries the claims `userId` and `role`.
2. **Gateway**: validates the signature, expiry and issuer, maps `role` to `ROLE_<role>`, and
   applies the path and method rules in §4. A bad token gets `401`; a wrong role gets `403`.
3. **Service**: validates the same JWT **again** with its own `SecurityFilterChain`. It then
   applies its own URL rules, `@PreAuthorize` on the controller, and **ownership checks** in the
   service layer.
4. **Ownership**: the caller's identity always comes from the JWT, never from the request body.
   A resource owned by someone else is reported as `404`, so its existence is not revealed.
   - Students: `OwnershipGuard.requireSelf` / `requireSelfOrAdmin` (user-service); booking reads
     check `booking.userId == caller` (studyhall).
   - Vendors: `VendorAccess.ownedHall` / `writableHall` and the `findOwned(id, vendorId)`
     repository queries, used by both the new and the legacy APIs.
   - Suspended vendors are read-only (`VENDOR_SUSPENDED`).
5. **Account status**: login and refresh reject `SUSPENDED` and `DISABLED` accounts. Suspending an
   account revokes all of its refresh tokens. Admin registration is impossible through the public
   API: `ADMIN` accounts are created only by `AdminBootstrapRunner`, and their status cannot be
   changed through the API.

## 4. Role-based access matrix (verified)

✅ = allowed · — = denied (401/403) · *own* = only the caller's own resources (others get 404/403)

| Endpoint group | Public | USER | VENDOR | ADMIN |
|---|:-:|:-:|:-:|:-:|
| `POST /api/auth/register*`, `login`, `otp/*`, `refresh`, `logout` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/auth/me`, `PUT /api/auth/me/password` | — | ✅ | ✅ | ✅ |
| `GET\|PUT /api/users/me[/…]` | — | ✅ | — | ✅ |
| `GET /api/users/{id}[/programs\|enrollments\|activity]` | — | *own* | — | ✅ |
| `PUT /api/users/{id}` | — | *own* | — | *own* |
| `GET /api/studyhalls`, `/{id}`, `/{id}/availability`, `/{id}/seats` | ✅ | ✅ | ✅ | ✅ |
| Hall writes (`POST/PUT/PATCH/DELETE /api/studyhalls/**`) | — | — | *own* | — |
| `GET /api/studyhalls/{id}/blocks`, `/enrollments`, `/bookings` | — | — | *own* | — |
| `GET /api/programs[/{id}]`, `GET /api/amenities` | ✅ | ✅ | ✅ | ✅ |
| `GET /all` and writes on `/api/programs/**`, `/api/amenities/**` | — | — | — | ✅ |
| `GET /api/vendors/{vendorId}` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/vendors`, `/api/vendors/me/**` | — | — | *own* | — |
| `POST /api/bookings`, `/{id}/confirm`, `/{id}/cancel`, `GET /me` | — | *own* | — | — |
| `GET /api/bookings/{id}` | — | *own* | *own hall* | ✅ |
| Legacy Partner API (`/api/owners\|properties\|blocks\|seats/**`, `/amenities/**`, `/images/**`) | — | — | *own* | — |
| `/api/admin/**` | — | — | — | ✅ |
| `/internal/**` | — (gateway denies; services require the API key) | | | |

### Review result — 2026-11-10: **PASS**

- **Present everywhere.** All 5 services have a stateless JWT `SecurityFilterChain` that ends in
  `denyAll` or `authenticated`, with `@EnableMethodSecurity`. Every role-restricted controller
  method or class has `@PreAuthorize`.
- **Gateway and services agree.** Each gateway rule is mirrored, or made stricter, by the owning
  service. No endpoint is opened by the gateway alone.
- **Ownership is enforced** for every `{id}` endpoint: user profiles, bookings, halls, blocks,
  seats, enrollments and all legacy Partner resources.
- **Tests: 93 run, 0 failures** (auth 24, user 6, studyhall 39, admin 13, gateway 11), including
  `GatewaySecurityTest` and `RoleBasedAccessTest`. 3 tests in `DoubleBookingPostgresTest` were
  skipped because they need Docker (Testcontainers), which was not running.
- **Fixed during the review.** Some role-restricted GETs fell under the gateway's public GET
  patterns: `/api/vendors/me/**`, `/api/studyhalls/{id}/blocks|enrollments|bookings` and
  `/api/programs|amenities/all`. The services already blocked them. They are now also rejected at
  the gateway (VENDOR or ADMIN respectively), and tests cover this.

Notes, both by design:

1. After an account is suspended, its access token stays valid for up to 15 minutes, until it
   expires. Its refresh tokens are revoked immediately.
2. Vendors cannot call `/api/users/**`. Vendors see student names through
   `/api/vendors/me/students` instead.

## 5. Data model (per service)

| Database | Main tables |
|---|---|
| `spacz_auth` | `user_accounts` (email/phone, BCrypt hash, role, status, lockout), `refresh_tokens` (SHA-256, rotation and reuse detection), `otp_challenges` (HMAC code, attempts, expiry) |
| `spacz_user` | `user_profiles`, `user_programs` (program ID + snapshot), `user_activities` (append-only) |
| `spacz_studyhall` | `vendor_profiles`, `study_halls`, `study_hall_images`, `study_hall_operating_hours`, `amenities`, `programs`, `blocks` (rows × columns grid), `seats`, `bookings` (GiST **exclusion constraint** against overlaps), `enrollments`, plus join tables |
| `spacz_admin` | `admin_profiles`, `audit_logs` (append-only, enforced by a DB trigger) |

Schemas are managed by Flyway. Migrated legacy rows keep their old IDs in `legacy_*` columns.

## 6. Core flows

- **Registration.** Email+password, or phone OTP (a random 6-digit code stored as an HMAC, valid
  5 minutes, at most 5 attempts, a 30-second cooldown and at most 5 codes per hour). auth creates
  the account and, in the same transaction, creates the student profile (user-service) or a
  `DRAFT` vendor profile (studyhall-service).
- **Vendor onboarding.**
  1. The vendor completes the profile and submits it (`DRAFT → PENDING`).
  2. The vendor builds halls, blocks and seats, then submits each hall (`→ PENDING_APPROVAL`).
  3. An admin approves the vendor and the halls, in either order. A hall goes `ACTIVE` once its
     vendor is operational.
- **Booking.**
  1. The student sends `POST /api/bookings` with a `DAILY` or `MONTHLY` plan.
  2. The service locks the seat row, expires stale holds, checks for overlaps and computes the
     price (seat → block → hall).
  3. The booking is saved as `PENDING` with a 15-minute hold.
  4. `confirm` moves it to `CONFIRMED` and creates or extends the student's enrollment.
  5. A scheduled job expires holds, completes finished bookings and ends lapsed enrollments.
- **Walk-ins.** A vendor enrolls a guest or an existing user on a seat. The seat becomes `RESERVED`.

### State machines
- **Vendor:** `DRAFT → PENDING → APPROVED | REJECTED`; `→ SUSPENDED → ACTIVE` (admin).
- **Study hall:** `DRAFT → PENDING_APPROVAL → APPROVED/ACTIVE | REJECTED`; `ACTIVE ⇄ INACTIVE`
  (vendor); `→ SUSPENDED` (admin).
- **Booking:** `PENDING → CONFIRMED → COMPLETED`, or `CANCELLED` / `EXPIRED`.
- **Enrollment:** `ACTIVE → COMPLETED | CANCELLED`.
- **Account:** `ACTIVE ⇄ SUSPENDED` (ADMIN accounts are protected).

## 7. Cross-cutting concerns

- **Errors:** one `ApiError` JSON shape everywhere: `timestamp`, `status`, `error`, `message`,
  `path`, `correlationId`.
- **Tracing:** the gateway assigns `X-Correlation-Id`, which is propagated to internal calls and
  stored in audit logs.
- **Pagination:** `page`, `size` (max 100), and a `sort` restricted to an allow-list.
- **Limits:** request bodies up to 2 MB; downstream timeouts of 3 s (connect) and 20 s (response)
  at the gateway; internal REST calls use 2 s / 5 s.
- **Health:** `/actuator/health` (with probes) and `/actuator/info`.

## 8. Repository layout

```
spacz-platform/
├── spacz-gateway-service/     spacz-auth-service/     spacz-user-service/
├── spacz-studyhall-service/   spacz-admin-service/
├── docker-compose.yml         # local stack
├── docker/postgres/           # DB + per-service role init
├── deploy/                    # docker-compose.prod.yml, deploy.sh, backup.sh, vps-setup.sh, nginx/
├── scripts/                   # smoke-test.mjs, legacy-migration/
├── postman/                   # SPACZ.postman_collection.json
└── docs/                      # this file + the documents below
```

## 9. Further documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Full architecture, entities, the complete API list, inter-service calls, design review |
| [USER_API.md](USER_API.md) · [VENDOR_API.md](VENDOR_API.md) · [PARTNER_API.md](PARTNER_API.md) | Per-client API guides |
| [FRONTEND_INTEGRATION.md](FRONTEND_INTEGRATION.md) | Base URL, authentication, errors and pagination, and per-app guides (student, vendor, admin, Partner) |
| [ADMIN_FRONTEND_PLAN.md](ADMIN_FRONTEND_PLAN.md) | Admin console blueprint |
| [DEPLOYMENT.md](DEPLOYMENT.md) | VPS, CI/CD and environment variables |
| [MIGRATION_PLAN.md](MIGRATION_PLAN.md) | How the legacy monolith and partner-service were merged in |
