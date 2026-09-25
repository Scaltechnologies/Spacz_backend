# SPACZ — Consolidation & Migration Plan

Status: **implemented 2026-09-25** (steps 1–7). Step 8 (removing `spacz-partner-service/` and the
partner parts of `spacz/`) waits for your sign-off. See [Outcome](#7-outcome) at the end.

Approved 2026-09-25. Decisions: D1 = keep a legacy-compatible Partner API,
D2 = PostgreSQL + migrate MySQL data (the local MySQL `spacz` has the schema but 0 rows, so the tool is
tested against a seeded throwaway MySQL), D3 = email/password **and** secure phone OTP,
D4 = program choices (user-service) **plus** hall memberships (studyhall-service), D5 = plain DTO
responses + the shared `ApiError`.

Refinements made while implementing (supersede the text below where they differ):

* **Approval = live.** Admin approval puts a hall straight into `ACTIVE` when its vendor is
  approved; otherwise it waits in `APPROVED` and goes `ACTIVE` automatically when the vendor is
  approved. The vendor can still toggle `ACTIVE ⇄ INACTIVE`. (The legacy Partner app has no
  "go live" step.)
* **Vendors start in `DRAFT`** and submit themselves (`POST /api/vendors/me/submit`) once the
  profile is complete. Owners and properties created through the legacy API are submitted on
  creation, since that app has no submit step.
* **No path aliases.** `spacz-platform` was never released, so the renamed paths replace the old ones.
* **Booking activity is still pushed** (studyhall → user, async, after commit). It is simpler and
  already proven. The user ↔ studyhall calls are read-only lookups plus this fire-and-forget push.
* **Audit events** are also a fire-and-forget push (auth/studyhall → admin), after commit.

Goal: one microservices backend under `spacz-platform/` with exactly five services. The working
functionality of `spacz-partner-service` (and the relevant parts of the legacy `spacz/` monolith) is
migrated into it. The old projects are removed only after the migration is verified.

---

## 1. What exists today

### 1.1 `spacz/` — legacy monolith (MySQL `spacz`, port 8080)

| Area | What it does | Quality notes |
|---|---|---|
| Auth `/api/auth/register/{phone}`, `/api/auth/login/{phone}/{otp}` | Phone + OTP login, `user_login` table (`login_id, phone_number, otp, is_owner_registered`) | **The OTP is derived from the phone number** (last 4 digits + first digit), so anyone can log in as anyone. No JWT, no sessions. |
| Aspirant users `/aspirant-users` CRUD | `aspirant_user` (name, phone, aadhar, email, current/permanent address) | No validation. Stores Aadhaar in plain text. |
| Booking entity | `booking` (start/end date, `seat_id` 1:1, `aspirant_user_id`) | No API. No overlap protection (seat is 1:1 with a booking). |
| Owner/Property/Block/Seat/Amenity/Image | Same tables as the partner service | Superseded by `spacz-partner-service` (JSON recursion bug). |

### 1.2 `spacz-partner-service/` — vendor side (MySQL `spacz`, port 8080)

It serves the **Partner mobile app** with a deliberately frozen legacy contract.

| Entity (table) | Fields | Relations |
|---|---|---|
| `Owner` (`owner`) | name, email, phone, address, `login_id` → legacy `user_login` | 1 owner → n properties |
| `Property` (`property`) | name, address, `google_coordinates` ("lat,lng" string) | n images, n blocks |
| `Image` (`image`) | `image_url` | → property |
| `Block` (`block`) | name, **daily price**, **monthly price** | → property; n seats; 1 amenity row |
| `Amenity` (`amenity`) | booleans `ac, wifi, water, lockers, newspapers` | **1:1 with block** |
| `Seat` (`seat`) | seat_number, `is_reserved`, seat_price | → block (no row/column position) |

APIs: CRUD on `/api/owners`, `/api/properties`, `/api/blocks`, `/api/seats`, `/amenities`, `/images`,
plus read-only child lists (`/api/owners/{id}/properties`, `/api/properties/{id}/blocks`, …).
18 integration tests. Good parts worth keeping: DTO responses without the recursion bug, 404/409
handling, parent validation, batch fetching.

What it does **not** have: authentication or ownership checks (any caller can edit any owner's
data), approval workflow, statuses, pagination, search, row/column seat layout, programs,
availability, or bookings.

### 1.3 `spacz-platform/` — the 5 services built earlier (PostgreSQL, one DB per service)

Working and verified: all 5 services, Docker Compose, Flyway, 62 tests, a 36-step end-to-end smoke
test and a 60-request Postman run. It covers email/password auth with JWT + refresh tokens,
profiles, an exam catalog, preparations, activity, vendors with an approval state machine, study
halls, images, hours, an amenity catalog, grid seat layouts with gaps, bookings with
double-booking protection, the admin dashboard, approvals and the audit log.

---

## 2. Gap analysis: partner-service + new brief vs. spacz-platform

| # | Partner service / new brief | spacz-platform today | Plan |
|---|---|---|---|
| G1 | **Block** = a section of a property with its own **daily & monthly price** | `SeatLayout` = named section, no price; hall has only `pricePerDay` | Rename the concept to **Block** (`Block` = section, keeps grid rows/columns). Add `dailyPrice` + `monthlyPrice` to hall (default) and block (override). Seat keeps its optional override. |
| G2 | **Monthly pricing** | Per-day only | Booking gets a `plan` (`DAILY` / `MONTHLY`). Monthly = whole months at the monthly price. The price is always computed on the server. |
| G3 | **Amenities per block** (e.g. AC block vs non-AC block) | Amenity catalog per hall | Keep the catalog (admin-managed, extensible). Allow amenities on **both** hall and block; a block inherits the hall's amenities. The legacy booleans map to catalog codes `AC, WIFI, WATER, LOCKER, NEWSPAPER`. |
| G4 | Seat `is_reserved` | Seat `status` AVAILABLE / MAINTENANCE / INACTIVE | Add status **`RESERVED`** (held by the vendor offline, not bookable online). |
| G5 | Seats have no position | Seats need row/column | Row/column stay required for the seat map. Legacy seats are auto-placed row by row (10 per row) during data migration; the vendor can rearrange them later. |
| G6 | `google_coordinates` "lat,lng" | `latitude`, `longitude` | Parsed during migration; invalid values → null. |
| G7 | Owner linked to `user_login` (phone OTP) | Vendor linked to auth account (email/password) | See decision D3. |
| G8 | Brief: **programs owned by studyhall-service** (`/api/programs` CRUD) | Catalog owned by user-service | Move the catalog to studyhall-service (see §3, R1). |
| G9 | Brief: **Enrollment** / student ↔ study hall, vendor manages its students | Only bookings link students to halls | See decision D4. |
| G10 | Brief: **AdminProfile** | Admins exist only as auth accounts | Add `admin_profiles` in admin-service (name, phone, title, last activity). |
| G11 | Brief: audit events *vendor registered, user created, study hall created/updated, program created* | Only admin actions are audited | Add an internal `POST /internal/audit-events` in admin-service. auth and studyhall report domain events **after commit, best effort**. |
| G12 | Brief paths: `/api/studyhalls`, `/api/users/me`, `/api/auth/register`, `POST …/approve` | `/api/study-halls`, `/api/users/{id}`, `/register/user|vendor`, `PATCH …/approve` | Adopt the brief's paths as canonical (details in §4). The old study-hall paths stay as aliases for one release. |
| G13 | Brief: consistent API response format | Errors use `ApiError`; success returns the DTO directly | Keep (see D5). |
| G14 | Legacy Partner-app contract (`/api/owners`, `/api/blocks` …) | Not supported | See decision D1. |

---

## 3. Proposed architecture (changes from today in **bold**)

```
Client ──► gateway :8080 ──┬─► auth :8081       accounts, credentials, JWT, refresh tokens, **phone OTP (D3)**
                           ├─► user :8082       profiles, preferences, **program choices (UserProgram)**, activity
                           ├─► studyhall :8083  vendors, halls, **blocks**, seats, amenities, images, hours,
                           │                    **program catalog**, **hall programs**, bookings, **enrollments (D4)**
                           └─► admin :8084      **admin profiles**, audit log, dashboard, admin workflows
```

### Database ownership

| DB | Tables |
|---|---|
| `spacz_auth` | `user_accounts`, `refresh_tokens`, (**`otp_challenges`** if D3 = phone OTP) |
| `spacz_user` | `user_profiles`, **`user_programs`** (was `user_preparations`; `program_id` → studyhall ID, name snapshot), `user_activities` |
| `spacz_studyhall` | `vendor_profiles`, `study_halls`, `study_hall_images`, `study_hall_operating_hours`, `amenities`, `study_hall_amenities`, **`block_amenities`**, **`programs`**, `study_hall_programs` (FK to `programs` now: same DB), **`blocks`** (was `seat_layouts`), `seats`, `bookings`, **`enrollments` (D4)** |
| `spacz_admin` | **`admin_profiles`**, `audit_logs` |

### R1 — Where the program catalog lives

The brief puts programs in studyhall-service, and vendors attach them to halls. Moving the catalog
there makes `study_hall_programs` a real in-database FK and puts all program management in one
service. user-service then stores `program_id` plus a name snapshot, validated through
`GET /internal/programs/lookup` on studyhall-service.

Call graph after the move:

```
auth      ──► user, studyhall            (create profile at registration)
user      ──► studyhall                  (validate program IDs, hall summaries for "my halls")
studyhall ──► user                       (student names for the vendor roster, read-only)
admin     ──► auth, user, studyhall
```

user ↔ studyhall becomes two-way. To keep it harmless: both directions are **read-only lookups
with timeouts and graceful fallback**, nothing runs at startup, and no transaction spans both
services. Booking activity is no longer pushed from studyhall to user: user-service's "activity"
endpoint merges its own events with the student's bookings, read from studyhall.

### Vendor state machine (unchanged, documented)

`PENDING → APPROVED | REJECTED`, `REJECTED → APPROVED`, `APPROVED|ACTIVE|INACTIVE → SUSPENDED`,
`SUSPENDED|INACTIVE → ACTIVE`. Only `APPROVED/ACTIVE` vendors can take halls live. A vendor submits
for approval explicitly: **new `POST /api/vendors/me/submit`**, which requires a complete profile.

---

## 4. Final API (public, through the gateway)

```
AUTH       POST /api/auth/register            {role: USER|VENDOR, ...}   (+ /register/user, /register/vendor kept)
           POST /api/auth/login | /refresh | /logout      GET /api/auth/me     PUT /api/auth/me/password
           (D3) POST /api/auth/otp/request, POST /api/auth/otp/verify

USERS      GET|PUT /api/users/me     GET /api/users/{id} (self/admin)
           GET /api/users/me/activity     GET|POST|PUT|DELETE /api/users/me/programs[/{id}]
           GET /api/users/me/enrollments  (D4)

VENDORS    POST /api/vendors (complete profile after registration)   GET|PUT /api/vendors/me
           POST /api/vendors/me/submit    GET /api/vendors/{id} (public business card)
           GET /api/vendors/me/students?studyHallId=&status=&search=

STUDYHALLS GET  /api/studyhalls (search/filter/paginate)     GET /api/studyhalls/{id}
           POST /api/studyhalls   PUT|DELETE /api/studyhalls/{id}   (vendor, owner only)
           POST /api/studyhalls/{id}/submit   PATCH /api/studyhalls/{id}/status
           PUT  /api/studyhalls/{id}/operating-hours
           POST|DELETE /api/studyhalls/{id}/images[/{imageId}]
           POST|DELETE /api/studyhalls/{id}/amenities[/{amenityId}]
           POST|DELETE /api/studyhalls/{id}/programs[/{programId}]
           GET|POST /api/studyhalls/{id}/blocks   PUT|DELETE /api/studyhalls/{id}/blocks/{blockId}
           GET  /api/studyhalls/{id}/seats?startDate&endDate    POST /api/studyhalls/{id}/seats
           PUT|DELETE /api/studyhalls/{id}/seats/{seatId}
           GET  /api/studyhalls/{id}/availability     GET /api/studyhalls/{id}/bookings (vendor)

PROGRAMS   GET /api/programs (public)   POST|PUT|DELETE /api/programs/{id} (ADMIN; DELETE = deactivate)
AMENITIES  GET /api/amenities (public)  POST|PUT /api/amenities/{id} (ADMIN)
BOOKINGS   POST /api/bookings   GET /api/bookings/me   GET /api/bookings/{id}   POST /api/bookings/{id}/confirm|cancel

ADMIN      GET /api/admin/dashboard     GET|PUT /api/admin/me (admin profile)
           GET /api/admin/users[/{id}]            POST /api/admin/users/{id}/suspend|activate
           GET /api/admin/vendors[/{id}]          POST /api/admin/vendors/{id}/approve|reject|suspend|activate
           GET /api/admin/studyhalls[/{id}]       POST /api/admin/studyhalls/{id}/approve|reject|suspend|activate
           GET /api/admin/bookings                GET /api/admin/audit-logs
```

Admin state changes move from `PATCH` to **`POST`**, as in the brief (they are actions, not partial
updates). Program and amenity management go directly to `/api/programs` and `/api/amenities`
(ADMIN role) instead of being proxied through admin-service. Studyhall reports each change to the
audit log.

---

## 5. Migration steps (in order; each step ends green: build + tests + smoke test)

1. **Studyhall model refactor:** `SeatLayout` → `Block` with daily/monthly prices and block
   amenities; seat `RESERVED` status; booking `plan` and monthly pricing. Flyway `V5+` (no data yet in
   Postgres, but written as proper migrations anyway).
2. **Program catalog move:** create `programs` in studyhall (seeded, plus EAMCET and others). user-service's
   `preparation_programs` → `user_programs` with `program_id` + name snapshot; the Flyway migration
   copies existing IDs so nothing dangles.
3. **API alignment:** new paths from §4, old paths kept as aliases, `/api/users/me`, single
   `/api/auth/register`, POST admin actions, vendor submit, vendor students.
4. **Admin profile + domain audit events** (G10, G11).
5. **Decision-dependent items:** D1 legacy compatibility layer, D3 OTP, D4 enrollments.
6. **Data migration** (D2): `scripts/migrate-legacy-mysql/`, a repeatable, idempotent job reading
   MySQL `spacz` and writing through the services' internal APIs (never into their DBs directly):
   `user_login` → accounts, `owner` → vendor profiles (status APPROVED, since they are already live),
   `property` → study halls (ACTIVE), `block` → blocks, `amenity` → block amenities, `seat` → seats
   (auto-positioned), `image` → images, `aspirant_user` → profiles. It writes an ID-mapping report
   (legacy ID → new ID).
7. **Per-service README, docs, Postman, tests** updated; `./mvnw clean test` for every service.
8. **Decommission:** after sign-off, remove `spacz-partner-service/` and the partner parts of `spacz/`
   in a separate commit (or move them to `legacy/`). Not before the migration is verified.

## 6. Decisions needed

* **D1 — Partner app compatibility.** Does the live Partner app need `/api/owners`, `/api/properties`,
  `/api/blocks`, `/api/seats`, `/amenities`, `/images` to keep working unchanged? (a) a compatibility
  controller in studyhall-service mapping the legacy contract onto the new model, or (b) a clean break
  where the app moves to the new API.
* **D2 — Database & existing data.** Stay on PostgreSQL (what spacz-platform uses now, recommended)
  or switch to MySQL (what the legacy apps use)? And is there data in MySQL `spacz` to migrate?
* **D3 — Login method.** The apps log in with phone + OTP today, and that OTP is guessable.
  Keep email + password only, or add phone-OTP login (random 6-digit OTP, hashed, 5-minute expiry,
  attempt limit, pluggable SMS sender with a console/log sender for development)?
* **D4 — What an "enrollment" is.** (a) The student's program/exam choice (user-service `UserProgram`);
  vendors see students through bookings. Or (b) additionally a **study-hall membership** owned by
  studyhall-service (student ↔ hall ↔ program ↔ seat, monthly plan, status), which vendors can also
  create for walk-in students. Bookings stay as date-range seat reservations.
* **D5 — Success response format.** Keep returning the DTO directly with standard HTTP codes (errors
  already share one `ApiError` shape), or wrap everything as `{success, data, error, timestamp}`?

## 7. Outcome

| Step | Result |
|---|---|
| 1 Studyhall refactor | `seat_layouts` → `blocks` with daily/monthly prices and block amenities; `RESERVED` seats; `DAILY`/`MONTHLY` bookings; hall prices optional (inherited). Flyway `V5`. |
| 2 Program catalog | Moved to studyhall-service (IDs 1–18 kept, EAMCET/ICET/Group 1/UGC NET/CTET added). user-service `V4` turns `user_preparations` into `user_programs` with name snapshots and drops its catalog. |
| 3 API alignment | `/api/studyhalls`, `/api/vendors`, `/api/users/me`, `/api/programs`, `/api/amenities`, `POST /api/auth/register`, `POST /api/admin/.../approve` etc. |
| 4 Admin | `admin_profiles`, `POST /internal/audit-events`, events from auth and studyhall (source recorded). Admin `V3`. |
| 5 Decisions | D1: `controller/legacy` in studyhall-service (7 ported partner tests). D3: phone OTP in auth-service (`V3`). D4: `enrollments` in studyhall + `/api/users/me/enrollments`. |
| 6 Data migration | `scripts/legacy-migration` (Node + mysql2), idempotent, dry-run mode, ID-mapping report. Tested on a seeded copy of the legacy schema, including edge cases. |
| 7 Docs, Postman, tests | All services: `mvn clean verify` green; 37-step end-to-end smoke test green on Docker. |

**Upgrade path verified.** The new migrations ran on a database holding data from the previous
version (auth V3, user V4, studyhall V5, admin V3). Existing student exam choices, seat layouts,
bookings and audit rows were carried over.

### What the Partner (vendor) mobile app must change

The resource endpoints and JSON are unchanged, but two things differ:

1. **Login:** the old `/api/auth/register/{phone}` + `/api/auth/login/{phone}/{otp}` (whose OTP was
   guessable) is replaced by `POST /api/auth/otp/request` + `POST /api/auth/otp/verify`
   (`role: "VENDOR"` on first login). Migrated owners log in with their existing phone number.
2. **Every call sends `Authorization: Bearer <accessToken>`** and sees only the caller's own data.
   `GET /api/owners` returns a list containing just the caller's owner record.

IDs are the new IDs (`ownerId` = vendor profile, `propertyId` = study hall, `amenityId` = block).
They come from `GET /api/owners` after login. Other differences: seat numbers must be unique within
a property (409), and a property is submitted for admin approval when it is created.

### Decommissioning (step 8, awaiting sign-off)

Once the Partner app runs against the gateway and the data migration has been run on the real
MySQL database:
1. Stop `spacz-partner-service` and the legacy `spacz` app.
2. Delete `spacz-partner-service/`, and either delete `spacz/` or move it to `legacy/`.
3. Keep a MySQL dump as an archive.
