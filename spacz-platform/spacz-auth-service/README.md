# spacz-auth-service (port 8081, database `spacz_auth`)

Identity for SPACZ: accounts, credentials, roles (`ADMIN`, `USER`, `VENDOR`), account status, JWTs
and refresh tokens. No business data: student profiles live in user-service and vendor profiles in
studyhall-service.

* **Login methods:** email + password (BCrypt 12, 5 failures → 15-minute lock), and **phone OTP**
  (random 6-digit code stored as an HMAC, 5-minute expiry, 5 attempts, 30-second resend cooldown,
  5 codes per hour). Phone numbers are normalised to E.164.
* **Tokens:** HS256 access token (15 min; claims `userId`, `role`, `email`, `phone`), opaque refresh
  token (7 days, stored hashed, rotated on use, reuse revokes every session).
* **Registration** creates the profile in user-service (students) or a DRAFT vendor profile in
  studyhall-service (vendors) inside the same transaction, and reports `USER_REGISTERED` /
  `VENDOR_REGISTERED` to the admin audit log.
* **First admin:** created at startup from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.

| Layer | Contents |
|---|---|
| `entity` | `UserAccount`, `RefreshToken`, `OtpChallenge` |
| `service` | `AuthService`, `OtpService`, `TokenService`, `AccountManagementService` (+ `sms/SmsSender`) |
| `controller` | `AuthController` (`/api/auth/**`), `internal/InternalAccountController` (`/internal/accounts/**`) |
| `client` | `UserServiceClient`, `StudyHallServiceClient`, `AdminServiceClient` |
| migrations | `V1` schema, `V2` indexes, `V3` phone + OTP + legacy IDs |

**Run alone:** `docker compose up -d postgres` (from `spacz-platform/`), then
`../mvnw spring-boot:run`, or run `SpaczAuthServiceApplication` in the IDE.

| Env var | Default (dev) | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `localhost:5432/spacz_auth` | database |
| `JWT_SECRET`, `JWT_ISSUER` | dev value, `spacz-auth` | token signing (≥ 32 chars, shared) |
| `INTERNAL_API_KEY` | dev value | service-to-service key (shared) |
| `USER_SERVICE_URL`, `STUDYHALL_SERVICE_URL`, `ADMIN_SERVICE_URL` | `localhost:8082/8083/8084` | downstream services |
| `ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL` | `15m`, `7d` | token lifetimes |
| `OTP_SMS_PROVIDER` | `log` | `log` writes codes to the log; implement `SmsSender` for a real gateway |
| `OTP_EXPOSE_CODE` | `false` | dev only: returns the code in the API response |
| `OTP_DEFAULT_COUNTRY_CODE`, `OTP_TTL`, `OTP_RESEND_COOLDOWN`, `OTP_MAX_PER_HOUR` | `+91`, `5m`, `30s`, `5` | OTP policy |
| `BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | empty | first admin |

**Tests** (`../mvnw test`): 24. They cover registration, lockout, suspended accounts, JWT validation
(tampered / expired / wrong key / wrong issuer), refresh rotation and reuse, OTP register and login,
attempt limits, rate limits, and legacy import.
