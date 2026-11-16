# SPACZ Notifications

`spacz-notification-service` (port 8085, database `spacz_notification`) handles notifications for
every app: the **student app** (role `USER`), the **Partner / vendor app** (`VENDOR`) and the
**admin panel** (`ADMIN`). Each notification is:

* **stored in the account's in-app inbox** (always), and
* **pushed to the account's phones/browsers** through Firebase Cloud Messaging (FCM), unless it is
  marked in-app only, the account turned push off for that category, or push is not configured.

Other services never wait for it. They send notifications after their own transaction commits, in
the background, and retry briefly. If notification-service is down, bookings, approvals and the rest
still work; only those notifications are lost (and logged).

```
 auth ─┐                                                   ┌─► inbox (PostgreSQL)
 user ─┼─ POST /internal/notifications ─► notification ────┤
 studyhall ─┤   (after commit, async,       -service       └─► FCM ─► Android / iOS / Web
 admin ─┘    retried, idempotent)
```

---

## 1. What is sent, to whom

"Admins" means a broadcast to every ADMIN account (the admin team's shared inbox, read state per admin).

| Type | Recipient | Category | Push | When |
|---|---|---|:-:|---|
| `WELCOME` | new student | REGISTRATION | – | student registers (email or phone OTP) |
| `USER_REGISTERED` | admins | ADMIN | – | student registers |
| `WELCOME_PARTNER` | new vendor | REGISTRATION | – | vendor registers |
| `VENDOR_REGISTERED` | admins | ADMIN | ✓ | vendor registers |
| `VENDOR_APPROVAL_REQUESTED` | admins | REQUEST | ✓ | vendor profile submitted (API or Partner app) |
| `VENDOR_SUBMITTED` | vendor | APPROVAL | – | same, acknowledgement |
| `VENDOR_APPROVED` / `VENDOR_REJECTED` | vendor | APPROVAL | ✓ | admin decision (reason included) |
| `VENDOR_SUSPENDED` / `VENDOR_ACTIVATED` | vendor | ACCOUNT | ✓ | admin action |
| `STUDY_HALL_APPROVAL_REQUESTED` | admins | REQUEST | ✓ | hall submitted (Partner app: hall created) |
| `STUDY_HALL_SUBMITTED` | vendor | APPROVAL | – | same, acknowledgement |
| `STUDY_HALL_PUBLISHED` | vendor | APPROVAL | ✓ | hall submitted while approval is switched off |
| `STUDY_HALL_APPROVED` / `_REJECTED` / `_SUSPENDED` / `_ACTIVATED` | vendor | APPROVAL | ✓ | admin decision (reason included) |
| `STUDY_HALL_CREATED` / `_UPDATED` / `_DELETED` | vendor | ACTIVITY | – | vendor's own action |
| `STUDY_HALL_IMAGE_UPLOADED` | vendor | UPLOAD | – | hall photo uploaded |
| `BOOKING_HELD` | student | BOOKING | ✓ | booking created, seat held until confirmed |
| `BOOKING_CONFIRMED` | student | BOOKING | ✓ | booking confirmed |
| `BOOKING_RECEIVED` | vendor | BOOKING | ✓ | booking confirmed at the vendor's hall |
| `BOOKING_CANCELLED` | student (in-app) + vendor (push, only if it had been confirmed) | BOOKING | ✓ | student cancels |
| `BOOKING_EXPIRED` | student | BOOKING | ✓ | hold expired without confirmation |
| `BOOKING_COMPLETED` | student | BOOKING | – | booking period ended |
| `ENROLLED` | student | ENROLLMENT | ✓ | vendor adds a registered student to a hall |
| `DOCUMENT_UPLOADED` | student (UPLOAD) + admins (ADMIN) | | – | Aadhaar image uploaded |
| `FEEDBACK_SUBMITTED` / `FEEDBACK_RECEIVED` | student (ACTIVITY) / admins (ADMIN) | | – | app feedback |
| `PROFILE_UPDATED` | student | ACTIVITY | – | profile saved |
| `ACCOUNT_SUSPENDED` / `ACCOUNT_ACTIVATED` | the account (any role) | ACCOUNT | ✓ | admin action |
| `OFFER_LIVE` | all students | OFFER | ✓ | offer created live, or enabled while in its date window |
| `PLATFORM_UPDATE` | all students | ANNOUNCEMENT | ✓ | update published |
| `ADMIN_MESSAGE` | chosen audience or accounts | default ANNOUNCEMENT | default ✓ | admin panel "send notification" |

Offers that start in the future are not announced when created; enable them (or send an
`ADMIN_MESSAGE`) when they start.

The existing `GET /api/notifications` (admin-service: live offers + published updates feed) is
unchanged. Everything **below** `/api/notifications/` is this service.

---

## 2. API (through the gateway, `Authorization: Bearer <accessToken>`)

Same endpoints for all three apps; they always act on the signed-in account.

| Method & path | Purpose |
|---|---|
| `GET /api/notifications/categories` | **public**: categories, labels, Android channel IDs |
| `GET /api/notifications/inbox?category=&unreadOnly=&page=&size=` | inbox, newest first (page size max 100) |
| `GET /api/notifications/inbox/unread-count` | `{"unread": 3}` for the bell badge |
| `GET /api/notifications/inbox/{id}` | one notification (e.g. opened from a push) |
| `POST /api/notifications/inbox/{id}/read` | mark read |
| `POST /api/notifications/inbox/read-all` | mark all read → `{"updated": n}` |
| `DELETE /api/notifications/inbox/{id}` | remove from my inbox (204) |
| `POST /api/notifications/devices` | register / refresh this device for push |
| `POST /api/notifications/devices/unregister` | stop push to this device (logout) |
| `GET /api/notifications/devices` | my registered devices |
| `GET /api/notifications/preferences` | push on/off per category |
| `PUT /api/notifications/preferences` | change some categories |
| `POST /api/notifications/admin/send` | **ADMIN**: send to an audience or to accounts |
| `GET /api/notifications/admin/sent` | **ADMIN**: history of admin-sent notifications + push results |
| `GET /api/notifications/admin/push-status` | **ADMIN**: `FCM` or `DISABLED`, devices per role |

Inbox item:

```json
{
  "id": 812, "category": "BOOKING", "type": "BOOKING_CONFIRMED",
  "title": "Booking confirmed",
  "body": "Seat A1 at Focus Hall Ameerpet, 21 Nov 2026 – 23 Nov 2026. Booking ref SPZ-7KQ2M9XW4D.",
  "imageUrl": null, "entityType": "BOOKING", "entityId": 5531,
  "data": { "screen": "booking", "bookingId": "5531" },
  "broadcast": false, "read": false, "createdAt": "2026-11-16T10:15:30Z"
}
```

Register a device (`app`: `USER_APP` for the student app, `PARTNER_APP` for the partner app,
`ADMIN_APP` for the admin panel; it must match the account's role, else 422 `APP_ROLE_MISMATCH`):

```json
POST /api/notifications/devices
{ "token": "<FCM registration token>", "app": "PARTNER_APP", "platform": "ANDROID", "appVersion": "2.3.0" }
```

Preferences (only the listed categories change; in-app notifications are always kept):

```json
PUT /api/notifications/preferences
{ "preferences": [ { "category": "OFFER", "pushEnabled": false } ] }
```

Admin send (either `audience`: `USER` | `VENDOR` | `ADMIN` | `ALL`, or `userIds`):

```json
POST /api/notifications/admin/send
{ "audience": "VENDOR", "title": "Payout schedule", "body": "Payouts move to Mondays from 1 Dec.",
  "category": "ANNOUNCEMENT", "data": { "screen": "home" }, "push": true }
```

---

## 3. App integration checklist (student app and Partner app)

1. **Firebase**: add the apps to the **SPACZ** Firebase project (not Strike's): Android
   `google-services.json`, iOS `GoogleService-Info.plist` plus an APNs key uploaded in the Firebase
   console, web push VAPID key for the admin panel.
2. **After login** (and on every app start, and in `onTokenRefresh`): get the FCM token and call
   `POST /api/notifications/devices`. A device that switches accounts moves to the new account.
3. **On logout**: call `POST /api/notifications/devices/unregister` with the token **before**
   discarding the access token.
4. **Android channels** (optional but recommended): create one channel per category using
   `GET /api/notifications/categories` → `androidChannelId` (e.g. `spacz_booking`). Without them,
   Android uses the app's default channel.
5. **Bell icon**: `GET /inbox/unread-count`; refresh it when a push arrives in the foreground and
   when the app resumes.
6. **Notification centre**: `GET /inbox` (filter with `category` / `unreadOnly`), `POST /{id}/read`
   when opened, "mark all read", swipe to delete.
7. **Tapping a push**: the data payload always has `notificationId`, `type`, `category`, and when
   relevant `entityType`, `entityId` plus the `data` keys. Open the screen from `screen`, then mark it read.
8. **Settings screen**: `GET` / `PUT /preferences`.

`screen` values used today: `explore`, `profile`, `documents`, `booking`, `enrollments`, `offers`,
`updates` (student app) · `vendor-profile`, `vendor-studyhall`, `vendor-booking` (partner app) ·
`admin-vendor`, `admin-studyhall` (admin panel). Extra ids: `bookingId`, `studyHallId`, `offerId`, `updateId`.

---

## 4. Turning on push (server)

Without credentials the service runs with push **disabled**: everything goes to the inbox, and each
notification's push status is `DISABLED`.

1. Firebase console → SPACZ project → Project settings → Service accounts → **Generate new private key**.
2. Turn the JSON file into one line of base64. On the VPS: `base64 -w0 spacz-firebase-key.json`.
   On Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("spacz-firebase-key.json"))`.
3. Put it in `/opt/spacz/.env` as `FCM_CREDENTIALS_BASE64=<that line>`, then redeploy
   (`bash /opt/spacz/deploy/deploy.sh`). Delete the JSON file afterwards.
4. Check: `GET /api/notifications/admin/push-status` → `"provider": "FCM"`. The service log also
   prints the Firebase project ID at startup, and an error if it looks like a Strike project.

---

## 5. Data and housekeeping

* Personal notifications keep their read state on the row; broadcasts keep one receipt per account
  that read or dismissed them.
* Every push records its result on the notification: `NOT_REQUESTED`, `PENDING`, `SENT`, `FAILED`,
  `NO_DEVICES`, `MUTED`, `DISABLED` (visible in `/admin/sent`).
* Tokens FCM reports as gone are deleted immediately; tokens not refreshed for 90 days, and
  notifications older than 120 days, are deleted nightly (`DEVICE_TOKEN_TTL`, `NOTIFICATION_RETENTION`).
* At most 10 devices per account (the least recently seen are dropped).
* Producers send a `dedupKey` with every notification, so a retried delivery never creates a duplicate.
