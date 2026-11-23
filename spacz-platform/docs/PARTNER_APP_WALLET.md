# SPACZ Partner App — Wallet & Withdrawals

What the **Partner (vendor) app** needs for the wallet screens: balance, statement, payout accounts
(UPI / bank) and withdrawal requests. Plus the admin endpoints used to pay requests out.

* Base URL: the gateway. Auth: `Authorization: Bearer <accessToken>` (role `VENDOR`).
* Everything acts on the signed-in vendor. Amounts are INR, JSON numbers with 2 decimals.
* Errors: standard SPACZ body `{ timestamp, status, error, message, path, correlationId, fieldErrors? }` —
  show `message`, branch on `error`.

---

## 1. How the money works

| Source | Effect on wallet |
|---|---|
| Student pays **online** (Razorpay) | **+ credit** = seat price − offer discount − SPACZ commission. Platform charges and tax stay with SPACZ. Shown as *pending* for the settlement hold (default **48 h**), then *available*. |
| Online payment **refunded** | **− debit** of that credit |
| Student pays **at the hall** (cash/UPI to you) | You already have the money. If the booking had platform charges / tax / commission, that part is **− debited** (it belongs to SPACZ). With today's settings (0 commission, 0 fee) nothing is recorded. |
| You **request a withdrawal** | **− debit** immediately (amount is reserved) |
| Withdrawal **rejected / cancelled** | **+ credit** back |

* **availableBalance** = what you can withdraw now.
* One open withdrawal at a time. Min **Rs.100**, max **Rs.2,00,000** per request (server config; read the limits from `GET /wallet`).
* Payouts are made by the SPACZ team (UPI / bank transfer); when paid, the request shows the **UTR** in `payoutReference`.

Server settings (env): `WALLET_COMMISSION_PERCENT` (0), `WALLET_SETTLEMENT_HOLD` (48h),
`WALLET_MIN_WITHDRAWAL` (100), `WALLET_MAX_WITHDRAWAL` (200000). A commission change applies to new
payments only; past entries never change.

---

## 2. Route summary (Partner app)

| # | Method & path | Screen / use |
|---|---|---|
| 1 | `GET /api/vendors/me/wallet` | Wallet home: balances, limits, "Withdraw" button state |
| 2 | `GET /api/vendors/me/wallet/entries?type=&page=&size=` | Statement / transactions list |
| 3 | `GET /api/vendors/me/payout-accounts` | Saved UPI IDs and bank accounts |
| 4 | `POST /api/vendors/me/payout-accounts` | Add UPI ID / bank account |
| 5 | `PATCH /api/vendors/me/payout-accounts/{id}/default` | Set default |
| 6 | `DELETE /api/vendors/me/payout-accounts/{id}` | Remove |
| 7 | `POST /api/vendors/me/withdrawals` | Request withdrawal |
| 8 | `GET /api/vendors/me/withdrawals?status=&page=&size=` | Withdrawal history |
| 9 | `GET /api/vendors/me/withdrawals/{id}` | Withdrawal detail (also the push deep link) |
| 10 | `POST /api/vendors/me/withdrawals/{id}/cancel` | Cancel while still `PENDING` |

---

## 3. Details

### 3.1 `GET /api/vendors/me/wallet`

```json
{
  "currency": "INR",
  "availableBalance": 1080.00,
  "pendingBalance": 240.00,
  "inWithdrawal": 0.00,
  "totalEarned": 4320.00,
  "totalWithdrawn": 3000.00,
  "minWithdrawalAmount": 100.00,
  "maxWithdrawalAmount": 200000.00,
  "settlementHoldHours": 48,
  "canWithdraw": true,
  "withdrawBlockedReason": null,
  "openWithdrawal": null
}
```

| Field | Show as |
|---|---|
| `availableBalance` | big number "Available to withdraw". **Can be negative** if Pay-at-Hall dues exceed earnings — show "You owe Rs.X". |
| `pendingBalance` | "Rs.240 will be available within 48 h" |
| `inWithdrawal` | "Rs.X being withdrawn" (already deducted from available) |
| `totalEarned`, `totalWithdrawn` | lifetime stats |
| `canWithdraw` | enable the Withdraw button |
| `withdrawBlockedReason` | when disabled: `VENDOR_NOT_APPROVED` → "Available once your account is approved"; `WITHDRAWAL_IN_PROGRESS` → show `openWithdrawal` card; `NO_PAYOUT_ACCOUNT` → "Add a UPI ID or bank account"; `BELOW_MINIMUM` → "Minimum withdrawal is Rs.100" |
| `openWithdrawal` | the current PENDING / PROCESSING request (same shape as §3.6), else `null` |

### 3.2 `GET /api/vendors/me/wallet/entries`

Query: `type` (optional: `BOOKING_EARNING`, `BOOKING_REFUND`, `PAY_AT_HALL_FEE`, `WITHDRAWAL`,
`WITHDRAWAL_REVERSAL`), `page` (0), `size` (20, max 100). Newest first.

```json
{
  "content": [
    {
      "id": 51, "type": "BOOKING_EARNING", "amount": 108.00, "currency": "INR",
      "description": "Earning for booking SPZ-7KQ2M9XW4D (after Rs.12.00 commission)",
      "bookingId": 5531, "bookingReference": "SPZ-7KQ2M9XW4D", "withdrawalId": null,
      "availableAt": "2026-11-23T10:15:30Z", "pending": true, "createdAt": "2026-11-21T10:15:30Z"
    },
    {
      "id": 50, "type": "WITHDRAWAL", "amount": -500.00, "currency": "INR",
      "description": "Withdrawal WDR-7KQ2M9XPA4RT", "bookingId": null, "bookingReference": null,
      "withdrawalId": 12, "availableAt": "2026-11-20T08:00:00Z", "pending": false, "createdAt": "2026-11-20T08:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1, "first": true, "last": true
}
```

`amount` > 0 = credit (green, "+"), < 0 = debit (red). `pending: true` → tag "Pending until <availableAt>".
Tap: `bookingId` → booking detail (`GET /api/bookings/{id}`); `withdrawalId` → withdrawal detail.

### 3.3 `GET /api/vendors/me/payout-accounts`

Default first. Bank account numbers are **masked**.

```json
[
  { "id": 4, "method": "BANK_ACCOUNT", "upiId": null, "accountHolderName": "Focus Study Hall",
    "accountNumberMasked": "XXXXXXXXXX6789", "ifscCode": "HDFC0001234", "bankName": "HDFC Bank",
    "displayName": "HDFC Bank ••6789", "isDefault": true, "createdAt": "2026-11-21T09:00:00Z" },
  { "id": 3, "method": "UPI", "upiId": "focushall@okaxis", "accountHolderName": null,
    "accountNumberMasked": null, "ifscCode": null, "bankName": null,
    "displayName": "focushall@okaxis", "isDefault": false, "createdAt": "2026-11-20T09:00:00Z" }
]
```

### 3.4 `POST /api/vendors/me/payout-accounts` → `201`

UPI:
```json
{ "method": "UPI", "upiId": "focushall@okaxis", "makeDefault": false }
```

Bank account:
```json
{ "method": "BANK_ACCOUNT", "accountHolderName": "Focus Study Hall", "accountNumber": "50100123456789",
  "ifscCode": "HDFC0001234", "bankName": "HDFC Bank", "makeDefault": true }
```

| Field | Rule |
|---|---|
| `upiId` | `name@handle`, stored lower-case |
| `accountHolderName` | 2–120 letters (spaces, `.'&()-` allowed) |
| `accountNumber` | 9–18 digits (spaces ignored). Ask the user to type it twice in the UI. |
| `ifscCode` | 11 chars `AAAA0XXXXXX`, stored upper-case |
| `bankName` | optional |
| `makeDefault` | optional; the first account is always default |

Errors: `422 INVALID_PAYOUT_ACCOUNT` (message starts with the field name, e.g. `ifscCode: Enter a valid IFSC…`),
`422 PAYOUT_ACCOUNT_LIMIT` (max 5), `409 DUPLICATE_RESOURCE` (already saved), `400 VALIDATION_ERROR`.

### 3.5 `PATCH …/payout-accounts/{id}/default` → account · `DELETE …/payout-accounts/{id}` → `204`

Deleting the default promotes the next account. Existing withdrawals keep their own copy of the details.

### 3.6 `POST /api/vendors/me/withdrawals` → `201`

```json
{ "amount": 1000.00, "payoutAccountId": 4, "note": "Weekly payout" }
```

Response (`WithdrawalResponse`, also used by §3.7–3.9 and `openWithdrawal`):

```json
{
  "id": 12, "reference": "WDR-7KQ2M9XPA4RT", "amount": 1000.00, "currency": "INR",
  "status": "PENDING",
  "payoutAccountId": 4, "payoutMethod": "BANK_ACCOUNT", "upiId": null,
  "accountHolderName": "Focus Study Hall", "accountNumberMasked": "XXXXXXXXXX6789",
  "ifscCode": "HDFC0001234", "bankName": "HDFC Bank", "payoutDisplayName": "HDFC Bank ••6789",
  "note": "Weekly payout", "rejectionReason": null, "payoutReference": null,
  "requestedAt": "2026-11-21T10:00:00Z", "processingAt": null, "paidAt": null,
  "rejectedAt": null, "cancelledAt": null
}
```

Errors:

| HTTP / `error` | Meaning |
|---|---|
| `422 VENDOR_NOT_APPROVED` | partner account not approved / suspended |
| `422 WITHDRAWAL_BELOW_MINIMUM` / `WITHDRAWAL_ABOVE_MAXIMUM` | outside the limits |
| `422 INSUFFICIENT_BALANCE` | more than `availableBalance` (message says the max) |
| `409 WITHDRAWAL_IN_PROGRESS` | another request is still PENDING / PROCESSING |
| `404 NOT_FOUND` | payout account not found / deleted |
| `400 VALIDATION_ERROR` | amount missing, ≤ 0 or > 2 decimals |

UI: amount field with "Max" button (= `availableBalance`), payout account picker (default preselected),
confirm sheet, then refresh `GET /wallet`.

### 3.7 `GET /api/vendors/me/withdrawals`

Query: `status` (`PENDING` | `PROCESSING` | `PAID` | `REJECTED` | `CANCELLED`), `page`, `size`.
`PageResponse<WithdrawalResponse>`, newest first.

### 3.8 `GET /api/vendors/me/withdrawals/{id}` → `WithdrawalResponse` (404 if not yours)

### 3.9 `POST /api/vendors/me/withdrawals/{id}/cancel` → `WithdrawalResponse` (`CANCELLED`)

Only while `PENDING`; `409 WITHDRAWAL_STATUS_CONFLICT` once processing started.

### Status chips

| `status` | Label | Extra |
|---|---|---|
| `PENDING` | Requested | "Cancel" button |
| `PROCESSING` | Processing | "Money is being sent" |
| `PAID` | Paid | show `paidAt`, **UTR `payoutReference`** (copy button) |
| `REJECTED` | Declined | show `rejectionReason`; "amount returned to wallet" |
| `CANCELLED` | Cancelled | "amount returned to wallet" |

---

## 4. Notifications (category `PAYOUT`, channel `spacz_payout`)

All carry `entityType: "WITHDRAWAL"`, `entityId` = withdrawal id, `data: { screen: "vendor-withdrawal", withdrawalId }`
→ open withdrawal detail (§3.8).

| `type` | Push | When |
|---|:-:|---|
| `WITHDRAWAL_REQUESTED` | in-app | request created |
| `WITHDRAWAL_PROCESSING` | ✓ | admin started the transfer |
| `WITHDRAWAL_PAID` | ✓ | money sent (body includes the UTR) |
| `WITHDRAWAL_REJECTED` | ✓ | declined (body includes the reason) |
| `WITHDRAWAL_CANCELLED` | in-app | vendor cancelled |

Admins get `WITHDRAWAL_REQUESTED` (category `REQUEST`, push, `screen: admin-withdrawal`).

---

## 5. Admin panel endpoints (role `ADMIN`, admin-service)

| Method & path | Body | Use |
|---|---|---|
| `GET /api/admin/withdrawals?status=&vendorId=&page=&size=&sort=` | – | queue (full account numbers, vendor name/phone); e.g. `status=PENDING&sort=requestedAt,asc` |
| `GET /api/admin/withdrawals/{id}` | – | detail |
| `POST /api/admin/withdrawals/{id}/processing` | `{ "note": "NEFT batch 2" }` (optional) | accept; vendor can no longer cancel |
| `POST /api/admin/withdrawals/{id}/paid` | `{ "payoutReference": "UTR412345678901", "note": "…" }` | after sending the money |
| `POST /api/admin/withdrawals/{id}/reject` | `{ "reason": "UPI name does not match" }` | decline, amount returned |
| `GET /api/admin/vendors/{vendorId}/wallet` | – | a vendor's balance |
| `GET /api/admin/vendors/{vendorId}/wallet/entries?type=&page=&size=` | – | a vendor's statement |

Wrong state → `409 WITHDRAWAL_STATUS_CONFLICT`. Every action is written to the admin audit log
(`WITHDRAWAL_PROCESSING`, `WITHDRAWAL_PAID`, `WITHDRAWAL_REJECTED`).
