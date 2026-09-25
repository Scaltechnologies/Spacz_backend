# spacz-studyhall-service (port 8083, database `spacz_studyhall`)

The main business service: vendors/institutes and their approval status, study halls (branches),
blocks and seats, amenities, the exam/course catalog, bookings, enrollments (a hall's students), and
the **legacy Partner-app API**. It replaces `spacz-partner-service`
(owner → vendor profile, property → study hall, block → block, seat → seat,
amenity flags → amenities, image → image).

* **Seating:** a hall has blocks. A block is a `rows × columns` grid; cells without a seat are gaps
  (aisles, pillars). Prices resolve seat → block → hall, separately for day and month.
* **Double-booking prevention:** seat row lock + overlap check + PostgreSQL exclusion constraint
  (`V3`), tested with concurrent requests on real PostgreSQL.
* **Enrollments:** created from confirmed bookings, or added by the vendor for walk-ins (the seat
  becomes `RESERVED` for them).
* **Legacy API** (`controller/legacy`): same paths and JSON as spacz-partner-service, now requiring a
  VENDOR token and scoped to the caller. Its 7 integration tests were ported (`LegacyPartnerApiTest`).
* **Import API** (`/internal/import/**`) used by `scripts/legacy-migration`.

| Layer | Contents |
|---|---|
| `entity` | `VendorProfile`, `StudyHall`, `StudyHallImage`, `OperatingHours`, `Amenity`, `Program`, `Block`, `Seat`, `Booking`, `Enrollment` |
| `service` | `VendorService`, `StudyHallManagementService`, `StudyHallQueryService`, `StudyHallAdminService`, `SeatingService`, `ProgramService`, `AmenityService`, `BookingService`, `EnrollmentService`, `StatsService`, `LegacyPartnerService`, `LegacyImportService` |
| `controller` | `VendorController`, `StudyHallController`, `SeatingController`, `EnrollmentController`, `ProgramController`, `AmenityController`, `BookingController`, `legacy/*`, `internal/*` |
| `client` | `UserServiceClient`, `AdminServiceClient` (audit events) |
| migrations | `V1` schema, `V2` indexes, `V3` exclusion constraint, `V4` amenities seed, `V5` blocks/programs/enrollments/legacy IDs |

**Run alone:** Postgres from compose, then `../mvnw spring-boot:run`. Env: DB vars, `JWT_SECRET`,
`JWT_ISSUER`, `INTERNAL_API_KEY`, `USER_SERVICE_URL`, `ADMIN_SERVICE_URL`,
`STUDYHALL_APPROVAL_REQUIRED` (true), `BOOKING_AUTO_CONFIRM` (false), `BOOKING_HOLD_DURATION` (15m),
`BUSINESS_TIME_ZONE` (Asia/Kolkata).

**Tests:** 34. They cover state machines and price inheritance, booking rules (daily/monthly,
conflicts, holds), the end-to-end vendor → approval → search → booking → enrollment flow, catalogs,
the ported legacy Partner API tests, and PostgreSQL (migrations + validation, 12 concurrent bookings
→ 1, exclusion constraint). The PostgreSQL test needs Docker and is skipped without it.
