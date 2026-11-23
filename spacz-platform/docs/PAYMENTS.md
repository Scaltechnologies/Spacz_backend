# Payments

Students pay for a seat booking in one of two ways:

* **Online**: Razorpay Checkout (UPI, cards, netbanking) in the app.
* **Pay at Hall**: the student shows a QR code (or a 6-digit code) at the hall's counter and pays there.
  The vendor scans or types it, then taps **Confirm payment received**.

A booking becomes `CONFIRMED` **only** when its payment succeeds. Students can no longer call
`POST /api/bookings/{id}/confirm`; that endpoint is now an ADMIN-only manual override.

The design is ported from Strike's payment stack, with Strike's known bugs fixed. It is summarized below.

| Where | What |
|---|---|
| studyhall-service, `com.spacz.studyhall.payment` | payments, Razorpay adapter, Pay at Hall, sweeps, refunds. Payment `SUCCESS` and booking `CONFIRMED` commit in **one local transaction**. |
| admin-service, `paymentgateway` + `settings` | Razorpay credentials (AES-256-GCM encrypted at rest), platform `PAYMENT_MODE`, Pay-at-Hall switch per vendor, payment audit view |
| gateway | routes `/api/payments/**` to studyhall-service; `POST /api/payments/webhook` is public (HMAC-authenticated) |

## Flows

### Online (Razorpay)

```
App                         studyhall-service                          Razorpay
 │ POST /api/bookings ─────► booking PENDING, 15 min hold
 │ POST /api/payments ─────► amount = booking.totalPrice (server) ──POST /orders──► order_xxx
 │ ◄── {paymentId, gatewayOrderId, gatewayKeyId, amount}   (hold extended to +20 min)
 │ Razorpay Checkout(key, order_id) ───────────────────────────────────────────────► pay
 │ POST /api/payments/{id}/verify {orderId, paymentId, signature}
 │                           HMAC check + GET /orders/{id}/payments (live status)
 │ ◄── SUCCESS (booking CONFIRMED) | PROCESSING (poll) | FAILED (retry)
 │                           ◄──────── POST /api/payments/webhook (HMAC of raw body)
```

* **The client never sends a price.** The amount is the booking's server-computed `totalPrice`.
* **Verify trusts nothing from the client.** It checks the signature *and* asks Razorpay for the live status.
* **Single choke point.** Verify, the webhook, cancel, retry and the reconciliation sweep all go through one
  method, under a `SELECT … FOR UPDATE` lock on the payment row. Duplicate success signals have exactly one
  effect. This is proven against PostgreSQL by `PaymentRacePostgresTest`.
* **Cancel** (checkout dismissed) asks Razorpay first. A charge that really happened still ends `SUCCESS`.
* **Retry** reconciles the previous attempt first, then opens a new attempt with a new Razorpay order.
  Retry needs the seat hold to still be live.
* **Reconciliation sweep** runs every 5 minutes. For payments still open after their 15-minute session, it
  asks Razorpay: SUCCESS or FAILED is applied; anything still uncertain becomes `EXPIRED` (never `FAILED`).
  A late capture is still reconciled forward by the webhook.

### Late success and refunds

If Razorpay reports a capture after the hold expired, the booking is revived and confirmed, as long as the seat
and the student's slot in that hall are still free (checked under the seat lock).

Otherwise the payment becomes `REFUND_PENDING`, and the refund job (every 2 minutes) refunds the full amount.
The job is idempotent at Razorpay: an existing refund is found, not repeated. The payment then becomes
`REFUNDED` and carries `gatewayRefundId`. The same happens when:

* the booking was already paid by another payment;
* the captured amount differs from the booking total.

Refunds when a student cancels an already **confirmed** booking are a business decision and are **not**
automated.

### Pay at Hall

```
Student app: POST /api/payments {bookingId, paymentMethod:"PAY_AT_HALL"}
   ◄── qrPayload "SPACZ-PAY-HALL:<token>" + verificationCode "123456"   (returned ONCE)
Vendor app:  scan QR → POST /api/payments/pay-at-hall/verify-qr {qrToken}        → AWAITING_VENDOR_CONFIRMATION
         or  pick order → POST /api/payments/pay-at-hall/verify-code {paymentId, code}
             take the cash/UPI → POST /api/payments/pay-at-hall/{id}/confirm  → SUCCESS, booking CONFIRMED
```

* Only SHA-256 hashes of the token and code are stored. The raw values are never logged, audited or returned again.
  The app must keep them in secure storage to show them again after a restart.
* Scanning or entering the code **never** confirms payment. Confirming requires a prior scan or code entry
  (`409 PAYMENT_NOT_CONFIRMABLE` otherwise). Confirm is idempotent, so it is safe to retry.
* Five wrong codes lock code entry for 15 minutes. The counter is persisted even though the request fails.
* The session lasts 4 hours, but never past the end of the booking's first day (`PAY_AT_HALL_SESSION_TTL`).
  The seat is held for the same time. Expired sessions are swept every 5 minutes.
* If the booking is no longer valid when the vendor confirms, confirm returns 409 with
  "Do not collect payment" and the session is closed.
* An admin can switch Pay at Hall off per vendor. Every step is written to `payment_audit_events`.

### Payment mode

`PAYMENT_MODE` (admin panel) can be `ONLINE_ONLY`, `PAY_AT_HALL_ONLY` or `BOTH` (the default). `GET /api/payments/mode`
serves the same 60-second-cached value the server enforces, so the UI and the server never disagree.
Use `PAY_AT_HALL_ONLY` until live Razorpay keys are ready.

## Statuses

`CREATED → PENDING → PROCESSING → SUCCESS | FAILED | CANCELLED | EXPIRED`, plus `AWAITING_VENDOR_CONFIRMATION`
(Pay at Hall) and `REFUND_PENDING → REFUNDED`.

* `SUCCESS` and `REFUNDED` are final.
* `FAILED`, `CANCELLED` and `EXPIRED` can move forward if Razorpay later reports a capture, or back to
  `PENDING` through retry.
* `PaymentStatus.canTransitionTo` is enforced on every change.

Database backstops (Flyway `V12__payments.sql`): at most one open payment and at most one `SUCCESS` per
booking (partial unique indexes), a unique webhook `dedupe_key`, and unique Razorpay order and payment ids.

## API

Responses are DTOs; errors are `ApiError` with the code in `error`.

| Method & path | Who | Notes |
|---|---|---|
| `GET /api/payments/mode` | any signed-in | `{"paymentMode": …}` |
| `POST /api/payments` | USER | header `Idempotency-Key` (one per checkout screen visit); `{bookingId, paymentMethod?: ONLINE\|PAY_AT_HALL}` → 201 |
| `GET /api/payments/{id}` | USER (owner) | poll every 4 s while open (Pay at Hall: every 10–15 s) |
| `GET /api/payments/booking/{bookingId}` | USER (owner) | latest payment of a booking (resume after restart) |
| `POST /api/payments/{id}/verify` | USER | `{gatewayOrderId, gatewayPaymentId, gatewaySignature}` from Checkout |
| `POST /api/payments/{id}/cancel` | USER | checkout dismissed / cancel a Pay-at-Hall order |
| `POST /api/payments/{id}/retry` | USER | ONLINE only → 201, new attempt |
| `POST /api/payments/webhook` | Razorpay | `X-Razorpay-Signature`; always 200 for duplicates/unknown orders |
| `POST /api/payments/pay-at-hall/verify-qr` · `/verify-code` · `/{id}/confirm` | VENDOR | |
| `GET /api/payments/pay-at-hall/pending` · `/history?page&size` | VENDOR | |
| `GET/PUT /api/admin/payment-gateway/razorpay` | ADMIN | secrets masked; blank secret on PUT keeps the stored one |
| `GET/PATCH /api/admin/settings/payment-mode` | ADMIN | |
| `PATCH /api/admin/vendors/{vendorId}/pay-at-hall {enabled}` | ADMIN | |
| `GET /api/admin/bookings/{bookingId}/payments` | ADMIN | payments of a booking with attempts, gateway ids, refund id |
| `GET /api/admin/payments/{paymentId}/audit-events` | ADMIN | |
| `GET /api/payments/history?status&page&size` | USER | all my payments, every status, newest first |
| `GET /api/payments/transactions?type&status&bookingId&from&to&page&size` · `/{id}` | USER | my ledger: payments and refunds |
| `GET /api/vendors/me/transactions?studyHallId&type&status&paymentMethod&bookingId&from&to` | VENDOR | ledger of my halls |
| `GET /api/vendors/me/transactions/summary?studyHallId&paymentMethod&from&to` | VENDOR | collected, refunded, refund pending, net, collected per method |
| `GET /api/bookings/{id}/history` | the booking's student, the hall's vendor, ADMIN | status changes + payments + transactions, oldest first |
| `GET /api/admin/transactions` · `/summary` | ADMIN | same filters, plus `userId` and `vendorId` |
| `GET /api/admin/bookings/{bookingId}/history` | ADMIN | |
| `POST /api/bookings/{id}/cancel` | USER | cancels the booking's open payment first (gateway asked); `409 PAYMENT_ALREADY_SUCCESSFUL` if it had gone through |

The main error codes are:

| Status | Codes |
|---|---|
| 409 | `BOOKING_NOT_PAYABLE`, `PAYMENT_ALREADY_PROCESSING`, `PAYMENT_ALREADY_SUCCESSFUL`, `PAYMENT_NOT_CONFIRMABLE` |
| 422 | `BOOKING_HOLD_EXPIRED` (send the user back to seat selection), `INVALID_PAYMENT_CODE` (message says how many attempts are left) |
| 403 | `ONLINE_PAYMENT_DISABLED`, `PAY_AT_HALL_DISABLED`, `WRONG_VENDOR` |
| 404 | `INVALID_QR` |
| 410 | `PAYMENT_EXPIRED` |
| 429 | `TOO_MANY_ATTEMPTS` |
| 401 | `PAYMENT_SIGNATURE_INVALID` |
| 502 | `PAYMENT_GATEWAY_ERROR` |
| 503 | `PAYMENT_GATEWAY_NOT_CONFIGURED` |

## History and transactions

Two append-only records, written in the **same transaction** as the change they describe (Flyway
`V13__booking_history_and_transactions.sql`):

| Table | One row per | Written by |
|---|---|---|
| `booking_status_history` | booking status change: from → to, actor (`USER` / `VENDOR` / `ADMIN` / `SYSTEM` + id), payment id, reason | `BookingStatusChangedEvent` → `BookingHistoryRecorder` (creation, payment confirm, admin confirm, cancel, hold expiry, completion, legacy import) |
| `payment_transactions` | money movement: `PAYMENT` (Razorpay capture or vendor-confirmed Pay at Hall) or `REFUND` (`PENDING` → `SUCCESS`) | `TransactionLedger`, called from `PaymentServiceImpl` under the payment row lock |

* Each transaction has a reference like `TXN-7KQ2M9XPA4RT` for receipts and support.
* `(payment_id, type)` is unique, so a payment is recorded as received at most once and refunded at most once,
  however many duplicate verifies or webhooks arrive.
* Failed, cancelled or expired payments moved no money, so they are **not** ledger rows. They show in
  `GET /api/payments/history` and in the booking history.
* An admin's manual confirmation (`POST /api/bookings/{id}/confirm`) is recorded in the booking history, but
  creates no transaction, because no money moved through the platform.
* Summary totals: `net = collected − refunded − refundPending`. Date filters (`from`/`to`, inclusive) use the
  business time zone and apply to `occurredAt`.
* The migration backfills one history row per existing booking (its status at that point) and ledger rows for
  existing `SUCCESS` / `REFUND_PENDING` / `REFUNDED` payments.

## Configuration

| Variable | Service | Purpose |
|---|---|---|
| `ADMIN_SECRETS_ENCRYPTION_KEY` | admin | `openssl rand -base64 32`. **Back it up.** If it is lost, re-enter the Razorpay secrets. |
| `RAZORPAY_KEY_ID` / `_KEY_SECRET` / `_WEBHOOK_SECRET` | studyhall | optional fallback, used only while nothing is saved in the admin panel |
| `BOOKING_AUTO_CONFIRM` | studyhall | **must be `false`**; `true` confirms bookings without payment (demos only) |
| `PAYMENT_SESSION_TTL` (15m), `PAY_AT_HALL_SESSION_TTL` (4h) | studyhall | session lengths |

Credentials changed in the admin panel take effect within 60 seconds, without a restart.

## Razorpay setup (per environment)

1. Razorpay Dashboard → Settings → API Keys: generate **Test** keys (`rzp_test_…`). Generate Live keys
   after KYC.
2. Admin panel → Payment Gateway: paste the Key ID and Key Secret, set Mode TEST, set Active on.
3. Razorpay → Webhooks → Add:
   * URL `https://api.spacz.in/api/payments/webhook`
   * a strong secret (paste the same value into the admin panel's Webhook Secret)
   * events `payment.captured`, `payment.failed`, `order.paid`

   Locally, expose the gateway with a tunnel (`ngrok http 8080`).
4. Test with UPI `success@razorpay` / `failure@razorpay`, or the test cards in Razorpay's docs.
5. Going live: paste the live keys and the live webhook secret, and set Mode LIVE. Open test-mode orders
   then fail reconciliation and expire; this is expected.

## Runbook

* **`REFUND_PENDING` older than ~1 hour.** The refund job is failing.
  1. Check `docker logs spacz-studyhall-service-1 | grep -i refund`. The usual causes are a Razorpay outage
     or wrong keys.
  2. `GET /api/admin/payments/{id}/audit-events` shows the history.
  3. A row logged "has no captured attempt" needs manual review in the Razorpay dashboard.
* **Payment stuck in `PROCESSING`.** The sweep resolves it about 15 minutes after creation. Check the
  order in the Razorpay dashboard. A capture that arrives later is still applied by the webhook.
* **Webhook errors.** Find them with
  `select * from payment_webhook_events where processing_result like 'ERROR%'`. Razorpay's retries
  reprocess these rows automatically.
* **Logs never contain secrets, QR tokens or codes.** To check, grep for `keySecret`, `qrPayload` and
  `verificationCode`.

Suggested alerts:

* `REFUND_PENDING` older than 1 hour
* webhook `processing_result LIKE 'ERROR%'`

## Not built yet

* **Frontends.** Needed: the student checkout and Pay-at-Hall QR screens, the vendor scan / enter-code /
  confirm screens, and the admin gateway, payment-mode and vendor-switch screens. All backend APIs above
  exist.
* **Outbox.** Not needed while every payment side effect is local. Add one before introducing remote
  financial side effects such as vendor payouts or commission.
