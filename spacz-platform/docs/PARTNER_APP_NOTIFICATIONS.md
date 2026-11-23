# SPACZ Partner App — Notifications Integration

Everything the **Partner (vendor) app** needs to build the notification UI: the routes, request and
response shapes, every notification a vendor can receive, and where tapping each one should go.

* Base URL: the gateway (same as every other Partner app call).
* Auth: `Authorization: Bearer <accessToken>` on every route except `GET /api/notifications/categories`.
* The signed-in account must have role `VENDOR`. All routes act on **the signed-in account only**.
* Errors use the standard SPACZ body: `{ timestamp, status, error, message, path, correlationId, fieldErrors? }`.

> ⚠️ Use only the routes **below** `/api/notifications/…`. The bare `GET /api/notifications`
> (no trailing path) is the student app's offers/updates feed and is not for the Partner app.

---

## 1. Route summary

| # | Method & path | Used for | Response |
|---|---|---|---|
| 1 | `GET /api/notifications/categories` | Settings labels, Android channels (**public**) | `CategoryResponse[]` |
| 2 | `GET /api/notifications/inbox` | Notification list screen | `PageResponse<Notification>` |
| 3 | `GET /api/notifications/inbox/unread-count` | Bell badge | `{ "unread": 3 }` |
| 4 | `GET /api/notifications/inbox/{id}` | Open one notification (e.g. from a push tap) | `Notification` |
| 5 | `POST /api/notifications/inbox/{id}/read` | Mark one as read | `Notification` |
| 6 | `POST /api/notifications/inbox/read-all` | "Mark all as read" | `{ "updated": 7 }` |
| 7 | `DELETE /api/notifications/inbox/{id}` | Swipe to delete | `204 No Content` |
| 8 | `POST /api/notifications/devices` | Register FCM token (login / app start / token refresh) | `DeviceResponse` |
| 9 | `POST /api/notifications/devices/unregister` | Stop push on logout | `204 No Content` |
| 10 | `GET /api/notifications/devices` | (optional) list my devices | `DeviceResponse[]` |
| 11 | `GET /api/notifications/preferences` | Notification settings screen | `PreferenceResponse[]` |
| 12 | `PUT /api/notifications/preferences` | Toggle push per category | `PreferenceResponse[]` |

---

## 2. Route details

### 2.1 `GET /api/notifications/categories` (public, no token)

```json
[
  { "category": "REGISTRATION", "label": "Registration & welcome", "androidChannelId": "spacz_registration" },
  { "category": "ACCOUNT",      "label": "Account status",         "androidChannelId": "spacz_account" },
  { "category": "APPROVAL",     "label": "Approvals",              "androidChannelId": "spacz_approval" },
  { "category": "BOOKING",      "label": "Bookings",               "androidChannelId": "spacz_booking" },
  ...
]
```

All 13 categories are returned: `REGISTRATION, ACCOUNT, APPROVAL, REQUEST, BOOKING, ENROLLMENT,
UPLOAD, PAYOUT, OFFER, ANNOUNCEMENT, ACTIVITY, ADMIN, SYSTEM`. The ones a **vendor actually receives** are:

| Category | Label | Vendor gets |
|---|---|---|
| `REGISTRATION` | Registration & welcome | welcome message |
| `ACCOUNT` | Account status | partner/account suspended or re-activated |
| `APPROVAL` | Approvals | partner profile + study hall review results |
| `BOOKING` | Bookings | new bookings, cancellations, Pay at Hall orders |
| `UPLOAD` | Uploads | hall photo uploaded |
| `ACTIVITY` | Your activity | hall created / updated / deleted |
| `PAYOUT` | Payouts | wallet withdrawals: requested, processing, paid, declined |
| `ANNOUNCEMENT` | Announcements | messages from the SPACZ admin team |

Use these 8 for the filter chips and the settings screen (hide the rest in the Partner app).

### 2.2 `GET /api/notifications/inbox`

Query params (all optional):

| Param | Type | Default | Notes |
|---|---|---|---|
| `category` | enum (see above) | — | filter chips |
| `unreadOnly` | boolean | `false` | "Unread" tab |
| `page` | int | `0` | 0-based |
| `size` | int | `20` | max `100` |

Newest first. Includes personal notifications **and** announcements sent to all vendors.

```json
{
  "content": [
    {
      "id": 812,
      "category": "BOOKING",
      "type": "BOOKING_RECEIVED",
      "title": "New booking at Focus Hall Ameerpet",
      "body": "Seat A1 booked for 21 Nov 2026 – 23 Nov 2026 (daily). Ref SPZ-7KQ2M9XW4D.",
      "imageUrl": null,
      "entityType": "BOOKING",
      "entityId": 5531,
      "data": { "screen": "vendor-booking", "bookingId": "5531", "studyHallId": "42" },
      "broadcast": false,
      "read": false,
      "createdAt": "2026-11-21T10:15:30Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 37, "totalPages": 2, "first": true, "last": false
}
```

Notification fields:

| Field | Type | Notes |
|---|---|---|
| `id` | number | use for read / delete / detail |
| `category` | enum | icon / colour / filter |
| `type` | string | exact event (see §3) — drive the tap action from this + `data.screen` |
| `title`, `body` | string | display as-is (already formatted, dates/amounts included) |
| `imageUrl` | string \| null | optional big picture (admin announcements) |
| `entityType`, `entityId` | string \| null, number \| null | what it is about: `VENDOR`, `STUDY_HALL`, `BOOKING`, `PAYMENT`, `USER_ACCOUNT` |
| `data` | `{ [key]: string }` | deep-link data; **all values are strings** (parse ids) |
| `broadcast` | boolean | `true` = announcement to all vendors |
| `read` | boolean | |
| `createdAt` | ISO-8601 UTC | show as relative time ("5 min ago") |

### 2.3 `GET /api/notifications/inbox/unread-count`

```json
{ "unread": 3 }
```

Refresh: on app start/resume, after a push arrives in the foreground, after read / read-all / delete.

### 2.4 `GET /api/notifications/inbox/{id}`

Returns one `Notification` (same shape as an inbox item). `404` if it isn't in this account's inbox
(e.g. deleted).

### 2.5 `POST /api/notifications/inbox/{id}/read`

No body. Returns the updated `Notification` (`"read": true`). Idempotent.

### 2.6 `POST /api/notifications/inbox/read-all`

No body. Returns `{ "updated": 7 }`. Set the badge to 0.

### 2.7 `DELETE /api/notifications/inbox/{id}`

No body. `204`. Removes it from this account's inbox only (announcements stay for other vendors).

### 2.8 `POST /api/notifications/devices` — register for push

```json
{
  "token": "<FCM registration token>",
  "app": "PARTNER_APP",
  "platform": "ANDROID",
  "appVersion": "2.3.0"
}
```

| Field | Required | Values |
|---|---|---|
| `token` | ✓ | FCM token, max 512 chars |
| `app` | ✓ | **always `PARTNER_APP`** in this app |
| `platform` | ✓ | `ANDROID` \| `IOS` \| `WEB` |
| `appVersion` | – | max 40 chars |

Response:

```json
{ "id": 19, "app": "PARTNER_APP", "platform": "ANDROID", "appVersion": "2.3.0",
  "createdAt": "2026-11-21T10:00:00Z", "lastSeenAt": "2026-11-21T10:00:00Z" }
```

Call it **after login, on every app start, and in `onTokenRefresh`**. Safe to call repeatedly.
Errors: `422 APP_ROLE_MISMATCH` if `app` isn't `PARTNER_APP` for a vendor account; `400 VALIDATION_ERROR`.

### 2.9 `POST /api/notifications/devices/unregister` — logout

```json
{ "token": "<FCM registration token>" }
```

`204`. Call **before** clearing the access token on logout (it needs auth).

### 2.10 `GET /api/notifications/devices`

`DeviceResponse[]` (same shape as 2.8). Optional; only for a "logged-in devices" screen.

### 2.11 `GET /api/notifications/preferences`

```json
[
  { "category": "BOOKING",  "label": "Bookings",  "pushEnabled": true },
  { "category": "APPROVAL", "label": "Approvals", "pushEnabled": true },
  ...
]
```

Returns all categories; show only the 8 vendor ones from §2.1.

### 2.12 `PUT /api/notifications/preferences`

Send only the categories that changed; others keep their setting.

```json
{ "preferences": [ { "category": "ANNOUNCEMENT", "pushEnabled": false } ] }
```

Returns the full list (same as 2.11). Turning push off **only stops the phone push** — notifications
still appear in the in-app inbox. Show that hint on the settings screen.

---

## 3. Every notification a vendor can receive

"Push" = sent to the phone as a push (unless the vendor muted that category). "In-app" = inbox only,
never pushed.

### 3.1 Registration & account

| `type` | Category | Push | Title (example) | `entityType` / `entityId` | `data` | Tap → screen |
|---|---|:-:|---|---|---|---|
| `WELCOME_PARTNER` | REGISTRATION | in-app | Welcome to SPACZ Partner! | `VENDOR` / vendorId | `screen: vendor-profile` | Business profile |
| `VENDOR_SUBMITTED` | APPROVAL | in-app | Profile submitted for review | `VENDOR` / vendorId | `screen: vendor-profile` | Business profile |
| `VENDOR_APPROVED` | APPROVAL | ✓ | Your partner account is approved | `VENDOR` / vendorId | `screen: vendor-profile` | Business profile |
| `VENDOR_REJECTED` | APPROVAL | ✓ | Partner application not approved | `VENDOR` / vendorId | `screen: vendor-profile` | Business profile (body contains "Reason: …") |
| `VENDOR_SUSPENDED` | ACCOUNT | ✓ | Partner account suspended | `VENDOR` / vendorId | `screen: vendor-profile` | Business profile |
| `VENDOR_ACTIVATED` | ACCOUNT | ✓ | Partner account active again | `VENDOR` / vendorId | `screen: vendor-profile` | Business profile |
| `ACCOUNT_SUSPENDED` | ACCOUNT | ✓ | Account suspended | `USER_ACCOUNT` / userId | — (no `screen`) | Just open the notification detail |
| `ACCOUNT_ACTIVATED` | ACCOUNT | ✓ | Account active again | `USER_ACCOUNT` / userId | — | Notification detail |

> `ACCOUNT_SUSPENDED` = the whole login is blocked by an admin. After it, API calls with the old
> token may fail with `403 ACCOUNT_SUSPENDED`; send the user to the login screen.

### 3.2 Study halls

| `type` | Category | Push | Title (example) | `entityType` / `entityId` | `data` | Tap → screen |
|---|---|:-:|---|---|---|---|
| `STUDY_HALL_SUBMITTED` | APPROVAL | in-app | Study hall submitted | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_PUBLISHED` | APPROVAL | ✓ | Study hall published | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_APPROVED` | APPROVAL | ✓ | 'Focus Hall' is approved | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_REJECTED` | APPROVAL | ✓ | 'Focus Hall' was not approved | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall edit (body has "Reason: …") |
| `STUDY_HALL_SUSPENDED` | APPROVAL | ✓ | 'Focus Hall' is suspended | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_ACTIVATED` | APPROVAL | ✓ | 'Focus Hall' is active again | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_CREATED` | ACTIVITY | in-app | Study hall created | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_UPDATED` | ACTIVITY | in-app | Study hall updated | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall detail |
| `STUDY_HALL_DELETED` | ACTIVITY | in-app | Study hall deleted | `STUDY_HALL` / hallId | — (hall is gone) | No navigation |
| `STUDY_HALL_IMAGE_UPLOADED` | UPLOAD | in-app | Photo uploaded | `STUDY_HALL` / hallId | `screen: vendor-studyhall` | Hall photos |

### 3.3 Bookings & payments

| `type` | Category | Push | Title (example) | `entityType` / `entityId` | `data` | Tap → screen |
|---|---|:-:|---|---|---|---|
| `BOOKING_RECEIVED` | BOOKING | ✓ | New booking at Focus Hall | `BOOKING` / bookingId | `screen: vendor-booking`, `bookingId`, `studyHallId` | Booking detail |
| `BOOKING_CANCELLED` | BOOKING | ✓ | Booking cancelled at Focus Hall | `BOOKING` / bookingId | `screen: vendor-booking`, `bookingId`, `studyHallId` | Booking detail |
| `PAY_AT_HALL_CREATED` | BOOKING | ✓ | Pay at Hall order | `PAYMENT` / **paymentId** | `screen: vendor-pay-at-hall`, `bookingId` | Pay at Hall pending list (highlight `entityId`) |

Notes:
* `BOOKING_RECEIVED` fires when a booking is **confirmed** (online payment or Pay at Hall confirmed).
* `BOOKING_CANCELLED` reaches the vendor only if the booking had been confirmed.
* `PAY_AT_HALL_CREATED`: `entityId` is the **payment** id, not the booking id.

### 3.4 Wallet withdrawals (see [PARTNER_APP_WALLET.md](PARTNER_APP_WALLET.md))

| `type` | Category | Push | Title (example) | `entityType` / `entityId` | `data` | Tap → screen |
|---|---|:-:|---|---|---|---|
| `WITHDRAWAL_REQUESTED` | PAYOUT | in-app | Withdrawal requested | `WITHDRAWAL` / withdrawalId | `screen: vendor-withdrawal`, `withdrawalId` | Withdrawal detail |
| `WITHDRAWAL_PROCESSING` | PAYOUT | ✓ | Withdrawal being processed | `WITHDRAWAL` / withdrawalId | same | Withdrawal detail |
| `WITHDRAWAL_PAID` | PAYOUT | ✓ | Withdrawal paid (body has the UTR) | `WITHDRAWAL` / withdrawalId | same | Withdrawal detail |
| `WITHDRAWAL_REJECTED` | PAYOUT | ✓ | Withdrawal not processed (body has the reason) | `WITHDRAWAL` / withdrawalId | same | Withdrawal detail |
| `WITHDRAWAL_CANCELLED` | PAYOUT | in-app | Withdrawal cancelled | `WITHDRAWAL` / withdrawalId | same | Withdrawal detail |

### 3.5 Announcements from SPACZ admins

| `type` | Category | Push | `entityType` | `data` | Tap → screen |
|---|---|:-:|---|---|---|
| `ADMIN_MESSAGE` | usually `ANNOUNCEMENT` (admin can choose any) | usually ✓ | null | free-form, may contain `screen` | `data.screen` if known, else notification detail |

`broadcast: true` when sent to all vendors; may include `imageUrl`.

---

## 4. Deep-link routing (`data.screen`)

Partner-app `screen` values and the API to load when opening them:

| `screen` | Open | Load with |
|---|---|---|
| `vendor-profile` | Business profile / approval status | `GET /api/vendors/me` |
| `vendor-studyhall` | Study hall detail (`entityId` = hallId) | `GET /api/studyhalls/{entityId}` |
| `vendor-booking` | Booking detail (`data.bookingId`) | `GET /api/bookings/{bookingId}` (+ `/history` for timeline) |
| `vendor-pay-at-hall` | Pay at Hall pending orders | `GET /api/payments/pay-at-hall/pending` → find `id == entityId` |
| `vendor-withdrawal` | Withdrawal detail (`data.withdrawalId`) | `GET /api/vendors/me/withdrawals/{withdrawalId}` |
| *(missing / unknown)* | Notification detail screen | `GET /api/notifications/inbox/{id}` |

Pseudo-code:

```ts
function onNotificationTap(n: { id: number; type: string; entityId?: number; data: Record<string,string> }) {
  markRead(n.id);                                  // POST /inbox/{id}/read, then refresh badge
  switch (n.data.screen) {
    case 'vendor-profile':     return nav('VendorProfile');
    case 'vendor-studyhall':   return nav('StudyHallDetail', { hallId: n.entityId });
    case 'vendor-booking':     return nav('BookingDetail',   { bookingId: Number(n.data.bookingId) });
    case 'vendor-pay-at-hall': return nav('PayAtHallPending', { highlightPaymentId: n.entityId });
    case 'vendor-withdrawal':  return nav('WithdrawalDetail', { withdrawalId: Number(n.data.withdrawalId) });
    default:                   return nav('NotificationDetail', { id: n.id });
  }
}
```

---

## 5. Push payload (FCM)

Each push has a visible `notification` (title, body, optional image) and a `data` map with **string**
values:

```json
{
  "notificationId": "812",
  "type": "BOOKING_RECEIVED",
  "category": "BOOKING",
  "entityType": "BOOKING",
  "entityId": "5531",
  "screen": "vendor-booking",
  "bookingId": "5531",
  "studyHallId": "42"
}
```

* Android channel = `spacz_<category lowercase>` (e.g. `spacz_booking`). Create channels at app start
  from `GET /api/notifications/categories` (use the 8 vendor categories); otherwise Android uses the
  default channel.
* Tap (background / killed): build the same object as §4 from the data map
  (`id = Number(data.notificationId)`, `entityId = Number(data.entityId)`) and call `onNotificationTap`.
* Foreground push: show an in-app banner/toast, refresh the unread count and (if open) the inbox list.

---

## 6. UI checklist

1. **Firebase**: Partner app registered in the **SPACZ** Firebase project (Android
   `google-services.json`, iOS `GoogleService-Info.plist` + APNs key).
2. **Login / app start / token refresh** → `POST /devices` with `app: "PARTNER_APP"`.
3. **Logout** → `POST /devices/unregister` **then** clear tokens.
4. **Bell icon + badge** in the header → `GET /inbox/unread-count`.
5. **Notifications screen**:
   * tabs `All` / `Unread` (`unreadOnly=true`),
   * category chips (vendor categories from §2.1),
   * infinite scroll (`page`, stop when `last: true`),
   * unread items styled bold / with a dot,
   * tap → §4 routing, swipe → `DELETE /inbox/{id}`,
   * "Mark all as read" → `POST /inbox/read-all`,
   * pull to refresh, empty state.
6. **Notification detail** (fallback screen): title, body, image, time.
7. **Settings → Notifications**: `GET /preferences`, toggle → `PUT /preferences` with just that
   category; note that in-app notifications are always kept.
8. Ask for notification permission (Android 13+ / iOS) before registering the device.
