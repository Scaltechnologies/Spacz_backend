# SPACZ Vendor API

Every route the vendor console needs, taken from the backend version running in production on 4 November 2026.

**Base URL:** `https://api.spacz.in`

**Interactive docs:** <https://api.spacz.in/swagger-ui.html> (choose a service in the top-right menu)

In the tables below:

- `*` marks a required field.
- **Vendor** means a logged-in account with the `VENDOR` role.
- **Logged in** means any logged-in account.
- **Public** means no token is needed.

## Know before you start

- **Local development:** browser calls are accepted from `http://localhost:3000` and `http://localhost:5173` only. Any other address, including `localhost:4200`, is blocked until the backend team adds it. Send them the live console address as well.
- **Phone OTP:** SMS isn't connected yet, so codes don't reach phones or appear in API responses. Use email and password sign-up for testing.
- **Hall photos:** there is no file upload route. Upload the file elsewhere first, then send its URL.
- **Production data:** there is no separate test environment, so test accounts and data are real records.
- **Test coverage:** the routes that need a login were checked without a token and correctly refused it (`401`). Complete logged-in flows haven't been run against production yet, so report any surprises with the `correlationId`.

## Connect the console

- Send `Authorization: Bearer <accessToken>` on every Vendor route.
- Access tokens last 15 minutes. On a `401`, call `/api/auth/refresh` once and retry.
- Refresh tokens are single use. Store the new one each time, and share one refresh call across parallel requests, because reusing an old token signs the account out everywhere.
- Lists take `?page=0&size=20&sort=field,asc` and return `{content, page, size, totalElements, totalPages, first, last}`.
- Errors return `{status, error, message, path, correlationId, fieldErrors}`. Branch on `error` and map `fieldErrors` to form fields.
- Dates use `YYYY-MM-DD`, times `HH:mm`, and money is a number in INR.

## Vendor go-live order

1. Register or log in, then read `GET /api/vendors/me`.
2. Fill the fields listed in `missingForSubmission` with `PUT /api/vendors/me`.
3. Submit the profile with `POST /api/vendors/me/submit`.
4. Create a hall, then add its hours, photos, amenities and exams.
5. Create a block. This generates its seats.
6. Submit the hall with `POST /api/studyhalls/{id}/submit`. It needs an available seat with a price and at least one open day.
7. The hall goes `ACTIVE` once an admin has approved both the vendor and the hall.

## Sign-up and login

Shared with the other SPACZ apps. Route the user by `user.role`, which is `VENDOR` here.

| Method | Path | Access | Purpose | Body |
|---|---|---|---|---|
| POST | `/api/auth/register/vendor` | Public | Create a vendor account and log in. The profile starts as `DRAFT`. | `email*`, `password*`, `businessName*`, `contactName*`, `phone*`, `city` |
| POST | `/api/auth/register` | Public | Same sign-up with an explicit role. | `role*` (`"VENDOR"`), `email*`, `password*`, `businessName`, `contactName`, `phone`, `city` |
| POST | `/api/auth/login` | Public | Log in with email or phone and a password. | `email*`, `password*` |
| POST | `/api/auth/otp/request` | Public | Send a one-time code to a phone. Not usable until SMS is connected. | `phone*` |
| POST | `/api/auth/otp/verify` | Public | Check the code. A new number returns `422 REGISTRATION_REQUIRED`; repeat with the same code and role. | `phone*`, `code*`, `role` (`"VENDOR"`), `businessName`, `contactName`, `city` |
| POST | `/api/auth/refresh` | Public | Swap a refresh token for a new token pair. | `refreshToken*` |
| POST | `/api/auth/logout` | Public | Revoke a refresh token. Returns `204`. | `refreshToken*` |
| GET | `/api/auth/me` | Logged in | The logged-in account. | |
| PUT | `/api/auth/me/password` | Logged in | Set or change the password. Signs out other sessions. Returns `204`. | `currentPassword`, `newPassword*` |

Passwords must be 8-72 characters with at least one letter and one digit.

## Vendor profile and overview

| Method | Path | Access | Purpose | Body or query |
|---|---|---|---|---|
| GET | `/api/vendors/me` | Vendor | My profile, `status`, `statusReason` and `missingForSubmission`. | |
| POST | `/api/vendors` | Vendor | Create my profile. Only needed when `GET /api/vendors/me` returns `404`. | Body: `businessName*`, `contactName`, `phone`, `email`, `addressLine`, `city`, `state`, `pincode`, `gstNumber`, `description`, `logoUrl` |
| PUT | `/api/vendors/me` | Vendor | Update my profile. | Body: same fields as `POST /api/vendors` |
| POST | `/api/vendors/me/submit` | Vendor | Send my profile for approval. Moves `DRAFT` or `REJECTED` to `PENDING`. Needs `businessName`, `contactName`, `phone`, `addressLine` and `city`. | |
| GET | `/api/vendors/me/studyhalls` | Vendor | My halls in any status. | Query: `status`, `page`, `size`, `sort` |
| GET | `/api/vendors/me/students` | Vendor | My students across halls. | Query: `studyHallId`, `status`, `programId`, `search`, `page`, `size`, `sort` |
| GET | `/api/vendors/me/bookings` | Vendor | Bookings across my halls, with student contact details. | Query: `studyHallId`, `status`, `from`, `to`, `page`, `size`, `sort` |
| GET | `/api/vendors/{vendorId}` | Public | Public profile of an approved vendor. | |

## Study halls

| Method | Path | Access | Purpose | Body or query |
|---|---|---|---|---|
| POST | `/api/studyhalls` | Vendor | Create a hall as `DRAFT`. | Body: `name*`, `description`, `addressLine*`, `city*`, `state*`, `pincode`, `latitude`, `longitude`, `contactPhone`, `contactEmail`, `pricePerDay*`, `pricePerMonth`, `rules`, `openingTime*`, `closingTime*` |
| GET | `/api/studyhalls/{id}` | Public | Hall details. Send the token to see your own hall in any status; without it, only live halls are returned. | |
| PUT | `/api/studyhalls/{id}` | Vendor | Update hall details. | Body: `name*`, `description`, `addressLine*`, `city*`, `state*`, `pincode`, `latitude`, `longitude`, `contactPhone`, `contactEmail`, `pricePerDay*`, `pricePerMonth`, `rules` |
| DELETE | `/api/studyhalls/{id}` | Vendor | Delete a hall that was never approved and has no bookings. Returns `204`. | |
| POST | `/api/studyhalls/{id}/submit` | Vendor | Send a hall for approval. Moves `DRAFT` or `REJECTED` to `PENDING_APPROVAL`. | |
| PATCH | `/api/studyhalls/{id}/status` | Vendor | Hide or show an approved hall. | Body: `status*` (`"ACTIVE"` or `"INACTIVE"`) |
| PUT | `/api/studyhalls/{id}/operating-hours` | Vendor | Replace the weekly schedule. Days you leave out are closed. | Body: `days*`: `[{dayOfWeek*, openTime, closeTime, closed}]` |
| GET | `/api/studyhalls/{id}/availability` | Public | Seat availability summary. Defaults to today. | Query: `startDate`, `endDate` |

## Photos, amenities and exams

Each route returns the updated hall.

| Method | Path | Access | Purpose | Body |
|---|---|---|---|---|
| POST | `/api/studyhalls/{id}/images` | Vendor | Add a photo by URL. | `url*`, `caption`, `cover`, `displayOrder` |
| DELETE | `/api/studyhalls/{id}/images/{imageId}` | Vendor | Remove a photo. | |
| POST | `/api/studyhalls/{id}/amenities` | Vendor | Add hall-wide amenities. | `amenityIds*` |
| DELETE | `/api/studyhalls/{id}/amenities/{amenityId}` | Vendor | Remove an amenity. | |
| POST | `/api/studyhalls/{id}/programs` | Vendor | Add supported exams or courses. | `programIds*` |
| DELETE | `/api/studyhalls/{id}/programs/{programId}` | Vendor | Remove an exam or course. | |

## Blocks and seats

| Method | Path | Access | Purpose | Body or query |
|---|---|---|---|---|
| POST | `/api/studyhalls/{id}/blocks` | Vendor | Create a block and generate its seats. Use `gaps` for aisles and pillars. | Body: `name*`, `totalRows*` (1-60), `totalColumns*` (1-60), `seatNumberPrefix`, `defaultSeatType`, `gaps`: `[{row, column}]`, `dailyPrice`, `monthlyPrice`, `amenityIds` |
| GET | `/api/studyhalls/{id}/blocks` | Vendor | All blocks with their seats. | |
| PUT | `/api/studyhalls/{id}/blocks/{blockId}` | Vendor | Update a block. | Body: `name*`, `totalRows`, `totalColumns`, `dailyPrice`, `monthlyPrice`, `amenityIds` |
| DELETE | `/api/studyhalls/{id}/blocks/{blockId}` | Vendor | Delete a block that has never been booked. Returns `204`. | |
| GET | `/api/studyhalls/{id}/seats` | Public | Seat map with availability. Defaults to today. Send the token to see your own hall in any status. | Query: `startDate`, `endDate` |
| POST | `/api/studyhalls/{id}/seats` | Vendor | Add a seat in an empty cell of a block. | Body: `blockId*`, `seatNumber*`, `row*`, `column*`, `seatType*`, `pricePerDay`, `pricePerMonth` |
| PUT | `/api/studyhalls/{id}/seats/{seatId}` | Vendor | Update a seat. | Body: `seatNumber*`, `seatType*`, `status*`, `pricePerDay`, `pricePerMonth` |
| DELETE | `/api/studyhalls/{id}/seats/{seatId}` | Vendor | Delete a seat that has never been booked. Returns `204`. | |

## Students and bookings

| Method | Path | Access | Purpose | Body or query |
|---|---|---|---|---|
| GET | `/api/studyhalls/{id}/enrollments` | Vendor | Students of one hall. | Query: `status`, `programId`, `search`, `page`, `size`, `sort` |
| POST | `/api/studyhalls/{id}/enrollments` | Vendor | Add a SPACZ user or a walk-in student. Send `userId` or `guestName`, not both. | Body: `userId`, `guestName`, `guestPhone`, `guestEmail`, `programId`, `seatId`, `plan`, `startDate*`, `endDate`, `notes` |
| PATCH | `/api/studyhalls/{id}/enrollments/{enrollmentId}` | Vendor | Change the seat, exam or end date, or end the enrollment. | Body: `status`, `seatId`, `programId`, `endDate`, `notes` |
| GET | `/api/studyhalls/{id}/bookings` | Vendor | Bookings of one hall, with student contact details. | Query: `status`, `from`, `to`, `page`, `size`, `sort` |
| GET | `/api/bookings/{id}` | Vendor | One booking at your hall. | |

## Lists for forms

Use these to fill the amenity and exam pickers.

| Method | Path | Access | Purpose | Query |
|---|---|---|---|---|
| GET | `/api/amenities` | Public | Active amenities. | |
| GET | `/api/programs` | Public | Active exams and courses. | `search`, `category` |
| GET | `/api/programs/{id}` | Public | One exam or course. | |

## Values you can send

| Field | Values |
|---|---|
| Seat type | `STANDARD`, `PREMIUM`, `CABIN`, `WINDOW`, `ACCESSIBLE` |
| Seat status | `AVAILABLE`, `RESERVED`, `MAINTENANCE`, `INACTIVE` |
| Plan | `DAILY`, `MONTHLY` |
| Enrollment status (update) | `ACTIVE`, `COMPLETED`, `CANCELLED` |
| Hall status | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`, `ACTIVE`, `INACTIVE` |
| Day of week | `MONDAY` to `SUNDAY` |

## Older Partner app routes

These exist for the existing Partner mobile app only. Don't use them in a new vendor console. All of them need a Vendor token.

| Resource | List and create | By ID | Extra routes |
|---|---|---|---|
| Owners | `GET`, `POST /api/owners` | `GET`, `PUT`, `DELETE /api/owners/{id}` | `GET /api/owners/{id}/properties` |
| Properties | `GET`, `POST /api/properties` | `GET`, `PUT`, `DELETE /api/properties/{id}` | `GET /api/properties/{id}/blocks`, `GET /api/properties/{id}/images` |
| Blocks | `GET`, `POST /api/blocks` | `GET`, `PUT`, `DELETE /api/blocks/{id}` | `GET /api/blocks/{id}/seats`, `GET /api/blocks/{id}/amenity` |
| Seats | `GET`, `POST /api/seats` | `GET`, `PUT`, `DELETE /api/seats/{id}` | |
| Amenities | `GET`, `POST /amenities` | `GET`, `PUT`, `DELETE /amenities/{id}` | |
| Images | `GET`, `POST /images` | `GET`, `PUT`, `DELETE /images/{id}` | |
