# spacz-partner-service

The Partner/Vendor side of SPACZ, moved out of the legacy monolith into its own Spring Boot
service. It owns **Owner, Property, Block, Seat, Amenity and Image**.

```
OLD:  SPACZ Partner app ──> spacz (monolith) :8080 ──> MySQL `spacz`
NEW:  SPACZ Partner app ──> spacz-partner-service :8080 ──> MySQL `spacz`
```

**The external contract is the legacy one.** Paths, HTTP methods, request fields, response
fields, status codes, port, database tables and relationships are unchanged. The only
intended change is that responses no longer recurse forever
(`Document nesting depth (1001) exceeds the maximum allowed (1000)`).

The legacy monolith in [`../spacz`](../spacz) is unchanged. It still holds the parts that
belong to future services: Auth (`/api/auth/**`, `UserLogin`), User (`/aspirant-users`) and
the `Booking` entity. Both apps use the same `spacz` database.

## Run locally

Prerequisites: Java 17 and a MySQL with a `spacz` database on `localhost:3306` (`root`/`root`
by default). Either use `docker compose up -d` in `../spacz`, or a local MySQL.

```bash
cd spacz-partner-service
./mvnw spring-boot:run          # macOS/Linux
mvnw.cmd spring-boot:run        # Windows
```

- API: `http://localhost:8080` (same as the legacy backend)
- Swagger UI: `http://localhost:8080/swagger-ui.html`

If you also need the legacy app (e.g. for `/api/auth`), run one of the two on another port.
Spring Boot reads `SERVER_PORT` for both, e.g. `SERVER_PORT=8083`.

| Env var | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:mysql://localhost:3306/spacz…` / `root` / `root` | Same defaults as the legacy app |
| `JPA_DDL_AUTO` | `update` | Only adds missing tables/columns; `validate` forbids any change |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:[*],http://127.0.0.1:[*]` | Browser origins (Expo web) |

Tests use an in-memory H2 database and never touch MySQL: `./mvnw test`.

## Run with Docker

This runs only the service container. It connects to the MySQL published on host port 3306.

```bash
docker compose up -d --build      # http://localhost:8080
docker compose down
```

## API (same as the legacy backend)

| Resource | Endpoints | POST status |
|---|---|---|
| Owner | `POST/GET /api/owners`, `GET/PUT/DELETE /api/owners/{id}` | 200 |
| Property | `POST/GET /api/properties`, `GET/PUT/DELETE /api/properties/{id}` | 200 |
| Block | `POST/GET /api/blocks`, `GET/PUT/DELETE /api/blocks/{id}` | 200 |
| Seat | `POST/GET /api/seats`, `GET/PUT/DELETE /api/seats/{id}` | 200 |
| Amenity | `POST/GET /amenities`, `GET/PUT/DELETE /amenities/{id}` | 201 |
| Image | `POST/GET /images`, `GET/PUT/DELETE /images/{id}` | 201 |

Additional read-only relationship endpoints (new, optional to use):
`GET /api/owners/{id}/properties`, `GET /api/properties/{id}/blocks`,
`GET /api/properties/{id}/images`, `GET /api/blocks/{id}/seats`, `GET /api/blocks/{id}/amenity`.

### Request bodies (unchanged)

```
POST /api/owners      { ownerName, ownerEmail, ownerPhoneNumber, address, userLogin?: { loginId } }
POST /api/properties  { propertyName, address, googleCoordinates, owner: { ownerId } }
POST /images          { imageUrl, property: { propertyId } }
POST /api/blocks      { blockName, property: { propertyId }, blockDailyPrice, blockMonthlyPrice }
POST /amenities       { ac, wifi, water, lockers, newspapers, block: { blockId } }
POST /api/seats       { seatNumber, block: { blockId }, seatPrice, reserved }   ("isReserved" also accepted)
```

### Response bodies (same fields, without the infinite loop)

Every response has the same fields as the legacy entity JSON. Parents are returned as nested
objects and children as nested lists, so all of the data is still there. The only difference
is that a nested record does not point back at the record that contains it. That back-link
is what made the legacy JSON loop forever.

```jsonc
// GET /api/owners/1
{ "ownerId": 1, "ownerName": "Ravi", "ownerEmail": "...", "ownerPhoneNumber": "...", "address": "...",
  "userLogin": null,
  "properties": [ { "propertyId": 1, "propertyName": "...", "address": "...", "googleCoordinates": "...",
                    "images": [ { "imageId": 1, "imageUrl": "..." } ],
                    "blocks": [ { "blockId": 1, "blockName": "...", "blockDailyPrice": 150.0, "blockMonthlyPrice": 2500.0,
                                  "seats": [ { "seatId": 1, "seatNumber": "A1", "seatPrice": 200.0, "reserved": true } ] } ] } ] }

// GET /api/seats/1
{ "seatId": 1, "seatNumber": "A1", "seatPrice": 200.0, "reserved": true,
  "block": { "blockId": 1, "blockName": "...", "blockDailyPrice": 150.0, "blockMonthlyPrice": 2500.0,
             "property": { "propertyId": 1, "propertyName": "...", "address": "...", "googleCoordinates": "...",
                           "owner": { "ownerId": 1, "ownerName": "...", ... } } } }
```

### Differences from the legacy backend

Verified side by side against the legacy app on the same MySQL. Status codes and field values
matched on every step except the ones below.

| Case | Legacy | Partner service | Why |
|---|---|---|---|
| GET/PUT responses with related data | JSON loops until `nesting depth (1001)`, response broken | Complete, valid JSON | The fix this migration is for |
| Nested parent inside a response (e.g. `seat.block`) | carried its own child lists (`block.seats`, …) | those back-lists are left out | Breaks the loop |
| `seat.booking` | present (always `null` so far; no Booking API exists) | not returned | Booking belongs to the future User/Booking service |
| `owner.userLogin` | full login row incl. OTP | `{ "loginId": n }` or `null` | Login data belongs to the future Auth service |
| PUT on an unknown ID (owners/properties/blocks/seats) | 500 | 404 | Proper error handling |
| POST with a parent ID that doesn't exist | 500 (DB error) | 404 | Proper error handling |
| POST without a parent (e.g. a block with no `property`) | saved an orphan row | 400 | Parent relationships are validated |
| PUT without a parent | cleared the parent (orphan row) | parent left unchanged | Prevents orphaning |
| PUT property: `propertyName`, `googleCoordinates`, `owner` | ignored (code was commented out) | saved when sent | Makes PUT work; anything that worked before gives the same result |
| PUT block: `blockName`, prices | ignored (code was commented out) | saved when sent | Same |
| PUT seat: `seatPrice` | ignored | saved when sent | Same |
| Deleting a block/property/owner that has an amenity | 500 (FK on `amenity.block_id`) | deletes the amenity too | Delete now works |
| Deleting a seat that has a booking | deleted the booking too | 409, nothing deleted | This service doesn't delete another service's bookings |
| Second amenity for the same block | 500 (unique `amenity.block_id`) | 409 | Proper error handling |

Error responses are JSON: `{ timestamp, status, error, message, path }`.

Database: **no schema changes.** The entities map to the exact existing tables and columns.
The only difference is that `owner.login_id` is mapped as a plain column instead of a JPA
relationship to `user_login`; the column and its existing FK are untouched.
