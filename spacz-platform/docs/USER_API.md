# SPACZ User API

Every route the user (student) app needs, taken from the backend version running in production on 4 November 2026.

**Base URL:** `https://api.spacz.in`

**Interactive docs:** <https://api.spacz.in/swagger-ui.html> (choose a service in the top-right menu)

In the tables below:

- `*` marks a required field.
- **User** means a logged-in account with the `USER` role.
- **Logged in** means any logged-in account.
- **Public** means no token is needed.

## Know before you start

- **Local development:** browser calls are accepted from `http://localhost:3000` and `http://localhost:5173` only. Any other address, including `localhost:4200`, is blocked until the backend team adds it. Send them the live app address as well. Native mobile apps aren't affected.
- **Phone OTP:** SMS isn't connected yet, so codes don't reach phones or appear in API responses. Use email and password sign-up for testing.
- **Payments:** the API has no payment routes. The app takes payment through its own provider, then calls `/confirm`. Agree the payment approach with the backend team before building this screen.
- **Search results:** only live halls appear, so a hall must be set up by a vendor and approved by an admin before students can find it.
- **Profile photos:** there is no file upload route. Upload the file elsewhere first, then send its URL as `profileImageUrl`.
- **Production data:** there is no separate test environment, so test accounts and bookings are real records.
- **Test coverage:** the routes that need a login were checked without a token and correctly refused it (`401`). Complete logged-in flows haven't been run against production yet, so report any surprises with the `correlationId`.

## Connect the app

- Send `Authorization: Bearer <accessToken>` on every User route.
- Access tokens last 15 minutes. On a `401`, call `/api/auth/refresh` once and retry.
- Refresh tokens are single use. Store the new one each time, and share one refresh call across parallel requests, because reusing an old token signs the account out everywhere.
- Lists that say `page`, `size` and `sort` return `{content, page, size, totalElements, totalPages, first, last}`. Use `?page=0&size=20&sort=field,asc`.
- Errors return `{status, error, message, path, correlationId, fieldErrors}`. Branch on `error` and map `fieldErrors` to form fields.
- Dates use `YYYY-MM-DD`, times `HH:mm`, and money is a number in INR.

## Booking flow

1. Find a hall with `GET /api/studyhalls`, then open it with `GET /api/studyhalls/{id}`.
2. Load the seat map with `GET /api/studyhalls/{id}/seats?startDate=&endDate=`.
3. Create the booking with `POST /api/bookings`. It starts as `PENDING` and holds the seat for 15 minutes. Use `holdExpiresAt` for the countdown.
4. Take payment, using the `totalPrice` from the response. The server always sets the price.
5. Call `POST /api/bookings/{id}/confirm` before the hold expires.
6. The student now appears as a member of the hall in `GET /api/users/me/enrollments`.

Common booking errors:

| Status | Error | What to do |
|---|---|---|
| 409 | `SEAT_UNAVAILABLE` | Someone else took the seat. Reload the seat map. |
| 422 | `BOOKING_HOLD_EXPIRED` | The 15 minutes ran out. Book again. |

### Booking request examples

```jsonc
// DAILY: both dates are included in the booking
{ "studyHallId": 2, "seatId": 11, "plan": "DAILY", "startDate": "2026-12-01", "endDate": "2026-12-03", "programId": 1 }

// MONTHLY: 1-12 months; the server works out the end date
{ "studyHallId": 2, "seatId": 11, "plan": "MONTHLY", "startDate": "2026-12-01", "months": 2 }
```

### Drawing the seat map

Draw each block as a grid of `totalRows` × `totalColumns` and place each seat at its `row` and `column`, counting from 1. Empty cells are aisles or gaps. Only seats with `available: true` can be selected. Each seat has its own `pricePerDay` and `pricePerMonth`; a `null` monthly price means monthly booking isn't offered for that seat.

## Sign-up and login

Route the user by `user.role`, which is `USER` here.

| Method | Path | Access | Purpose | Body |
|---|---|---|---|---|
| POST | `/api/auth/register/user` | Public | Create a student account and log in. | `email*`, `password*`, `firstName*`, `lastName`, `phone`, `city` |
| POST | `/api/auth/register` | Public | Same sign-up with an explicit role. | `role*` (`"USER"`), `email*`, `password*`, `firstName`, `lastName`, `phone`, `city` |
| POST | `/api/auth/login` | Public | Log in with email or phone and a password. | `email*`, `password*` |
| POST | `/api/auth/otp/request` | Public | Send a one-time code to a phone. Not usable until SMS is connected. | `phone*` |
| POST | `/api/auth/otp/verify` | Public | Check the code. A new number returns `422 REGISTRATION_REQUIRED`; repeat with the same code, `role` and `firstName`. | `phone*`, `code*`, `role` (`"USER"`), `firstName`, `lastName`, `city` |
| POST | `/api/auth/refresh` | Public | Swap a refresh token for a new token pair. | `refreshToken*` |
| POST | `/api/auth/logout` | Public | Revoke a refresh token. Returns `204`. | `refreshToken*` |
| GET | `/api/auth/me` | Logged in | The logged-in account. | |
| PUT | `/api/auth/me/password` | Logged in | Set or change the password. Signs out other sessions. Returns `204`. | `currentPassword`, `newPassword*` |

Passwords must be 8-72 characters with at least one letter and one digit. Login and sign-up return:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "Z0iuZT-Ipr...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": 7, "email": "student@example.com", "phone": "+919876543210", "role": "USER", "status": "ACTIVE", "passwordSet": true }
}
```

## My profile and activity

| Method | Path | Access | Purpose | Body or query |
|---|---|---|---|---|
| GET | `/api/users/me` | User | My profile. | |
| PUT | `/api/users/me` | User | Update my profile. This replaces all editable fields, so send the full form. | Body: `firstName*`, `lastName`, `phone`, `profileImageUrl`, `dateOfBirth`, `city`, `state`, `bio`, `educationLevel`, `preferredCity`, `preferredStudySlot`, `dailyStudyHoursGoal` |
| GET | `/api/users/me/programs` | User | Exams and courses I'm preparing for. | |
| POST | `/api/users/me/programs` | User | Add an exam or course. Returns `409` if it's already chosen. | Body: `programId*`, `startDate`, `targetDate`, `status`, `notes` |
| PUT | `/api/users/me/programs/{id}` | User | Update a chosen exam or course. | Body: same fields as `POST /api/users/me/programs` |
| DELETE | `/api/users/me/programs/{id}` | User | Remove a chosen exam or course. Returns `204`. | |
| GET | `/api/users/me/enrollments` | User | My study hall memberships. | |
| GET | `/api/users/me/activity` | User | My activity history, newest first. | Query: `type`, `page`, `size`, `sort` |

## Find study halls

| Method | Path | Access | Purpose | Query |
|---|---|---|---|---|
| GET | `/api/studyhalls` | Public | Search live halls. All filters are optional and combine together. Sort by `name`, `city`, `pricePerDay`, `pricePerMonth` or `createdAt`. | `search`, `city`, `programId`, `amenityIds`, `minPrice`, `maxPrice`, `latitude`, `longitude`, `radiusKm`, `availableFrom`, `availableTo`, `page`, `size`, `sort` |
| GET | `/api/studyhalls/{id}` | Public | Hall page: images, amenities, exams, hours and blocks with prices. | |
| GET | `/api/studyhalls/{id}/availability` | Public | Seat availability summary. Defaults to today. | `startDate`, `endDate` |
| GET | `/api/studyhalls/{id}/seats` | Public | Seat map with availability. Defaults to today. | `startDate`, `endDate` |
| GET | `/api/vendors/{vendorId}` | Public | Public profile of the hall's institute. | |

Example: `/api/studyhalls?city=Hyderabad&programId=1&amenityIds=1,3&maxPrice=200&page=0&size=20`

## Bookings

| Method | Path | Access | Purpose | Body or query |
|---|---|---|---|---|
| POST | `/api/bookings` | User | Create a `PENDING` booking that holds the seat for 15 minutes. | Body: `studyHallId*`, `seatId*`, `plan`, `startDate*`, `endDate`, `months`, `programId` |
| GET | `/api/bookings/me` | User | My bookings, newest first. | Query: `status`, `page`, `size`, `sort` |
| GET | `/api/bookings/{id}` | User | One of my bookings. | |
| POST | `/api/bookings/{id}/confirm` | User | Confirm a `PENDING` booking before its hold expires. | |
| POST | `/api/bookings/{id}/cancel` | User | Cancel a `PENDING` booking, or a `CONFIRMED` one before its start date. | Body (optional): `reason` |

`plan` defaults to `DAILY`. A `DAILY` booking needs `endDate`, and a `MONTHLY` booking needs `months`.

## Lists for forms

Use these for the exam picker and search filters.

| Method | Path | Access | Purpose | Query |
|---|---|---|---|---|
| GET | `/api/programs` | Public | Active exams and courses. | `search`, `category` |
| GET | `/api/programs/{id}` | Public | One exam or course. | |
| GET | `/api/amenities` | Public | Active amenities. | |

## Values you can send

| Field | Values |
|---|---|
| Booking plan | `DAILY`, `MONTHLY` |
| Booking status | `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `EXPIRED` |
| Exam or course status | `PLANNED`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `DROPPED` (defaults to `PLANNED`) |
| Preferred study slot | `EARLY_MORNING`, `MORNING`, `AFTERNOON`, `EVENING`, `NIGHT`, `FLEXIBLE` |
| Activity type | `PROFILE_CREATED`, `PROFILE_UPDATED`, `PROGRAM_ADDED`, `PROGRAM_UPDATED`, `PROGRAM_REMOVED`, `BOOKING_CREATED`, `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `BOOKING_COMPLETED`, `BOOKING_EXPIRED` |

## Same routes by user ID

The app doesn't need these, because the `/me` routes above do the same job. They only work for the logged-in user's own ID, and admins can read other users' data.

| Method | Path |
|---|---|
| GET, PUT | `/api/users/{userId}` |
| GET | `/api/users/{userId}/programs` |
| GET | `/api/users/{userId}/enrollments` |
| GET | `/api/users/{userId}/activity` |
