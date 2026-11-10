# SPACZ Partner API

Every backend route the SPACZ Partner mobile app uses, taken from the backend version running in production on 4 November 2026.

**Base URL:** `https://api.spacz.in`

In the tables below:

- `*` marks a required field.
- **Vendor** means a logged-in account with the `VENDOR` role. Partners are vendor accounts.
- **Logged in** means any logged-in account.
- **Public** means no token is needed.

## What changed from the old Partner backend

The owner, property, block, seat, amenity and image paths and JSON stayed the same, but the app must make these changes:

1. **Use the new base URL:** send every call to `https://api.spacz.in`.
2. **Use the new login:** `POST /api/auth/otp/request` and `POST /api/auth/otp/verify` replace `/api/auth/register/{phone}` and `/api/auth/login/{phone}/{otp}`.
3. **Send the token:** every Partner route needs `Authorization: Bearer <accessToken>`.
4. **Expect only your own data:** `GET /api/owners` returns a list with just the logged-in partner's owner record.
5. **Don't reuse saved IDs:** get them again from `GET /api/owners` after login.

## Know before you start

- **Phone OTP:** SMS isn't connected yet, so codes don't reach phones or appear in API responses. Use email and password sign-up for testing. Phone login needs SMS before launch.
- **Swagger:** the interactive docs show the wrong request and response fields for some Partner routes, including amenities. Use this file for those routes.
- **Image files:** there is no file upload route. Upload the file elsewhere first, then send its URL as `imageUrl`.
- **Browser testing:** native apps aren't affected by browser address rules, but Expo web is. Browser calls are accepted from `http://localhost:3000` and `http://localhost:5173` only.
- **Production data:** there is no separate test environment, so test accounts and data are real records.
- **Test coverage:** the Partner routes were checked without a token and correctly refused it (`401`). Complete logged-in flows haven't been run against production yet, so report any surprises with the `correlationId` where the error includes one.

## Connect the app

- Access tokens last 15 minutes. On a `401`, call `/api/auth/refresh` once and retry.
- Refresh tokens are single use. Store the new one each time, and share one refresh call across parallel requests, because reusing an old token signs the account out everywhere.
- Partner lists return plain JSON arrays and don't use paging.
- Errors return `{status, error, message, path, correlationId, fieldErrors}`. Branch on `error` and map `fieldErrors` to form fields.
- Prices are numbers in INR.

## Setup order

1. Log in, then call `GET /api/owners` to find the `ownerId`.
2. If the list is empty, or the account has never been submitted, create the owner with `POST /api/owners`. This also sends the partner for admin approval.
3. Create a property with `POST /api/properties`. This also sends the property for admin approval.
4. Create blocks with `POST /api/blocks`.
5. Add seats with `POST /api/seats`. Each one is placed in the next free position in the block.
6. Add block amenities with `POST /amenities` and property photos with `POST /images`.

## Sign-up and login

| Method | Path | Access | Purpose | Body |
|---|---|---|---|---|
| POST | `/api/auth/otp/request` | Public | Send a one-time code to a phone. Not usable until SMS is connected. | `phone*` |
| POST | `/api/auth/otp/verify` | Public | Check the code. A new number returns `422 REGISTRATION_REQUIRED`; repeat with the same code and `role`. | `phone*`, `code*`, `role` (`"VENDOR"`), `businessName`, `contactName`, `city` |
| POST | `/api/auth/register/vendor` | Public | Create a vendor account with email and password. Use this for testing. | `email*`, `password*`, `businessName*`, `contactName*`, `phone*`, `city` |
| POST | `/api/auth/login` | Public | Log in with email or phone and a password. | `email*`, `password*` |
| POST | `/api/auth/refresh` | Public | Swap a refresh token for a new token pair. | `refreshToken*` |
| POST | `/api/auth/logout` | Public | Revoke a refresh token. Returns `204`. | `refreshToken*` |
| GET | `/api/auth/me` | Logged in | The logged-in account. | |
| PUT | `/api/auth/me/password` | Logged in | Set or change the password. Signs out other sessions. Returns `204`. | `currentPassword`, `newPassword*` |

Passwords must be 8-72 characters with at least one letter and one digit.

## Owners

An owner is the logged-in partner's account record. In the wider platform, `ownerId` is the vendor profile.

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---|
| GET | `/api/owners` | Vendor | A list containing my owner record, with properties, blocks, seats and images. | `200` |
| POST | `/api/owners` | Vendor | Create or complete my owner record and send it for approval. Returns `409` if it was already submitted. | `200` |
| GET | `/api/owners/{id}` | Vendor | My owner record. | `200` |
| PUT | `/api/owners/{id}` | Vendor | Update my owner record. | `200` |
| DELETE | `/api/owners/{id}` | Vendor | Delete my owner record and **all of its properties**. Returns `409` if any have bookings. | `204` |
| GET | `/api/owners/{id}/properties` | Vendor | My properties. | `200` |

**Body for POST and PUT:**

```json
{
  "ownerName": "Ravi Kumar",
  "ownerEmail": "ravi@example.com",
  "ownerPhoneNumber": "9876543210",
  "address": "Ameerpet, Hyderabad"
}
```

## Properties

A property is a study hall. In the wider platform, `propertyId` is the study hall ID.

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---|
| GET | `/api/properties` | Vendor | My properties, with blocks, seats and images. | `200` |
| POST | `/api/properties` | Vendor | Create a property and send it for admin approval. | `200` |
| GET | `/api/properties/{id}` | Vendor | One property. | `200` |
| PUT | `/api/properties/{id}` | Vendor | Update a property. Fields you leave out stay as they are. | `200` |
| DELETE | `/api/properties/{id}` | Vendor | Delete a property. Returns `409` if it has bookings. | `204` |
| GET | `/api/properties/{id}/blocks` | Vendor | Blocks of a property. | `200` |
| GET | `/api/properties/{id}/images` | Vendor | Images of a property. | `200` |

**Body for POST and PUT** (`owner.ownerId` is required on POST):

```json
{
  "propertyName": "Sri Study Hall",
  "address": "Ameerpet, Hyderabad",
  "googleCoordinates": "17.4375,78.4483",
  "owner": { "ownerId": 1 }
}
```

Write `googleCoordinates` as `latitude,longitude`. If the value can't be read as coordinates, the location is saved as empty and no error is returned.

## Blocks

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---|
| GET | `/api/blocks` | Vendor | My blocks with their seats. | `200` |
| POST | `/api/blocks` | Vendor | Create a block in a property. | `200` |
| GET | `/api/blocks/{id}` | Vendor | One block. | `200` |
| PUT | `/api/blocks/{id}` | Vendor | Update a block. Fields you leave out stay as they are. Moving a block with bookings to another property returns `409`. | `200` |
| DELETE | `/api/blocks/{id}` | Vendor | Delete a block. Returns `409` if it has bookings or assigned students. | `204` |
| GET | `/api/blocks/{id}/seats` | Vendor | Seats of a block. | `200` |
| GET | `/api/blocks/{id}/amenity` | Vendor | Amenities of a block. Returns `404` if none are set. | `200` |

**Body for POST and PUT** (`property.propertyId` is required on POST):

```json
{
  "blockName": "AC Reading Hall",
  "property": { "propertyId": 1 },
  "blockDailyPrice": 150.0,
  "blockMonthlyPrice": 2500.0
}
```

## Seats

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---|
| GET | `/api/seats` | Vendor | All my seats. | `200` |
| POST | `/api/seats` | Vendor | Add a seat to a block. A seat number already used in that property returns `409`. | `200` |
| GET | `/api/seats/{id}` | Vendor | One seat. | `200` |
| PUT | `/api/seats/{id}` | Vendor | Update a seat. Moving a seat with bookings or an assigned student to another block returns `409`. | `200` |
| DELETE | `/api/seats/{id}` | Vendor | Delete a seat. Returns `409` if it has bookings or an assigned student. | `204` |

**Body for POST and PUT** (`block.blockId` is required on POST):

```json
{
  "seatNumber": "A1",
  "block": { "blockId": 1 },
  "reserved": false,
  "seatPrice": 200.0
}
```

- **Always send `reserved` on PUT.** Leaving it out clears the reservation.
- `isReserved` is also accepted in place of `reserved`.
- `seatPrice` is the seat's daily price.

## Amenities

Each block has one amenity record, and its `amenityId` is the same as its `blockId`. Use the block ID in the `/amenities/{id}` routes.

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---|
| GET | `/amenities` | Vendor | Amenity records of my blocks. | `200` |
| POST | `/amenities` | Vendor | Set a block's amenities. Returns `409` if the block already has a record. | `201` |
| GET | `/amenities/{id}` | Vendor | One block's amenities. | `200` |
| PUT | `/amenities/{id}` | Vendor | Replace a block's amenities, so send every value. | `200` |
| DELETE | `/amenities/{id}` | Vendor | Clear a block's amenities. | `204` |

**Body for POST and PUT** (`block.blockId` is required on POST):

```json
{
  "ac": true,
  "wifi": true,
  "water": true,
  "lockers": false,
  "newspapers": false,
  "block": { "blockId": 1 }
}
```

## Images

| Method | Path | Access | Purpose | Success |
|---|---|---|---|---|
| GET | `/images` | Vendor | All my property images. | `200` |
| POST | `/images` | Vendor | Add an image by URL. | `201` |
| GET | `/images/{id}` | Vendor | One image. | `200` |
| PUT | `/images/{id}` | Vendor | Update an image. | `200` |
| DELETE | `/images/{id}` | Vendor | Remove an image. | `204` |

**Body for POST and PUT** (`imageUrl` and `property.propertyId` are required on POST):

```json
{
  "imageUrl": "https://cdn.spacz.in/halls/12/front.jpg",
  "property": { "propertyId": 1 }
}
```

## Response examples

Lists and single records use the same record shape.

```jsonc
// GET /api/owners
[
  {
    "ownerId": 1,
    "ownerName": "Ravi Kumar",
    "ownerEmail": "ravi@example.com",
    "ownerPhoneNumber": "9876543210",
    "address": "Ameerpet, Hyderabad",
    "userLogin": { "loginId": 7 },
    "properties": [
      {
        "propertyId": 1,
        "propertyName": "Sri Study Hall",
        "address": "Ameerpet, Hyderabad",
        "googleCoordinates": "17.4375,78.4483",
        "images": [ { "imageId": 1, "imageUrl": "https://cdn.spacz.in/halls/12/front.jpg" } ],
        "blocks": [
          {
            "blockId": 1,
            "blockName": "AC Reading Hall",
            "blockDailyPrice": 150.0,
            "blockMonthlyPrice": 2500.0,
            "seats": [ { "seatId": 1, "seatNumber": "A1", "seatPrice": 200.0, "reserved": false } ]
          }
        ]
      }
    ]
  }
]

// GET /amenities/1
{
  "amenityId": 1,
  "ac": true,
  "wifi": true,
  "water": true,
  "lockers": false,
  "newspapers": false,
  "block": {
    "blockId": 1,
    "blockName": "AC Reading Hall",
    "blockDailyPrice": 150.0,
    "blockMonthlyPrice": 2500.0,
    "property": { "propertyId": 1, "propertyName": "Sri Study Hall", "address": "Ameerpet, Hyderabad", "googleCoordinates": "17.4375,78.4483", "owner": { "ownerId": 1, "ownerName": "Ravi Kumar" } }
  }
}
```

The values above are examples, and the nested `owner` in the second example is shortened.
