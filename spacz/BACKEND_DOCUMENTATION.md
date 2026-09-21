# Spacz Backend — Complete Documentation

This document explains everything about the Spacz backend: what it does, how it's built, how the pieces fit together, and every API endpoint it exposes. It's written so that someone with basic programming knowledge (not necessarily a Java/Spring expert) can understand the whole system.

---

## 1. What is Spacz?

Spacz is a **seat/study-space booking platform**, similar in concept to booking a co-working desk or a library seat. Based on the data model, the business works like this:

- An **Owner** (a "Partner" — the business side of the platform) owns one or more **Properties** (physical buildings/locations, e.g. "Green Valley Study Center").
- Each **Property** is divided into **Blocks** (e.g. "Block A", "AC Reading Hall") — each block has its own daily/monthly pricing.
- Each **Block** contains many **Seats**, and each seat can have its own price and a "reserved" flag.
- A **Property** also has **Images** (photos shown to users) and each **Block** has an **Amenity** record (AC, Wi-Fi, water, lockers, newspapers — yes/no flags).
- An **AspirantUser** (a student/customer — the "Aspirant" side of the platform) looking for a seat) can create **Bookings**, where a booking reserves one **Seat** for a date range (`startDate` to `endDate`).
- Login for users is done via **phone number + OTP** (one-time password), tracked in the **UserLogin** table.

This is currently a **backend-only REST API** — there is no frontend in this repository.

The system really has **two distinct kinds of users** — a **Partner** (the owner who lists and manages study spaces) and an **Aspirant** (the customer who browses and books a seat). [Section 2](#2-user-flows-aspirant-vs-partner) walks through each of their journeys separately.

---

## 2. User Flows: Aspirant vs Partner

Although the codebase doesn't implement formal "roles" or permissions, the data model and the set of endpoints clearly split into two separate journeys:

- **Partner** = the property owner, modeled as the `Owner` entity — the person who lists study spaces and manages them (`/api/owners`, `/api/properties`, `/api/blocks`, `/amenities`, `/images`, `/api/seats`).
- **Aspirant** = the student/customer, modeled as the `AspirantUser` entity — the person looking to book a seat (`/aspirant-users`).

Both currently share the exact same generic OTP login endpoints (`/api/auth/register/{phoneNumber}` and `/api/auth/login/{phoneNumber}/{otp}`) — there's only one login system, not a separate one per user type. However, only `Owner` is actually **linked** to a `UserLogin` record in the data model (`Owner.userLogin`, a one-to-one relationship, and `UserLogin.isOwnerRegistered` exists specifically to flag this). `AspirantUser` has **no relationship to `UserLogin` at all**. In practice this means: today, nothing in the code connects a logged-in phone number to a specific Aspirant profile — see item 5 in [Known Gaps](#8-known-gaps--things-to-fix).

### 2.1 Partner (Owner) Flow — listing and managing a study space

This is the flow for someone who wants to list their property on Spacz. It's a top-down "build the listing" flow: Owner → Property → Blocks → (Amenities + Seats) → Images.

```
STEP 1 — Register
  POST /api/auth/register/{phoneNumber}
  → Generates an OTP from the phone number and creates a UserLogin row.

STEP 2 — Verify / Log in
  POST /api/auth/login/{phoneNumber}/{otp}
  → Confirms the OTP matches. No token is returned (see §7.1).

STEP 3 — Create the Owner (Partner) profile
  POST /api/owners
  Body: { ownerName, ownerEmail, ownerPhoneNumber, address }
  ⚠️ Nothing in the code automatically links this new Owner back to the
     UserLogin created in Step 1 — there's no server-side logic that does
     this linking for you.

STEP 4 — Create a Property under that owner
  POST /api/properties
  Body: { propertyName, address, googleCoordinates, owner: { ownerId } }

STEP 5 — Add Images to the property
  POST /images   (repeat once per photo)
  Body: { imageUrl, property: { propertyId } }

STEP 6 — Create Blocks inside the property
  POST /api/blocks   (repeat once per block/hall, e.g. "AC Zone", "Silent Room")
  Body: { blockName, property: { propertyId }, blockDailyPrice, blockMonthlyPrice }

STEP 7 — Set Amenities for each block
  POST /amenities
  Body: { ac, wifi, water, lockers, newspapers, block: { blockId } }

STEP 8 — Create Seats inside each block
  POST /api/seats   (repeat once per seat)
  Body: { seatNumber, block: { blockId }, seatPrice, isReserved: false }

STEP 9 — Manage / edit the listing later
  - Update property : PUT /api/properties/{id}  (⚠️ only `address` actually saves — §8)
  - Update block     : PUT /api/blocks/{id}      (⚠️ only the `property` link actually saves — §8)
  - Update seat       : PUT /api/seats/{id}       (fully works)
  - Delete anything   : DELETE .../{id}
                         ⚠️ Deleting an Owner or Property cascades and deletes
                            everything underneath it (Blocks, Amenities, Seats,
                            Images) in one shot — see §7.2, Cascading deletes.
```

**Summary:** the Partner is the *root* of everything — every other record (Property, Block, Amenity, Seat, Image) ultimately traces back to one `Owner`. All of these endpoints support full create/read/update/delete, so a partner can fully build and maintain a listing through the API today.

### 2.2 Aspirant Flow — finding and booking a seat

This is the flow for a student/customer looking for a place to study. It's a top-down "browse" flow: Properties → Blocks → Seats/Amenities/Images — but it currently **stops short of the actual booking step**.

```
STEP 1 — Register
  POST /api/auth/register/{phoneNumber}
  → Same shared OTP endpoint as the partner flow.

STEP 2 — Verify / Log in
  POST /api/auth/login/{phoneNumber}/{otp}
  → Same shared OTP endpoint as the partner flow.

STEP 3 — Create the Aspirant (customer) profile
  POST /aspirant-users
  Body: { name, phoneNumber, aadharNumber, email, currentAddress, permanentAddress }
  ⚠️ Same disconnect as the partner flow: this profile is not automatically
     linked back to the UserLogin created in Step 1 (and in this case, there
     isn't even a database field to link them — see §2 intro above).

STEP 4 — Browse properties, blocks, seats
  GET /api/properties         → list every property
  GET /api/properties/{id}    → view one property's details
  GET /api/blocks             → list blocks (client filters by propertyId)
  GET /amenities               → list amenity records (client filters by blockId)
  GET /api/seats                → list seats (client filters by blockId)
  GET /images                    → list photos (client filters by propertyId)

  ⚠️ There is no dedicated "search" or "filter" endpoint (by city, price
     range, amenity, or seat availability) — a client has to fetch the full
     lists above and filter them itself.

STEP 5 — Book a seat
  ❌ NOT POSSIBLE TODAY.
  There is no BookingController / BookingService / BookingRepository.
  The `Booking` entity exists in the data model (it links a Seat + an
  AspirantUser + a start/end date), but no HTTP endpoint exposes it at all.
  See §8, Known Gaps, item 5.

STEP 6 — Manage own profile
  PUT /aspirant-users/{id}      → update profile details
  DELETE /aspirant-users/{id}   → delete profile
```

**Summary:** the aspirant flow works fine for *browsing* (properties → blocks → seats → amenities/images), but it **dead-ends right at the moment a customer would actually book a seat** — the one action this whole flow exists to support isn't implemented yet.

### 2.3 Side-by-side comparison

| | Partner (Owner) | Aspirant (Customer) |
|---|---|---|
| Login | OTP via `/api/auth` (shared endpoint) | OTP via `/api/auth` (shared endpoint) |
| Profile entity & endpoint | `Owner` → `/api/owners` | `AspirantUser` → `/aspirant-users` |
| Linked to `UserLogin`? | Has a `userLogin` field, but nothing auto-links it | No relationship to `UserLogin` at all |
| Direction of the flow | Top-down *build*: Owner → Property → Block → Seat/Amenity → Image | Top-down *browse*: Property → Block → Seat/Amenity/Image |
| Primary actions available | Full create/update/delete on every entity they manage | Read-only browsing (list/get) of properties, blocks, seats, amenities, images |
| Core "final" action of the flow | Publish a bookable listing | Book a seat |
| Is that final action implemented? | ✅ Yes — properties/blocks/seats/amenities/images are fully manageable | ❌ No — `Booking` has no API yet (see §8) |

---

## 3. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.2 |
| Web layer | Spring Web (REST controllers, embedded Tomcat server) |
| Data layer | Spring Data JPA (Hibernate as the ORM) |
| Database (production) | MySQL 8 |
| Database (available but unused) | H2 (in-memory, listed as a dependency but no profile currently uses it) |
| API docs | springdoc-openapi (generates Swagger UI automatically) |
| Build tool | Maven (with the `mvnw` wrapper, so you don't need Maven installed globally) |
| Testing | Spring Boot Test / JUnit (starter is present, but no real tests are written yet) |

**What is Spring Boot, briefly?** It's a Java framework that lets you build a web server (REST API) by writing plain Java classes and annotating them (`@RestController`, `@Entity`, etc.). Spring Boot auto-configures a lot of boilerplate (database connections, JSON conversion, the embedded web server) so you mostly write business logic.

---

## 4. Project Structure

The actual source code lives inside the `spacz/` folder (there's also a duplicate `__MACOSX/` folder at the repo root from a zip extraction on macOS — it contains no real code and can be ignored/deleted).

```
spacz/
├── pom.xml                          ← Maven build file (dependencies, Java version)
├── mvnw / mvnw.cmd                  ← Maven wrapper scripts (run without installing Maven)
├── src/
│   ├── main/
│   │   ├── java/com/studyhouse/spacz/
│   │   │   ├── SpaczApplication.java        ← main() entry point that starts the server
│   │   │   ├── controller/                  ← REST API endpoints (the "front door")
│   │   │   ├── service/                     ← business logic layer
│   │   │   ├── repository/                  ← database access layer (Spring Data JPA)
│   │   │   └── entity/                      ← database tables, modeled as Java classes
│   │   └── resources/
│   │       └── application.properties       ← configuration (DB connection, etc.)
│   └── test/
│       └── java/.../SpaczApplicationTests.java  ← empty placeholder test
```

### The layered architecture

This project follows a classic **4-layer Spring Boot design**:

```
HTTP request
    │
    ▼
Controller   (@RestController)  → reads the URL/JSON, calls a service, returns JSON
    │
    ▼
Service      (interface + *Impl) → business logic, decides what to do
    │
    ▼
Repository   (interface only)    → talks to the database (Spring generates the code)
    │
    ▼
Entity       (@Entity classes)   → represents a row in a database table
    │
    ▼
MySQL Database
```

For **every** business object (Amenity, AspirantUser, Block, Image, Owner, Property, Seat), the same 4-file pattern repeats:
`XController.java` → `XService.java` (interface) → `XServiceImpl.java` (implementation) → `XRepository.java`.

The one exception is **Booking** and **UserLogin**:
- `Booking` has an **entity** but **no controller/service/repository** — there is currently no API to create or view bookings (see [Section 8, Known Gaps](#8-known-gaps--things-to-fix)).
- `UserLogin` has no dedicated controller — it's handled through `AuthController`, and its service is called `UserService` (not `UserLoginService`).

---

## 5. Database Design (Entities & Relationships)

Every entity class in `entity/` becomes a MySQL table automatically (Hibernate creates/updates tables on startup because `spring.jpa.hibernate.ddl-auto=update` is set — more on this in [Section 7.4](#74-configuration-applicationproperties)).

### 5.1 Relationship diagram

```
Owner  ──1───────*  Property  ──1───────*  Image
  │                     │
  │(1:1)                │(1:*)
  │                     ▼
UserLogin              Block  ──1───────1  Amenity
                         │
                         │(1:*)
                         ▼
                        Seat  ──1───────1  Booking  *───────1  AspirantUser
```

### 5.2 Entities explained one by one

#### **Owner** — the person who owns study properties (the "Partner")
| Field | Type | Notes |
|---|---|---|
| ownerId | Long | primary key, auto-generated |
| ownerName | String | |
| ownerEmail | String | |
| ownerPhoneNumber | String | |
| address | String | |
| userLogin | UserLogin (1:1) | links the owner to their login/OTP record |
| properties | List\<Property\> (1:many) | all properties this owner has |

#### **UserLogin** — phone + OTP based login record
| Field | Type | Notes |
|---|---|---|
| loginId | Long | primary key |
| phoneNumber | String | used as the "username" |
| otp | String | the currently valid one-time password, stored as plain text |
| isOwnerRegistered | boolean | flags whether this login belongs to a registered owner |

#### **Property** — a physical building/location
| Field | Type | Notes |
|---|---|---|
| propertyId | Long | primary key |
| propertyName | String | |
| address | String | |
| googleCoordinates | String | e.g. lat,long for maps |
| owner | Owner (many:1) | which owner this property belongs to |
| images | List\<Image\> (1:many) | photos of the property |
| blocks | List\<Block\> (1:many) | the blocks/halls inside this property |

#### **Image** — a photo attached to a property
| Field | Type | Notes |
|---|---|---|
| imageId | Long | primary key |
| imageUrl | String | link to the image file (stored externally, e.g. cloud storage — this app only stores the URL, not the file itself) |
| property | Property (many:1) | which property this image belongs to |

#### **Block** — a section/hall inside a property (e.g. "AC Zone", "Silent Room")
| Field | Type | Notes |
|---|---|---|
| blockId | Long | primary key |
| blockName | String | |
| property | Property (many:1) | parent property |
| seats | List\<Seat\> (1:many) | all seats inside this block |
| blockDailyPrice | double | default daily price for seats in this block |
| blockMonthlyPrice | double | default monthly price for seats in this block |

#### **Amenity** — facilities available in a block (one record per block)
| Field | Type | Notes |
|---|---|---|
| amenityId | Long | primary key |
| ac | boolean | air conditioning available? |
| wifi | boolean | |
| water | boolean | |
| lockers | boolean | |
| newspapers | boolean | |
| block | Block (1:1) | which block this amenity record describes |

#### **Seat** — an individual bookable seat
| Field | Type | Notes |
|---|---|---|
| seatId | Long | primary key |
| seatNumber | String | e.g. "A1", "B12" |
| block | Block (many:1) | parent block |
| isReserved | boolean | whether the seat is currently taken |
| booking | Booking (1:1) | the current/most recent booking on this seat (if any) |
| seatPrice | double | this seat's own price (can override the block's default price) |

#### **AspirantUser** — a student/customer looking to book a seat (the "Aspirant")
| Field | Type | Notes |
|---|---|---|
| aspirantUserId | Long | primary key |
| name | String | |
| phoneNumber | String | |
| aadharNumber | String | Indian national ID number |
| email | String | |
| currentAddress | String | |
| permanentAddress | String | |
| bookings | List\<Booking\> (1:many) | all bookings made by this user |

#### **Booking** — reserves one seat for a date range
| Field | Type | Notes |
|---|---|---|
| bookingId | Long | primary key |
| startDate | LocalDate | booking start date |
| endDate | LocalDate | booking end date |
| seat | Seat (1:1) | the seat being booked |
| aspirantUser | AspirantUser (many:1) | who made the booking |

> **Note:** `Booking` exists purely as a database table right now — there's no REST endpoint to create/list/cancel bookings yet. See [Section 8](#8-known-gaps--things-to-fix).

---

## 6. API Reference (every endpoint)

The base URL when running locally is `http://localhost:8080` (default Spring Boot port; not overridden in config).

Auto-generated interactive API docs are also available once the app is running, thanks to `springdoc-openapi`:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 6.1 Auth — `/api/auth` (`AuthController`) — used by both Partner and Aspirant

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/api/auth/register/{phoneNumber}` | — | Generates an OTP for the given phone number and saves a new `UserLogin` row. Returns `"Registration successful. OTP sent."` |
| POST | `/api/auth/login/{phoneNumber}/{otp}` | — | Checks if the OTP matches what's stored for that phone number. Returns `200 OK` with a success message, or `401 Unauthorized` with `"Invalid OTP."` |

**How the OTP is generated (see [Section 7.1](#71-the-otp-authentication-flow) for full detail):** it's simply the last 4 digits of the phone number + the first digit of the phone number. It is **not** actually sent via SMS anywhere in the code — the comment says so but there's no SMS integration.

### 6.2 Owners (Partner) — `/api/owners` (`OwnerController`)

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/api/owners` | `Owner` JSON | Create a new owner |
| GET | `/api/owners` | — | List all owners |
| GET | `/api/owners/{id}` | — | Get one owner by ID (404 if not found) |
| PUT | `/api/owners/{id}` | `Owner` JSON | Update an owner's name/phone/email/address |
| DELETE | `/api/owners/{id}` | — | Delete an owner |

### 6.3 Properties (Partner) — `/api/properties` (`PropertyController`)

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/api/properties` | `Property` JSON | Create a new property |
| GET | `/api/properties` | — | List all properties |
| GET | `/api/properties/{id}` | — | Get one property by ID (404 if not found) |
| PUT | `/api/properties/{id}` | `Property` JSON | Update a property (⚠️ only the `address` field actually gets updated — see [Section 8](#8-known-gaps--things-to-fix)) |
| DELETE | `/api/properties/{id}` | — | Delete a property |

### 6.4 Blocks (Partner) — `/api/blocks` (`BlockController`)

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/api/blocks` | `Block` JSON | Create a new block |
| GET | `/api/blocks` | — | List all blocks |
| GET | `/api/blocks/{id}` | — | Get one block by ID (404 if not found) |
| PUT | `/api/blocks/{id}` | `Block` JSON | Update a block (⚠️ only the `property` link is actually updated, not the name/prices — see [Section 8](#8-known-gaps--things-to-fix)) |
| DELETE | `/api/blocks/{id}` | — | Delete a block |

### 6.5 Seats (Partner) — `/api/seats` (`SeatController`)

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/api/seats` | `Seat` JSON | Create a new seat |
| GET | `/api/seats` | — | List all seats |
| GET | `/api/seats/{id}` | — | Get one seat by ID (404 if not found) |
| PUT | `/api/seats/{id}` | `Seat` JSON | Update a seat (block, seat number, reserved flag — this one updates correctly) |
| DELETE | `/api/seats/{id}` | — | Delete a seat |

### 6.6 Amenities (Partner) — `/amenities` (`AmenityController`)

> Note this one does **not** have the `/api` prefix that the other newer controllers use — see [Section 8](#8-known-gaps--things-to-fix).

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/amenities` | `Amenity` JSON | Create a new amenity record |
| GET | `/amenities` | — | List all amenities |
| GET | `/amenities/{id}` | — | Get one amenity by ID (404 if not found) |
| PUT | `/amenities/{id}` | `Amenity` JSON | Update an amenity (fully replaces the record) |
| DELETE | `/amenities/{id}` | — | Delete an amenity (204 No Content on success, 404 if not found) |

### 6.7 Images (Partner) — `/images` (`ImageController`)

> Also without the `/api` prefix.

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/images` | `Image` JSON | Create a new image record (just stores a URL) |
| GET | `/images` | — | List all images |
| GET | `/images/{id}` | — | Get one image by ID (404 if not found) |
| PUT | `/images/{id}` | `Image` JSON | Update an image (fully replaces the record) |
| DELETE | `/images/{id}` | — | Delete an image |

### 6.8 Aspirant Users — `/aspirant-users` (`AspirantUserController`)

> Also without the `/api` prefix.

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/aspirant-users` | `AspirantUser` JSON | Create a new aspirant user (student/customer) |
| GET | `/aspirant-users` | — | List all aspirant users |
| GET | `/aspirant-users/{id}` | — | Get one aspirant user by ID (404 if not found) |
| PUT | `/aspirant-users/{id}` | `AspirantUser` JSON | Update an aspirant user (fully replaces the record) |
| DELETE | `/aspirant-users/{id}` | — | Delete an aspirant user |

### 6.9 What's *missing* from the API

- **No `/bookings` endpoint** — `Booking` is a full JPA entity with relationships to `Seat` and `AspirantUser`, but nothing lets a client actually create/view/cancel a booking through HTTP. This is the step where the Aspirant flow in [Section 2.2](#22-aspirant-flow--finding-and-booking-a-seat) currently dead-ends.
- **No endpoint to list "seats available in a block/property"** — a real seat-booking product would need this; right now you'd have to fetch all seats and filter client-side.

---

## 7. How Things Work Internally

### 7.1 The OTP authentication flow

This is the closest thing the app has to a "feature with logic," so it's worth walking through in detail.

**Step 1 — Register / request an OTP**
```
POST /api/auth/register/9876543210
```
`UserServiceImpl.generateOtp()` does this:
```java
String lastFourDigits = phoneNumber.substring(phoneNumber.length() - 4); // "3210"
String firstDigit = String.valueOf(phoneNumber.charAt(0));               // "9"
return lastFourDigits + firstDigit;                                      // "32109"
```
So for phone number `9876543210`, the OTP would always be `32109`. This value is then saved into the `UserLogin` table via `register()`.

**Step 2 — Log in with the OTP**
```
POST /api/auth/login/9876543210/32109
```
`UserServiceImpl.authenticate()` looks up the `UserLogin` row by phone number and checks if the stored `otp` string equals the one supplied in the URL. If it matches → success; otherwise → `401 Unauthorized`.

**Important characteristics of this design (see [Section 8](#8-known-gaps--things-to-fix) for why these matter):**
- The OTP is **deterministic** — anyone who knows the phone number can compute the OTP themselves, because it's just digits taken directly from the number. It is not random and not secret.
- No actual SMS/text message is ever sent. The comment `// Send OTP to the user's phone number via SMS` in `AuthController` is just a comment — there's no SMS provider integration (e.g. Twilio) in this code.
- The OTP doesn't expire and isn't a one-time-use value (nothing clears it after a successful login).
- Login doesn't return any token (like a JWT). After a successful login, the client has no credential to prove it's logged in on future requests — every other endpoint in the app is completely open/unauthenticated.
- This single login mechanism is shared by both the Partner and Aspirant flows (see [Section 2](#2-user-flows-aspirant-vs-partner)) — the backend has no concept of "log in as an owner" vs. "log in as an aspirant."

### 7.2 Cascading deletes

Several relationships use `cascade = CascadeType.ALL`:
- `Owner → Property`
- `Property → Image`, `Property → Block`
- `Block → Seat`
- `AspirantUser → Booking`

This means: **if you delete an Owner, JPA will also delete all of their Properties** (and in turn, cascades down to Images/Blocks/Seats too, because those cascades are also `ALL`). Be careful with `DELETE /api/owners/{id}` in a real environment — it can wipe out a large tree of related data in one call. This is the same cascade referenced in Step 9 of the [Partner flow](#21-partner-owner-flow--listing-and-managing-a-study-space).

### 7.3 How updates ("PUT") behave — and where they're inconsistent

Two different update styles are used across services, and they behave differently:

**Style A — "merge specific fields" (safer, partial-update-like)**
Used by `OwnerServiceImpl.updateOwner()`, `PropertyServiceImpl.updateProperty()`, `BlockServiceImpl.updateBlock()`, `SeatServiceImpl.updateSeat()`: the code fetches the existing row, copies a few fields from the incoming JSON onto it, and saves. This is more defensive because relationships that weren't sent won't be wiped.

**Style B — "replace the whole entity" (simpler, riskier)**
Used by `AmenityServiceImpl`, `AspirantUserServiceImpl`, `ImageServiceImpl`: the code just takes the incoming JSON object, force-sets its ID to match the URL's `{id}`, and saves it directly. If the client's JSON is missing a field (e.g. forgets to send `block` when updating an `Amenity`), that field gets overwritten with `null`/`false` in the database, because JPA treats this as a full replace, not a partial patch.

Additionally, in Style A, **not all fields are actually copied**:
- `PropertyServiceImpl.updateProperty()` only copies `address`. The `propertyName` and `owner` lines are commented out — so calling `PUT /api/properties/{id}` with a new `propertyName` silently has no effect.
- `BlockServiceImpl.updateBlock()` only copies `property`. The `blockName` line is commented out (and a `numberOfRooms` field referenced in a comment doesn't even exist on the `Block` entity) — so you currently **cannot update a block's name or its prices** through the API at all.

### 7.4 Configuration (`application.properties`)

```properties
spring.application.name=spacz

# MySQL Database Configuration
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/spacz?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:root}

# Hibernate/JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

spring.cache.type=simple
```

Plain-language explanation of each line:
- `spring.datasource.url/username/password` — connects to a MySQL server running on `localhost:3306`, database name `spacz`, using the `root` user with password `root` by default. **This means the app expects a local MySQL server to already be running with a database called `spacz` created** — see [Section 9](#9-how-to-run-this-locally) for the one-command way to get that via Docker. The `${DB_URL:...}`/`${DB_USERNAME:...}`/`${DB_PASSWORD:...}` syntax means "use env var `DB_URL` if set, otherwise fall back to the default after the colon" — so every developer gets working defaults with zero config, but can override any of them (e.g. to point at a different DB) without editing a tracked file.
- `spring.jpa.hibernate.ddl-auto=update` — on every app startup, Hibernate looks at your `@Entity` classes and automatically creates missing tables/columns in the database, or updates them to match. You don't write any SQL migration files. Convenient for development, but risky for production (it never deletes/renames columns safely, and could behave unexpectedly on a shared database).
- `spring.jpa.show-sql=true` + `format_sql=true` — every SQL query Hibernate runs gets printed to the console, nicely formatted. Useful for debugging, noisy in production.
- `spring.cache.type=simple` — enables a very basic in-memory cache (not actually used anywhere in the code right now — no `@Cacheable` annotations exist).

There is only **one** `application.properties` file — there are no separate profiles for `dev`/`test`/`prod` (e.g. `application-prod.properties`).

---

## 8. Known Gaps / Things to Fix

These aren't necessarily "bugs" to panic about (this looks like an early-stage/demo project — the Maven `<description>` literally says *"Demo project for Spring Boot"*), but they're worth knowing before this goes anywhere near production:

1. **No security at all.** There's no Spring Security dependency, no JWT, no session handling. Every endpoint (create/read/update/delete owners, properties, seats, etc.) is fully public — anyone who can reach the server can call any endpoint.
2. **OTP is not secret and not sent anywhere.** As explained in [7.1](#71-the-otp-authentication-flow), the OTP is derived deterministically from the phone number itself, so it provides no real security, and no SMS is actually sent.
3. **Login doesn't produce a token.** Since there's no JWT/session, "being logged in" isn't something the backend can check on later requests.
4. **Passwords/DB credentials default to `root`/`root`** in `application.properties`, now overridable via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars (see [7.4](#74-configuration-applicationproperties)). Fine for shared local dev (matches `docker-compose.yml`); for any real deployment these should be set via env vars/secrets manager and the checked-in defaults should not be relied on.
5. **`Booking` has no API, and neither Owner nor AspirantUser is truly linked to `UserLogin` end-to-end.** The `Booking` entity and its relationships exist, but there's no `BookingController`/`BookingService`/`BookingRepository`, so seats can never actually be booked through this API yet — this is the exact point where the [Aspirant flow](#22-aspirant-flow--finding-and-booking-a-seat) breaks down. Separately, `AspirantUser` has no relationship to `UserLogin` at all, and even `Owner.userLogin` has no automatic linking logic — see [Section 2](#2-user-flows-aspirant-vs-partner).
6. **Inconsistent URL prefixes.** `AuthController`, `OwnerController`, `PropertyController`, `BlockController`, `SeatController` use `/api/...`, while `AmenityController`, `ImageController`, `AspirantUserController` don't have the `/api` prefix. Worth standardizing.
7. **Some PUT/update methods silently ignore fields:**
   - `PropertyServiceImpl.updateProperty()` only updates `address` (name/owner changes are ignored).
   - `BlockServiceImpl.updateBlock()` only updates the linked `property` (name/prices can't be changed via the API).
8. **No input validation.** None of the request DTOs use `@Valid`/`@NotNull`/etc. — you can POST an `Owner` with a `null` name and it will save fine. There's also no dedicated DTO layer — entities are used directly as request/response bodies, which means internal DB structure (and full relationship graphs) is exposed directly over the API.
9. **Generic exception handling.** Several services throw a plain `new RuntimeException("X not found")` instead of a proper custom exception, and there's no global `@ControllerAdvice` to turn these into clean JSON error responses — a `RuntimeException` like this will currently surface as a generic `500 Internal Server Error`.
10. **`Owner` entity has non-standard getter/setter casing** — `getownerId()`/`setownerId()`/`getowner()`/`setowner()` (lowercase "o") instead of the conventional `getOwnerId()`/`setOwnerId()`/`getOwner()`/`setOwner()`. This still works but is inconsistent with normal Java naming conventions and could trip up JSON (de)serialization expectations.
11. **No pagination.** `GET` "list all" endpoints (e.g. `GET /api/properties`) return every row in the table with no paging — fine for a demo, but will not scale.
12. **`H2` dependency is present but unused** — there's no test/dev profile configured to actually use the in-memory H2 database, so every run (including local dev) needs a real MySQL instance.
13. **Duplicate project files in this repo.** There's a `__MACOSX/spacz` folder (macOS zip-extraction artifacts) sitting alongside the real `spacz/` folder — it contains no real code and can likely be deleted to avoid confusion.

---

## 9. How to Run This Locally

See **[`SETUP.md`](./SETUP.md)** for the full step-by-step developer setup guide (prerequisites, Docker-based MySQL setup, credential overrides, manual fallback without Docker, and troubleshooting).

Short version: `docker compose up -d` to start MySQL, then `./mvnw spring-boot:run` (or `mvnw.cmd spring-boot:run` on Windows). The API is then available at `http://localhost:8080`, with Swagger UI at `http://localhost:8080/swagger-ui/index.html`. Hibernate auto-creates all the tables listed in [Section 5](#5-database-design-entities--relationships) the first time it starts.

---

## 10. Quick Glossary (Spring Boot terms used above)

| Term | Meaning |
|---|---|
| `@RestController` | Marks a class as one that handles HTTP requests and returns JSON responses directly (as opposed to rendering HTML pages). |
| `@RequestMapping` / `@GetMapping` / `@PostMapping` / etc. | Maps a URL + HTTP method to a Java method. |
| `@Entity` | Marks a Java class as being backed by a database table. |
| `@Id` / `@GeneratedValue` | Marks the primary key field and tells Hibernate to auto-increment it. |
| `@OneToOne` / `@OneToMany` / `@ManyToOne` | Describes how two tables relate to each other (foreign keys), mirrored as Java object references/lists. |
| `JpaRepository<Entity, IdType>` | An interface you extend that gives you `findAll()`, `findById()`, `save()`, `deleteById()`, etc. "for free" — Spring generates the implementation at startup. No SQL needs to be written for basic CRUD. |
| `@Service` | Marks a class as holding business logic, sitting between controllers and repositories. |
| `ResponseEntity<T>` | A wrapper Spring uses to control both the HTTP status code (200, 404, etc.) and the response body together. |
| `ddl-auto=update` | Tells Hibernate to auto-create/alter database tables to match your `@Entity` classes on every startup. |
| DTO (Data Transfer Object) | A plain object used just for API request/response shaping, separate from the database entity — **this project doesn't currently use DTOs**, so raw entities go over the wire. |

---

*Document generated by analyzing the source code in `spacz/src/main/java/com/studyhouse/spacz/` as of the current repository state.*
