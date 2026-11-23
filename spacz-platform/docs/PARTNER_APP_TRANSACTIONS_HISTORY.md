# Partner App — Transactions & History: Routes + Frontend Implementation

Everything the **Spacz_Partner** app needs for the money and history screens: the backend routes, response
shapes, paste-ready API code, navigation routes and screen specs. Same conventions as the wallet guide
([PARTNER_APP_WALLET_IMPLEMENTATION.md](PARTNER_APP_WALLET_IMPLEMENTATION.md)): `src/api/client.ts`,
`apiErrorMessage`, `Screen` / `Card` / `StateView` / `Badge` / `KeyValue` / `Chip` / `Segmented` / `StatTile`,
`money()` / `formatDate()`.

Auth on every route: `Authorization: Bearer <accessToken>`, role `VENDOR`. Results only ever include **this
vendor's** study halls. Someone else's id returns `404`.

---

## 0. The four histories (and how they differ)

| History | What it is | Backend | In the app today |
|---|---|---|---|
| **A. Transactions** | Money **students paid** for bookings at your halls (and refunds). Online money is collected by SPACZ; Pay at Hall money is collected by you. | `/api/vendors/me/transactions…` | **new** |
| **B. Booking history** | Your bookings (list) and each booking's timeline: status changes, payment attempts, transactions | `/api/vendors/me/bookings`, `/api/bookings/{id}`, `/api/bookings/{id}/history` | exists (`BookingDetailScreen`, `VendorActivity`); add the `transactions` part |
| **C. Pay at Hall history** | Every Pay at Hall order: paid, expired, cancelled, still open | `/api/payments/pay-at-hall/history` | **new** (only the *pending* list exists) |
| **D. Wallet & payouts** | What SPACZ owes you (earnings − charges − withdrawals) and your withdrawals | `/api/vendors/me/wallet…`, `/api/vendors/me/withdrawals…` | see the wallet guide |

Copy for the UI: **Transactions = what students paid.** **Wallet = what SPACZ pays you.** For example, a ₹120 online
booking shows as a ₹120 PAYMENT in Transactions and as a ₹108 earning in the Wallet (after commission).

---

## 1. Backend routes

| # | Method & path | Purpose |
|---|---|---|
| 1 | `GET /api/vendors/me/transactions` | Transaction list (paged, filtered) |
| 2 | `GET /api/vendors/me/transactions/{transactionId}` | One transaction (receipt) |
| 3 | `GET /api/vendors/me/transactions/summary` | Totals: collected, refunded, net, per method |
| 4 | `GET /api/vendors/me/bookings` | Bookings across my halls (paged, filtered) |
| 5 | `GET /api/bookings/{id}` | One booking |
| 6 | `GET /api/bookings/{id}/history` | One booking's timeline (status changes + payments + transactions) |
| 7 | `GET /api/payments/pay-at-hall/history` | All my Pay at Hall orders, newest first (paged) |
| 8 | `GET /api/payments/pay-at-hall/pending` | Open Pay at Hall orders (already in the app) |
| 9 | `GET /api/vendors/me/wallet/entries`, `GET /api/vendors/me/withdrawals` | Wallet statement and payouts (wallet guide) |

### 1.1 `GET /api/vendors/me/transactions`

| Query | Type | Notes |
|---|---|---|
| `studyHallId` | number | one hall (hall picker) |
| `type` | `PAYMENT` \| `REFUND` | |
| `status` | `SUCCESS` \| `PENDING` | `PENDING` only happens for refunds being processed |
| `paymentMethod` | `ONLINE` \| `PAY_AT_HALL` | |
| `bookingId` | number | transactions of one booking |
| `from`, `to` | `yyyy-MM-dd` | inclusive, India time; `to` before `from` → `422 INVALID_DATE_RANGE` |
| `page`, `size` | number | 0-based, size max 100 (default 20) |
| `sort` | `occurredAt,desc` (default) \| `amount,desc` \| `completedAt,desc` | other fields → `400 INVALID_SORT` |

```json
{
  "content": [
    {
      "id": 301,
      "transactionReference": "TXN-7KQ2M9XPA4RT",
      "type": "PAYMENT",
      "status": "SUCCESS",
      "paymentId": 88,
      "bookingId": 5531,
      "bookingReference": "SPZ-7KQ2M9XW4D",
      "userId": 9012,
      "vendorId": 77,
      "studyHallId": 42,
      "paymentMethod": "ONLINE",
      "amount": 120.00,
      "currency": "INR",
      "gatewayProvider": "RAZORPAY",
      "gatewayOrderId": "order_Pk2...",
      "gatewayPaymentId": "pay_Pk2...",
      "gatewayRefundId": null,
      "collectedByVendorId": null,
      "description": "Seat A1, Focus Hall Ameerpet, 21 Nov 2026",
      "occurredAt": "2026-11-21T10:15:30Z",
      "completedAt": "2026-11-21T10:15:30Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 37, "totalPages": 2, "first": true, "last": false
}
```

* `type: PAYMENT`: money received. For `ONLINE` it was collected by SPACZ (Razorpay ids are set). For `PAY_AT_HALL`
  you collected it (`collectedByVendorId` = you, gateway fields are `null`).
* `type: REFUND`: money returned to the student (always the full payment). `status: PENDING` while the refund
  runs, then `SUCCESS` with `gatewayRefundId`.
* `description` for a refund is the reason.

### 1.2 `GET /api/vendors/me/transactions/{transactionId}`

Same object as one list item. `404` if it isn't yours.

### 1.3 `GET /api/vendors/me/transactions/summary`

Query: `studyHallId`, `paymentMethod`, `from`, `to` (same rules as 1.1).

```json
{
  "from": "2026-11-01",
  "to": "2026-11-30",
  "currency": "INR",
  "paymentCount": 42,
  "collected": 25400.00,
  "refundCount": 1,
  "refunded": 120.00,
  "refundPending": 0.00,
  "net": 25280.00,
  "collectedByMethod": { "ONLINE": 18000.00, "PAY_AT_HALL": 7400.00 }
}
```

`net = collected − refunded − refundPending`.

### 1.4 `GET /api/vendors/me/bookings`

| Query | Notes |
|---|---|
| `studyHallId` | one hall |
| `status` | `PENDING` \| `CONFIRMED` \| `CANCELLED` \| `COMPLETED` \| `EXPIRED` |
| `from`, `to` | `yyyy-MM-dd`; bookings **overlapping** this range |
| `page`, `size`, `sort` | sort: `createdAt` (default desc) \| `startDate` \| `endDate` \| `status` \| `totalPrice` |

Each item is `{ booking: BookingResponse, student: { userId, name, phone, email, account } }`.
`BookingResponse` fields: `id, bookingReference, userId, studyHallId, studyHallName, studyHallCity, seatId,
seatNumber, programId, programName, plan (DAILY|MONTHLY), startDate, endDate, units, unitPrice, totalPrice, status,
holdExpiresAt, confirmedAt, cancelledAt, cancellationReason, createdAt, updatedAt, baseAmount, discountAmount,
offerId, offerTitle, platformCharges, taxes`.

> The app's `getVendorBookings()` currently downloads **all** pages (`fetchAll`). For a history list use the
> paged call from §3 (`getVendorBookingsPage`) with infinite scroll instead.

### 1.5 `GET /api/bookings/{id}/history`

```json
{
  "bookingId": 5531,
  "bookingReference": "SPZ-7KQ2M9XW4D",
  "currentStatus": "CONFIRMED",
  "totalPrice": 120.00,
  "statusHistory": [
    { "fromStatus": null, "toStatus": "PENDING", "actorType": "USER", "actorId": 9012, "paymentId": null,
      "reason": null, "at": "2026-11-21T10:10:00Z" },
    { "fromStatus": "PENDING", "toStatus": "CONFIRMED", "actorType": "SYSTEM", "actorId": null, "paymentId": 88,
      "reason": "Payment received", "at": "2026-11-21T10:15:30Z" }
  ],
  "payments": [
    { "paymentId": 87, "paymentMethod": "ONLINE", "status": "FAILED", "amount": 120.00, "currency": "INR",
      "failureReason": "Payment declined by bank", "createdAt": "…", "updatedAt": "…" },
    { "paymentId": 88, "paymentMethod": "ONLINE", "status": "SUCCESS", "amount": 120.00, "currency": "INR",
      "failureReason": null, "createdAt": "…", "updatedAt": "…" }
  ],
  "transactions": [ { "...": "same shape as 1.1 items" } ]
}
```

All three lists are oldest first. `actorType`: `USER` (student) · `VENDOR` (you, e.g. Pay at Hall confirm) ·
`ADMIN` · `SYSTEM` (payment, expiry, auto-complete).

### 1.6 `GET /api/payments/pay-at-hall/history?page=&size=`

Newest first, size max 100. Item:

```json
{
  "paymentId": 91, "userId": 9013, "bookingId": 5540, "bookingReference": "SPZ-Q8W2M4X7ZP",
  "studyHallId": 42, "seatId": 7, "seatNumber": "B3", "startDate": "2026-11-22", "endDate": "2026-11-22",
  "description": "Seat B3, Focus Hall Ameerpet, 22 Nov 2026", "amount": 120.00, "currency": "INR",
  "status": "SUCCESS", "createdAt": "…", "expiresAt": "…", "confirmedAt": "2026-11-22T04:01:00Z",
  "confirmedByVendorId": 77, "confirmationMethod": "CODE"
}
```

`status`: `PENDING` (waiting) · `AWAITING_VENDOR_CONFIRMATION` (code/QR verified, not confirmed yet) ·
`SUCCESS` (paid) · `EXPIRED` · `CANCELLED`. `confirmationMethod`: `QR` | `CODE` | `null`.

---

## 2. Frontend routes (navigation)

### 2.1 `src/navigation/types.ts` — add to `RootStackParamList`

```ts
  // TRANSACTIONS & HISTORY
  Transactions: { studyHallId?: number; bookingId?: number } | undefined;
  TransactionDetail: { transactionId: number };
  PayAtHallHistory: undefined;
  BookingsHistory: { status?: string } | undefined;
```

(`BookingDetail: { bookingId }`, `PayAtHallPending` and the wallet routes already exist.)

### 2.2 `src/navigation/AppNavigator.tsx`

```tsx
import TransactionsScreen from '../screens/TransactionsScreen';
import TransactionDetailScreen from '../screens/TransactionDetailScreen';
import PayAtHallHistoryScreen from '../screens/PayAtHallHistoryScreen';
import BookingsHistoryScreen from '../screens/BookingsHistoryScreen';

      {/* TRANSACTIONS & HISTORY */}
      <Stack.Screen name="Transactions" component={TransactionsScreen} />
      <Stack.Screen name="TransactionDetail" component={TransactionDetailScreen} />
      <Stack.Screen name="PayAtHallHistory" component={PayAtHallHistoryScreen} />
      <Stack.Screen name="BookingsHistory" component={BookingsHistoryScreen} />
```

### 2.3 Entry points

`ProfileScreen` menu (next to "Wallet & payouts"):

```tsx
<MenuRow icon="receipt-outline" label="Transactions" onPress={() => navigation.navigate('Transactions')} />
<MenuRow icon="time-outline" label="Booking history" onPress={() => navigation.navigate('BookingsHistory')} />
<MenuRow icon="storefront-outline" label="Pay at Hall history" onPress={() => navigation.navigate('PayAtHallHistory')} />
```

Also:
- `WalletScreen` → link "View all transactions" → `Transactions`.
- `PayAtHallPendingScreen` → header button "History" → `PayAtHallHistory`.
- `BookingDetailScreen` → each transaction row → `TransactionDetail`.
- Optional Home card "This month: ₹{net} collected" → `Transactions` (from the summary call).

### 2.4 Screen map

```
Profile ─┬─► Transactions ─► TransactionDetail ─► BookingDetail
         ├─► BookingsHistory ─► BookingDetail (timeline + payments + transactions) ─► TransactionDetail
         ├─► PayAtHallHistory ─► BookingDetail
         └─► Wallet (wallet guide) ─► WalletStatement / Withdrawals
PayAtHallPending ─► PayAtHallHistory
```

| Route | Params | Backend calls |
|---|---|---|
| `Transactions` | `studyHallId?`, `bookingId?` | #3 summary + #1 list |
| `TransactionDetail` | `transactionId` | #2 |
| `BookingsHistory` | `status?` | #4 (paged) |
| `BookingDetail` (exists) | `bookingId` | #5 + #6 |
| `PayAtHallHistory` | – | #7 (paged) |

---

## 3. API module — `src/api/history.ts` (paste as-is)

```ts
// src/api/history.ts
// Transactions at my study halls, Pay at Hall history and paged booking history (role VENDOR).
// See docs/PARTNER_APP_TRANSACTIONS_HISTORY.md.
import client from './client';
import type { PageResponse } from './notifications';

export type TransactionType = 'PAYMENT' | 'REFUND';
export type TransactionStatus = 'PENDING' | 'SUCCESS';
export type PaymentMethod = 'ONLINE' | 'PAY_AT_HALL';

export type Transaction = {
  id: number;
  transactionReference: string;      // TXN-XXXXXXXXXXXX
  type: TransactionType;
  status: TransactionStatus;
  paymentId: number;
  bookingId: number;
  bookingReference: string;
  userId: number;
  vendorId: number;
  studyHallId: number;
  paymentMethod: PaymentMethod;
  amount: number;
  currency: string;
  gatewayProvider: string | null;
  gatewayOrderId: string | null;
  gatewayPaymentId: string | null;
  gatewayRefundId: string | null;
  collectedByVendorId: number | null;
  description: string | null;
  occurredAt: string;
  completedAt: string | null;
};

export type TransactionSummary = {
  from: string | null;
  to: string | null;
  currency: string;
  paymentCount: number;
  collected: number;
  refundCount: number;
  refunded: number;
  refundPending: number;
  net: number;
  collectedByMethod: Partial<Record<PaymentMethod, number>>;
};

export type TransactionFilters = {
  studyHallId?: number;
  type?: TransactionType;
  status?: TransactionStatus;
  paymentMethod?: PaymentMethod;
  bookingId?: number;
  from?: string;   // yyyy-MM-dd
  to?: string;     // yyyy-MM-dd (inclusive)
};

export type TransactionSort = 'occurredAt,desc' | 'occurredAt,asc' | 'amount,desc' | 'amount,asc';

/** 1. Transactions at my halls, newest first. */
export async function getTransactions(filters: TransactionFilters = {}, page = 0, size = 20,
                                      sort: TransactionSort = 'occurredAt,desc'): Promise<PageResponse<Transaction>> {
  const { data } = await client.get<PageResponse<Transaction>>('/api/vendors/me/transactions', {
    params: { ...filters, page, size, sort },
  });
  return data;
}

/** 2. One transaction (404 when not mine). */
export async function getTransaction(id: number): Promise<Transaction> {
  const { data } = await client.get<Transaction>(`/api/vendors/me/transactions/${id}`);
  return data;
}

/** 3. Totals over the same filters (type / status / bookingId are ignored). */
export async function getTransactionSummary(
  filters: Pick<TransactionFilters, 'studyHallId' | 'paymentMethod' | 'from' | 'to'> = {},
): Promise<TransactionSummary> {
  const { data } = await client.get<TransactionSummary>('/api/vendors/me/transactions/summary', { params: filters });
  return data;
}

// ── Pay at Hall history ──

export type PayAtHallStatus = 'PENDING' | 'AWAITING_VENDOR_CONFIRMATION' | 'SUCCESS' | 'EXPIRED' | 'CANCELLED';

export type PayAtHallHistoryItem = {
  paymentId: number;
  userId: number;
  bookingId: number;
  bookingReference: string;
  studyHallId: number;
  seatId: number;
  seatNumber: string;
  startDate: string;
  endDate: string;
  description: string | null;
  amount: number;
  currency: string;
  status: PayAtHallStatus | string;
  createdAt: string;
  expiresAt: string | null;
  confirmedAt: string | null;
  confirmedByVendorId: number | null;
  confirmationMethod: 'QR' | 'CODE' | null;
};

/** 7. All my Pay at Hall orders, newest first. */
export async function getPayAtHallHistory(page = 0, size = 20): Promise<PageResponse<PayAtHallHistoryItem>> {
  const { data } = await client.get<PageResponse<PayAtHallHistoryItem>>('/api/payments/pay-at-hall/history', {
    params: { page, size },
  });
  return data;
}

// ── Booking history (paged) ──

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'EXPIRED';

export type VendorBookingItem = {
  booking: {
    id: number; bookingReference: string; userId: number; studyHallId: number; studyHallName: string;
    studyHallCity: string | null; seatId: number; seatNumber: string; programId: number | null;
    programName: string | null; plan: 'DAILY' | 'MONTHLY'; startDate: string; endDate: string; units: number;
    unitPrice: number; totalPrice: number; status: BookingStatus; holdExpiresAt: string | null;
    confirmedAt: string | null; cancelledAt: string | null; cancellationReason: string | null;
    createdAt: string; updatedAt: string; baseAmount: number; discountAmount: number; offerId: number | null;
    offerTitle: string | null; platformCharges: number; taxes: number;
  };
  student: { userId: number | null; name: string | null; phone: string | null; email: string | null; account: boolean } | null;
};

/** 4. One page of bookings across my halls (newest first by default). */
export async function getVendorBookingsPage(params: {
  studyHallId?: number; status?: BookingStatus; from?: string; to?: string;
  page?: number; size?: number; sort?: string;
} = {}): Promise<PageResponse<VendorBookingItem>> {
  const { page = 0, size = 20, sort = 'createdAt,desc', ...filters } = params;
  const { data } = await client.get<PageResponse<VendorBookingItem>>('/api/vendors/me/bookings', {
    params: { ...filters, page, size, sort },
  });
  return data;
}
```

### 3.1 Update `BookingHistory` in `src/api/partner.ts`

The backend also returns the booking's transactions. Add the field:

```ts
import type { Transaction } from './history';

export type BookingHistory = {
  bookingId: number;
  bookingReference: string;
  currentStatus: string;
  totalPrice: number;
  statusHistory: BookingStatusChange[];
  payments: BookingPaymentSummary[];
  transactions: Transaction[];          // ← new: money received / refunded for this booking
};
```

---

## 4. Helpers — `src/utils/history.ts` (paste as-is)

```ts
// src/utils/history.ts
import type { Tone } from '../components/ui';
import type { PaymentMethod, Transaction } from '../api/history';

/** Dates in the business time zone (India), as the backend expects (yyyy-MM-dd). */
export function ymdIST(d: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(d);
}

export type RangeKey = 'TODAY' | 'LAST_7' | 'THIS_MONTH' | 'LAST_MONTH' | 'ALL' | 'CUSTOM';

export const RANGE_OPTIONS: { value: RangeKey; label: string }[] = [
  { value: 'TODAY', label: 'Today' },
  { value: 'LAST_7', label: '7 days' },
  { value: 'THIS_MONTH', label: 'This month' },
  { value: 'LAST_MONTH', label: 'Last month' },
  { value: 'ALL', label: 'All time' },
  { value: 'CUSTOM', label: 'Custom' },
];

/** Preset → { from, to } (inclusive). ALL / CUSTOM → {} (CUSTOM: use the date pickers). */
export function rangeDates(key: RangeKey, now = new Date()): { from?: string; to?: string } {
  const today = ymdIST(now);
  const [y, m] = today.split('-').map(Number);
  switch (key) {
    case 'TODAY': return { from: today, to: today };
    case 'LAST_7': return { from: ymdIST(new Date(now.getTime() - 6 * 86400000)), to: today };
    case 'THIS_MONTH': return { from: `${y}-${String(m).padStart(2, '0')}-01`, to: today };
    case 'LAST_MONTH': {
      const py = m === 1 ? y - 1 : y;
      const pm = m === 1 ? 12 : m - 1;
      const last = new Date(Date.UTC(py, pm, 0)).getUTCDate();
      const mm = String(pm).padStart(2, '0');
      return { from: `${py}-${mm}-01`, to: `${py}-${mm}-${last}` };
    }
    default: return {};
  }
}

export const METHOD_LABEL: Record<PaymentMethod, string> = { ONLINE: 'Online', PAY_AT_HALL: 'Pay at Hall' };

/** Title, sign, tone and icon of a transaction row. */
export function transactionLook(t: Transaction): { title: string; sign: '+' | '−'; tone: Tone; icon: string; status: string } {
  if (t.type === 'REFUND') {
    return {
      title: 'Refund to student', sign: '−', tone: 'danger', icon: 'return-up-back',
      status: t.status === 'PENDING' ? 'Refund processing' : 'Refunded',
    };
  }
  return t.paymentMethod === 'ONLINE'
    ? { title: 'Online payment', sign: '+', tone: 'success', icon: 'card', status: 'Received by SPACZ' }
    : { title: 'Pay at Hall', sign: '+', tone: 'success', icon: 'storefront', status: 'Collected by you' };
}

export const PAH_STATUS: Record<string, { label: string; tone: Tone }> = {
  PENDING: { label: 'Waiting', tone: 'warning' },
  AWAITING_VENDOR_CONFIRMATION: { label: 'Verified · confirm', tone: 'brand' },
  SUCCESS: { label: 'Paid', tone: 'success' },
  EXPIRED: { label: 'Expired', tone: 'neutral' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
};

export const ACTOR_LABEL: Record<string, string> = {
  USER: 'Student', VENDOR: 'You', ADMIN: 'SPACZ admin', SYSTEM: 'System',
};

export const PAYMENT_STATUS_LABEL: Record<string, { label: string; tone: Tone }> = {
  CREATED: { label: 'Started', tone: 'neutral' },
  PENDING: { label: 'Pending', tone: 'warning' },
  PROCESSING: { label: 'Processing', tone: 'warning' },
  AWAITING_VENDOR_CONFIRMATION: { label: 'Awaiting your confirmation', tone: 'brand' },
  SUCCESS: { label: 'Paid', tone: 'success' },
  FAILED: { label: 'Failed', tone: 'danger' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
  EXPIRED: { label: 'Expired', tone: 'neutral' },
  REFUND_PENDING: { label: 'Refund processing', tone: 'warning' },
  REFUNDED: { label: 'Refunded', tone: 'neutral' },
};
```

---

## 5. Screens

General rules (same as the wallet screens): `Screen` with `onBack`, loading / error / empty with
`StateView`, `RefreshControl`, `useFocusEffect` reload, infinite scroll with `onEndReached` until `last`,
and a `loadingMore` guard so the same page isn't requested twice.

### 5.1 `TransactionsScreen` (route `Transactions`)

**State:** `range: RangeKey` (default `THIS_MONTH`), custom `from`/`to`, `method: 'ALL' | PaymentMethod`,
`type: 'ALL' | TransactionType`, `studyHallId` (from params or `HallPicker`), `sort`.

**Calls** (re-run both when a filter changes, and reset to page 0):
```ts
const dates = range === 'CUSTOM' ? { from, to } : rangeDates(range);
const common = { studyHallId, ...dates, paymentMethod: method === 'ALL' ? undefined : method };
const [summary, first] = await Promise.all([
  getTransactionSummary(common),
  getTransactions({ ...common, type: type === 'ALL' ? undefined : type, bookingId: route.params?.bookingId }, 0),
]);
```

**Layout:**
1. Filter bar:
   - `Chip`s from `RANGE_OPTIONS`. `CUSTOM` opens two date pickers (the app already uses
     `react-native-modal-datetime-picker`); format with `ymdIST`.
   - Hall picker (reuse `components/HallPicker.tsx`) when the vendor has more than one hall.
   - `Segmented`: All · Online · Pay at Hall.
2. **Summary card** (`StatGrid`):
   - `StatTile` "Net" = `money(summary.net)` (tone success);
   - "Collected" = `money(collected)` with `{paymentCount} payments` below;
   - "Refunded" = `money(refunded + refundPending)` with `{refundCount}` below (tone danger, hide when 0).
   - Below it, a split bar or two lines: Online `money(collectedByMethod.ONLINE ?? 0)` ·
     Pay at Hall `money(collectedByMethod.PAY_AT_HALL ?? 0)`.
   - Footnote: "Online payments are collected by SPACZ and paid to you via your Wallet."
3. Type chips: All · Payments · Refunds. Sort menu (optional): Newest · Oldest · Highest amount.
4. **List** (`FlatList`, grouped by day with `dayBucket()` from `notifications/meta.ts` or `formatDate(occurredAt)`).
   Row (`TransactionRow`, put in `src/components/TransactionRow.tsx`):

| Part | Value |
|---|---|
| icon | `transactionLook(t).icon` |
| title | `transactionLook(t).title` |
| subtitle | `bookingReference` · `formatDate(occurredAt, true)` |
| amount | `` `${look.sign}${money(t.amount)}` ``, green / red |
| badge | `look.status` (tone `warning` when `status === 'PENDING'`) |
| tap | `TransactionDetail { transactionId: t.id }` |

5. Empty: "No transactions in this period".
6. Errors: `INVALID_DATE_RANGE` → "End date must be after start date" (validate before calling as well).

### 5.2 `TransactionDetailScreen` (route `TransactionDetail`)

`getTransaction(transactionId)`; `404` → `StateView kind="empty" title="Transaction not found"`.

- Header: `look.sign + money(amount)` large, `look.title`, `Badge` with `look.status`.
- `KeyValue` rows:
  - Reference `transactionReference` (selectable / copy);
  - Date `formatDate(occurredAt, true)`; Completed `formatDate(completedAt, true)` when set;
  - Method `METHOD_LABEL[paymentMethod]`;
  - Booking `bookingReference` (tap → `BookingDetail { bookingId }`);
  - Description;
  - ONLINE: "Razorpay payment ID" `gatewayPaymentId`, "Order ID" `gatewayOrderId`;
  - REFUND: "Refund ID" `gatewayRefundId`, or "Processing" while `status === 'PENDING'`;
  - PAY_AT_HALL: "Collected by" "You" (when `collectedByVendorId` is set).
- Button "View booking" → `BookingDetail`.
- Optional "Share receipt" via `Share.share({ message: … })` with reference, amount, date, booking.

### 5.3 `BookingsHistoryScreen` (route `BookingsHistory`)

- Status chips: All · Confirmed · Pending · Completed · Cancelled · Expired (initial from `route.params?.status`).
- Optional range chips (`rangeDates`) → `from`/`to` (bookings **overlapping** the range).
- Hall picker if more than one hall.
- Paged `getVendorBookingsPage({ status, studyHallId, from, to, page })`.
- Row: student name (or "Walk-in"), seat `seatNumber` · `studyHallName`, dates
  `formatDate(startDate)`–`formatDate(endDate)` (plan DAILY/MONTHLY), `money(totalPrice)`,
  `Badge label={status} tone={bookingTone(status)}` (existing helper in `utils/format.ts`).
- Tap → `BookingDetail { bookingId: item.booking.id }`.

### 5.4 `BookingDetailScreen` (exists) — add a "Money" section

It already loads `getBooking()` + `getBookingHistory()`. Add:

1. **Price breakdown** (from `GET /api/bookings/{id}`): Seat price `baseAmount`, Discount `−discountAmount`
   (with `offerTitle`), Platform charges `platformCharges`, Taxes `taxes`, **Total** `totalPrice`.
2. **Payments** (`history.payments`, oldest first): method, `PAYMENT_STATUS_LABEL[status]`, amount, time,
   `failureReason` in small red text for FAILED ones. Shows failed and expired attempts as well.
3. **Transactions** (`history.transactions`): `TransactionRow`s → `TransactionDetail`.
4. **Timeline** (`history.statusHistory`), probably already rendered: use `ACTOR_LABEL[actorType]` for
   "by Student / You / System" and show `reason`.

### 5.5 `PayAtHallHistoryScreen` (route `PayAtHallHistory`)

- Paged `getPayAtHallHistory(page)`; infinite scroll.
- Optional client-side status chips: All · Paid · Expired · Cancelled · Open (`PENDING` +
  `AWAITING_VENDOR_CONFIRMATION`). The API has no status filter, so filter the loaded pages.
- Row: `money(amount)`, `Badge` from `PAH_STATUS[status]`, seat `seatNumber` · `formatDate(startDate)`,
  `bookingReference`, created `timeAgo(createdAt)`; for SUCCESS: "Confirmed {formatDate(confirmedAt, true)}
  via {confirmationMethod === 'QR' ? 'QR scan' : 'code'}".
- Tap → `BookingDetail { bookingId }`. Open orders: a "Collect" button → `PayAtHallPending { highlightPaymentId }`.

---

## 6. Error codes

| `error` | HTTP | Where | UI |
|---|---|---|---|
| `INVALID_DATE_RANGE` | 422 | transactions, summary | "End date must be on or after start date" |
| `INVALID_SORT` | 400 | transactions, bookings | bug: only use the sort values in §1 |
| `NOT_FOUND` | 404 | transaction / booking detail | "Not found" state |
| `VALIDATION_ERROR` | 400 | bad enum value in a filter | `apiErrorMessage(err)` |

---

## 7. Test checklist

1. Online booking paid by a student → **Transactions** shows `+₹120 · Online payment · Received by SPACZ`,
   and the summary shows Collected ₹120, Online ₹120.
2. Pay at Hall booking confirmed by you → `+₹120 · Pay at Hall · Collected by you`; summary Pay at Hall ₹120;
   **Pay at Hall history** shows it as Paid via code/QR.
3. Leave a Pay at Hall order unpaid until it expires → appears as **Expired** in Pay at Hall history; no transaction.
4. Booking detail → the Money section shows payments (including failed attempts), the transaction, and the timeline.
5. Filters: Today / This month / Custom range / Online only / Refunds only; hall picker; summary follows.
6. Open another vendor's transaction id via a deep link → "Not found".
