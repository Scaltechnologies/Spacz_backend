# spacz-user-service (port 8082, database `spacz_user`)

Student business data: profile and preferences, the exams/courses a student prepares for
(`UserProgram`), and the activity history.

* **Program catalog** is owned by studyhall-service. A choice stores `programId` plus a code/name
  snapshot, validated through `GET /internal/programs/lookup`.
* **Enrollments** (the halls a student belongs to) are owned by studyhall-service and read through
  `GET /internal/enrollments?userId=` for `/api/users/me/enrollments`.
* **Activity** records profile and program changes locally. studyhall-service pushes booking events
  to `POST /internal/users/{id}/activities`.

| Layer | Contents |
|---|---|
| `entity` | `UserProfile`, `UserProgram`, `UserActivity` |
| `service` | `UserProfileService`, `UserProgramService`, `EnrollmentQueryService`, `ActivityService` |
| `controller` | `UserController` (`/api/users/me/**`, `/api/users/{id}/**`), `internal/InternalUserController` |
| `client` | `StudyHallServiceClient` |
| migrations | `V1`–`V3` original schema, `V4` catalog moved to studyhall (`user_programs`), email optional |

**Run alone:** Postgres from compose, then `../mvnw spring-boot:run`. Env: `DB_URL`/`DB_USERNAME`/
`DB_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER`, `INTERNAL_API_KEY`, `STUDYHALL_SERVICE_URL`.

**Tests:** 6 (idempotent profile creation, `/me`, ownership, catalog-validated program choices,
catalog outage → 503, enrollments).
