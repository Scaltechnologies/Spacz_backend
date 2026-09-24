# SPACZ — Frontend Integration Guide

Everything the student app, vendor console, admin console and the existing Partner app need to talk
to the backend. The examples come from the running stack.

## 1. One base URL

```
VITE_API_BASE_URL=http://localhost:8080      # the gateway, never the services directly
```

CORS is configured on the gateway only (`CORS_ALLOWED_ORIGINS` in `.env`). Swagger for every
service: **http://localhost:8080/swagger-ui.html** (pick the service in the top-right dropdown).

## 2. Authentication

| Flow | Calls |
|---|---|
| Email sign-up | `POST /api/auth/register {role: "USER", email, password, firstName, lastName?, phone?, city?}`<br>`POST /api/auth/register {role: "VENDOR", email, password, businessName, contactName, phone, city?}` |
| Email login | `POST /api/auth/login {email, password}` (`email` may also be the phone number) |
| **Phone OTP** (login *and* sign-up) | 1. `POST /api/auth/otp/request {phone}` → `202 {phone: "+91******3210", expiresInSeconds: 300, resendAfterSeconds: 30}`<br>2. `POST /api/auth/otp/verify {phone, code}` → tokens, **or** `422 REGISTRATION_REQUIRED` for a new number<br>3. On 422, repeat verify with the **same code** plus `role: "USER", firstName` (students) or `role: "VENDOR", businessName?` (vendors) |
| Refresh | `POST /api/auth/refresh {refreshToken}` → new pair (single-use; store the new refresh token) |
| Logout | `POST /api/auth/logout {refreshToken}` → 204 |
| Account | `GET /api/auth/me` → `{id, email, phone, role, status, passwordSet, ...}` · `PUT /api/auth/me/password {currentPassword?, newPassword}` (OTP accounts set a first password without `currentPassword`) |

Every successful login or sign-up returns:

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "refreshToken": "Z0iuZT-Ipr...", "tokenType": "Bearer", "expiresIn": 900,
  "user": { "id": 7, "email": null, "phone": "+919876543210", "role": "VENDOR", "status": "ACTIVE", "passwordSet": false } }
```

* Send `Authorization: Bearer <accessToken>` on protected calls. On `401`, call refresh **once** and
  retry. Share one refresh promise across parallel requests (reusing an old refresh token logs out
  every session).
* Route by `user.role`: `USER` → student app, `VENDOR` → vendor console, `ADMIN` → admin console.
* OTP errors: `429 OTP_TOO_SOON` (wait `resendAfterSeconds`), `429 OTP_RATE_LIMITED`,
  `401 INVALID_CREDENTIALS` (wrong or expired code; 5 attempts per code), `400 INVALID_PHONE`.
  Numbers may be typed as `9876543210`, `09876543210` or `+91 98765 43210`.
* In development, `OTP_EXPOSE_CODE=true` adds `devCode` to the `otp/request` response and the code is
  also in the auth-service log. In production codes go out by SMS only.

Minimal client with single-flight refresh:

```ts
const API = import.meta.env.VITE_API_BASE_URL;
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;

export function setSession(a: { accessToken: string; refreshToken: string }) {
  accessToken = a.accessToken;
  localStorage.setItem('spacz.refresh', a.refreshToken);
}

async function refresh() {
  const refreshToken = localStorage.getItem('spacz.refresh');
  if (!refreshToken) return false;
  const res = await fetch(`${API}/api/auth/refresh`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }) });
  if (!res.ok) { localStorage.removeItem('spacz.refresh'); accessToken = null; return false; }
  setSession(await res.json());
  return true;
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const res = await fetch(`${API}${path}`, { ...init, headers: {
    'Content-Type': 'application/json', ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}), ...init.headers } });
  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    refreshing ??= refresh().finally(() => (refreshing = null));
    if (await refreshing) return api<T>(path, init, false);
  }
  if (res.status === 204) return undefined as T;
  const body = await res.json();
  if (!res.ok) throw body;          // ApiError, see §3
  return body as T;
}
```

## 3. Errors and pagination

Every error from every service (and the gateway) has this shape:

```json
{ "timestamp": "2026-09-25T06:58:14Z", "status": 400, "error": "VALIDATION_ERROR", "message": "Request validation failed",
  "path": "/api/auth/register", "correlationId": "bfa86351-...",
  "fieldErrors": [ { "field": "password", "message": "must be 8-72 characters and contain at least one letter and one digit" } ] }
```

Branch on `error`, show `message`, map `fieldErrors` to form fields, and quote `correlationId` in bug
reports.

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INVALID_SORT`, `INVALID_PHONE`, `BAD_REQUEST` |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN`, `ACCOUNT_SUSPENDED`, `ACCOUNT_DISABLED`, `ACCOUNT_LOCKED`, `VENDOR_SUSPENDED` |
| 404 | `NOT_FOUND` (also for other people's resources) |
| 409 | `DUPLICATE_RESOURCE`, `SEAT_UNAVAILABLE`, `DUPLICATE_BOOKING`, `SEAT_IN_USE`, `BLOCK_IN_USE`, `SEAT_HELD`, `CONFLICT`, `CONCURRENT_MODIFICATION` |
| 422 | `BUSINESS_RULE_VIOLATION`, `INVALID_STATUS_TRANSITION`, `REASON_REQUIRED`, `PROFILE_INCOMPLETE`, `REGISTRATION_REQUIRED`, `INVALID_DATE_RANGE`, `PLAN_NOT_OFFERED`, `STUDY_HALL_NOT_BOOKABLE`, `SEAT_NOT_BOOKABLE`, `BOOKING_HOLD_EXPIRED`, `BOOKING_ALREADY_STARTED`, `NO_SEATS`, `VENDOR_NOT_APPROVED` |
| 429 | `OTP_TOO_SOON`, `OTP_RATE_LIMITED` |
| 503 | `SERVICE_UNAVAILABLE` |

Lists take `?page=0&size=20&sort=field,asc` and return
`{content, page, size, totalElements, totalPages, first, last}`.

## 4. Student app (`role = USER`)

| Screen | Calls |
|---|---|
| Exam picker | `GET /api/programs?category=&search=` (public) |
| Profile | `GET /api/users/me` · `PUT /api/users/me` (full replacement of the editable fields) |
| My exams | `GET /api/users/me/programs` · `POST {programId, startDate?, targetDate?, status?}` · `PUT /api/users/me/programs/{id}` · `DELETE …/{id}` |
| My study halls | `GET /api/users/me/enrollments` (hall, seat, exam, plan, period, status) |
| History | `GET /api/users/me/activity` · `GET /api/bookings/me?status=` |
| Search | `GET /api/studyhalls?search=&city=&programId=&amenityIds=1,3&minPrice=&maxPrice=&latitude=&longitude=&radiusKm=&availableFrom=&availableTo=&sort=pricePerDay,asc` |
| Filters | `GET /api/amenities`, `GET /api/programs` |
| Hall page | `GET /api/studyhalls/{id}` (images, amenities, programs, hours, `blocks[]` with prices and amenities) · `GET /api/vendors/{vendorId}` (institute card) |
| Seat picker | `GET /api/studyhalls/{id}/seats?startDate=&endDate=` |
| Book | `POST /api/bookings` → `POST /api/bookings/{id}/confirm` · `POST /api/bookings/{id}/cancel {reason?}` |

**Seat map:** draw each `blocks[i]` as a CSS grid of `totalRows × totalColumns` and place each seat at
`(row, column)` (1-based). Empty cells are aisles or gaps. Only seats with `available: true` are
clickable. Seats with status `RESERVED` (held offline by the hall), `MAINTENANCE` or `INACTIVE` are
not. Each seat carries its effective `pricePerDay` and `pricePerMonth` (`null` = monthly not offered).

**Booking:**

```jsonc
// daily: inclusive dates
{ "studyHallId": 2, "seatId": 11, "plan": "DAILY", "startDate": "2026-10-01", "endDate": "2026-10-03", "programId": 1 }
// monthly: start + number of months (1-12); the end date is computed
{ "studyHallId": 2, "seatId": 11, "plan": "MONTHLY", "startDate": "2026-10-01", "months": 2 }
```

The response has `status: "PENDING"`, `units` (days or months), `unitPrice`, `totalPrice` and
`holdExpiresAt` (15 minutes). The price always comes from the server. Take payment, then call
`/confirm`. A confirmed booking makes the student a member of the hall (it appears in
`/api/users/me/enrollments`). On `409 SEAT_UNAVAILABLE`, reload the seat map. On
`422 BOOKING_HOLD_EXPIRED`, book again.

## 5. Vendor console (`role = VENDOR`)

| Screen | Calls |
|---|---|
| Onboarding | `GET /api/vendors/me` → `status` + `missingForSubmission[]` · `PUT /api/vendors/me` · `POST /api/vendors/me/submit` (DRAFT → PENDING) |
| Banner | `DRAFT` "complete your profile", `PENDING` "awaiting approval", `REJECTED`/`SUSPENDED` show `statusReason` |
| Branches | `GET /api/vendors/me/studyhalls` · `POST /api/studyhalls` · `GET|PUT|DELETE /api/studyhalls/{id}` |
| Go live | `POST /api/studyhalls/{id}/submit` → admin approval (hall goes `ACTIVE` once hall and vendor are approved) · `PATCH /api/studyhalls/{id}/status {status: "INACTIVE"|"ACTIVE"}` |
| Hours, photos | `PUT …/{id}/operating-hours {days: [...]}` · `POST …/{id}/images {url, caption, cover}` · `DELETE …/images/{imageId}` (upload files to your CDN first) |
| Amenities, exams | `POST …/{id}/amenities {amenityIds}` · `POST …/{id}/programs {programIds}` · `DELETE …/amenities/{aid}` · `DELETE …/programs/{pid}` |
| Seating designer | `POST …/{id}/blocks {name, totalRows, totalColumns, gaps: [{row,column}], seatNumberPrefix?, dailyPrice?, monthlyPrice?, amenityIds?}` · `GET …/{id}/blocks` · `PUT|DELETE …/blocks/{blockId}` |
| Seats | `POST …/{id}/seats {blockId, seatNumber, row, column, seatType, pricePerDay?, pricePerMonth?}` · `PUT …/seats/{seatId} {…, status}` · `DELETE …/seats/{seatId}` |
| Students | `GET /api/vendors/me/students?studyHallId=&status=&programId=&search=` · `POST /api/studyhalls/{id}/enrollments {userId | guestName, guestPhone?, programId?, seatId?, plan?, startDate, endDate?}` · `PATCH …/enrollments/{eid} {status?, seatId?, programId?, endDate?, notes?}` |
| Bookings | `GET /api/vendors/me/bookings` · `GET /api/studyhalls/{id}/bookings` → `{booking, student: {name, phone, email}}` |

## 6. Admin console (`role = ADMIN`)

| Screen | Calls |
|---|---|
| Dashboard | `GET /api/admin/dashboard` → `users`, `vendors` (incl. `draftVendors`, `pendingVendors`), `studyHalls` (incl. `activePrograms`), `bookings` (incl. `activeEnrollments`), `unavailableServices` |
| Me | `GET|PUT /api/admin/me` |
| Users | `GET /api/admin/users?search=&role=&status=` · `GET /api/admin/users/{id}` (account + profile + exam choices + enrollments, or vendor) · `GET …/{id}/activity` · `POST …/{id}/suspend {reason}` · `POST …/{id}/activate` |
| Vendor approvals | `GET /api/admin/vendors?status=PENDING` · `GET …/{vendorId}` · `POST …/{vendorId}/approve` · `POST …/reject {reason}` · `POST …/suspend {reason}` · `POST …/activate` |
| Hall reviews | `GET /api/admin/studyhalls?status=PENDING_APPROVAL` · `GET …/{id}` · `POST …/{id}/approve|reject|suspend|activate` |
| Exams / courses | `GET /api/programs/all?active=` · `POST /api/programs` · `PUT /api/programs/{id}` · `DELETE /api/programs/{id}` (deactivates) |
| Amenities | `GET /api/amenities/all` · `POST /api/amenities` · `PUT /api/amenities/{id}` |
| Bookings | `GET /api/admin/bookings?studyHallId=&userId=&status=&from=&to=` |
| Audit log | `GET /api/admin/audit-logs?search=&action=&actorUserId=&actorRole=&entityType=&entityId=&from=&to=`. It contains admin actions (with IP) and platform events such as `VENDOR_REGISTERED`, `VENDOR_SUBMITTED`, `STUDY_HALL_CREATED` (with `sourceService`). |

## 7. Existing Partner (vendor) mobile app

The old endpoints keep their paths and JSON: `/api/owners`, `/api/properties`, `/api/blocks`,
`/api/seats`, `/amenities`, `/images`. The app needs two changes: log in with
`/api/auth/otp/request` + `/otp/verify` (`role: "VENDOR"` the first time), and send the bearer token
on every call. Details: [MIGRATION_PLAN.md §7](MIGRATION_PLAN.md#7-outcome).

## 8. Formats

Dates are `YYYY-MM-DD`, times `HH:mm`, timestamps ISO-8601 UTC. Money is a JSON number with 2
decimals (INR). "Today" for booking rules is Asia/Kolkata. Enums are UPPER_CASE strings.
