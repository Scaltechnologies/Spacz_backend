# spacz-admin-service (port 8084, database `spacz_admin`)

Platform administration. It owns only admin data: `admin_profiles` and the append-only
`audit_logs`. Everything else is read or changed through the owning service's internal API. It never
touches another database.

* **Dashboard** aggregates `auth /internal/accounts/stats` and `studyhall /internal/stats` in
  parallel; a service that is down is listed in `unavailableServices`.
* **Workflows:** approve/reject/suspend/activate vendors and study halls (studyhall-service enforces
  the state machine), suspend/activate accounts (auth-service). Each successful action is audited
  with actor, IP, user agent and correlation ID.
* **Audit ingestion:** `POST /internal/audit-events` receives domain events from auth-service and
  studyhall-service (registrations, submissions, hall and catalog changes).
* **User detail** composes the account, profile, exam choices and enrollments (or the vendor
  profile). Parts that fail are listed in `warnings`.

| Layer | Contents |
|---|---|
| `entity` | `AdminProfile`, `AuditLog`, `AuditAction` |
| `service` | `DashboardService`, `UserAdminService`, `VendorAdminService`, `StudyHallModerationService`, `BookingAdminService`, `AuditService`, `AdminProfileService` |
| `controller` | `/api/admin/dashboard`, `/me`, `/users`, `/vendors`, `/studyhalls`, `/bookings`, `/audit-logs`; `internal/InternalAuditController` |
| `client` | `AuthServiceClient`, `UserServiceClient`, `StudyHallServiceClient` |
| migrations | `V1` audit log, `V2` indexes + append-only trigger, `V3` admin profiles + event sources |

**Run alone:** Postgres from compose, then `../mvnw spring-boot:run`. Env: DB vars, `JWT_SECRET`,
`JWT_ISSUER`, `INTERNAL_API_KEY`, `AUTH_SERVICE_URL`, `USER_SERVICE_URL`, `STUDYHALL_SERVICE_URL`.

**Tests:** 13 (role enforcement, audited approvals and suspensions, reasons required, pass-through of
invalid transitions, self-suspension guard, composed user detail, audit ingestion, admin profile,
dashboard degradation, 503 on outage).
