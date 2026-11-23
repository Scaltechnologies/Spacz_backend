# Partner App — Wallet & Withdrawals: Frontend Implementation Guide

Step-by-step guide to build the wallet UI in **Spacz_Partner** (Expo + React Navigation native-stack + axios).
It follows the app's existing patterns: `src/api/client.ts` + `apiErrorMessage`, the `Screen` / `Card` /
`StateView` / `Badge` / `KeyValue` / `Chip` / `FormField` kit in `src/components/ui.tsx`, `money()` / `formatDate()`
in `src/utils/format.ts`, and `tokens.colors`.

The backend API reference (all fields, all error codes) is in [PARTNER_APP_WALLET.md](PARTNER_APP_WALLET.md).

---

## 0. File plan

| Action | File | What |
|---|---|---|
| **new** | `src/api/wallet.ts` | types + 10 API functions (§2, paste as-is) |
| **new** | `src/utils/wallet.ts` | labels, tones, validators, error mapping (§3, paste as-is) |
| **new** | `src/screens/WalletScreen.tsx` | wallet home |
| **new** | `src/screens/WalletStatementScreen.tsx` | statement (entries) |
| **new** | `src/screens/PayoutAccountsScreen.tsx` | saved UPI / bank accounts |
| **new** | `src/screens/AddPayoutAccountScreen.tsx` | add UPI / bank form |
| **new** | `src/screens/WithdrawScreen.tsx` | request withdrawal form |
| **new** | `src/screens/WithdrawalsScreen.tsx` | withdrawal history |
| **new** | `src/screens/WithdrawalDetailScreen.tsx` | one withdrawal (push deep link) |
| edit | `src/navigation/types.ts` | 7 routes (§1.1) |
| edit | `src/navigation/AppNavigator.tsx` | register 7 screens (§1.2) |
| edit | `src/screens/ProfileScreen.tsx` | "Wallet & payouts" menu row (§1.3) |
| edit | `src/api/notifications.ts` | `PAYOUT` category (§5) |
| edit | `src/notifications/meta.ts` | `PAYOUT` icon/label (§5) |
| edit | `src/notifications/routing.ts` | `vendor-withdrawal` deep link (§5) |
| optional | `src/screens/HomeScreen.tsx` | wallet balance card (§4.1, "Home card") |

---

## 1. Navigation (frontend routes)

### 1.1 `src/navigation/types.ts` — add to `RootStackParamList`

```ts
  // WALLET & WITHDRAWALS
  Wallet: undefined;
  WalletStatement: undefined;
  PayoutAccounts: { pickForWithdraw?: boolean } | undefined;
  AddPayoutAccount: { method?: 'UPI' | 'BANK_ACCOUNT' } | undefined;
  Withdraw: { payoutAccountId?: number } | undefined;
  Withdrawals: undefined;
  WithdrawalDetail: { withdrawalId: number };
```

### 1.2 `src/navigation/AppNavigator.tsx`

```tsx
// Wallet & withdrawals
import WalletScreen from '../screens/WalletScreen';
import WalletStatementScreen from '../screens/WalletStatementScreen';
import PayoutAccountsScreen from '../screens/PayoutAccountsScreen';
import AddPayoutAccountScreen from '../screens/AddPayoutAccountScreen';
import WithdrawScreen from '../screens/WithdrawScreen';
import WithdrawalsScreen from '../screens/WithdrawalsScreen';
import WithdrawalDetailScreen from '../screens/WithdrawalDetailScreen';

// …inside <Stack.Navigator>, after the NOTIFICATIONS block:
      {/* WALLET & WITHDRAWALS */}
      <Stack.Screen name="Wallet" component={WalletScreen} />
      <Stack.Screen name="WalletStatement" component={WalletStatementScreen} />
      <Stack.Screen name="PayoutAccounts" component={PayoutAccountsScreen} />
      <Stack.Screen name="AddPayoutAccount" component={AddPayoutAccountScreen} />
      <Stack.Screen name="Withdraw" component={WithdrawScreen} />
      <Stack.Screen name="Withdrawals" component={WithdrawalsScreen} />
      <Stack.Screen name="WithdrawalDetail" component={WithdrawalDetailScreen} />
```

### 1.3 `src/screens/ProfileScreen.tsx` — menu row

Add above "Pay at Hall orders":

```tsx
<MenuRow icon="wallet-outline" label="Wallet & payouts" onPress={() => navigation.navigate('Wallet')} />
```

### 1.4 Screen map

```
Profile ─► Wallet ─┬─► Withdraw ─► (success) ─► WithdrawalDetail
   (Home card) ──┘ ├─► WalletStatement ─► BookingDetail / WithdrawalDetail
                   ├─► Withdrawals ─► WithdrawalDetail
                   └─► PayoutAccounts ─► AddPayoutAccount
Push / inbox (screen = vendor-withdrawal) ─► WithdrawalDetail
```

| Route | Params | Backend calls |
|---|---|---|
| `Wallet` | – | `GET /api/vendors/me/wallet`, `GET /api/vendors/me/wallet/entries?size=5` |
| `WalletStatement` | – | `GET /api/vendors/me/wallet/entries?type=&page=` |
| `PayoutAccounts` | `pickForWithdraw?` | `GET /api/vendors/me/payout-accounts`, `PATCH …/{id}/default`, `DELETE …/{id}` |
| `AddPayoutAccount` | `method?` | `POST /api/vendors/me/payout-accounts` |
| `Withdraw` | `payoutAccountId?` | `GET /api/vendors/me/wallet`, `GET /api/vendors/me/payout-accounts`, `POST /api/vendors/me/withdrawals` |
| `Withdrawals` | – | `GET /api/vendors/me/withdrawals?status=&page=` |
| `WithdrawalDetail` | `withdrawalId` | `GET /api/vendors/me/withdrawals/{id}`, `POST …/{id}/cancel` |

---

## 2. API module — `src/api/wallet.ts` (paste as-is)

```ts
// src/api/wallet.ts
// Partner wallet: balance, statement, payout accounts (UPI / bank) and withdrawal requests.
// Backend: studyhall-service /api/vendors/me/... (role VENDOR). See docs/PARTNER_APP_WALLET.md.
import client from './client';
import type { PageResponse } from './notifications';

export type PayoutMethod = 'UPI' | 'BANK_ACCOUNT';
export type WithdrawalStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'REJECTED' | 'CANCELLED';
export type WalletEntryType =
  | 'BOOKING_EARNING' | 'BOOKING_REFUND' | 'PAY_AT_HALL_FEE' | 'WITHDRAWAL' | 'WITHDRAWAL_REVERSAL';
export type WithdrawBlockedReason =
  | 'VENDOR_NOT_APPROVED' | 'WITHDRAWAL_IN_PROGRESS' | 'NO_PAYOUT_ACCOUNT' | 'BELOW_MINIMUM';

export type Withdrawal = {
  id: number;
  reference: string;              // WDR-XXXXXXXXXXXX
  amount: number;
  currency: string;
  status: WithdrawalStatus;
  payoutAccountId: number;
  payoutMethod: PayoutMethod;
  upiId: string | null;
  accountHolderName: string | null;
  accountNumberMasked: string | null;
  ifscCode: string | null;
  bankName: string | null;
  payoutDisplayName: string;      // "focushall@okaxis" | "HDFC Bank ••6789"
  note: string | null;
  rejectionReason: string | null;
  payoutReference: string | null; // UTR, once PAID
  requestedAt: string;
  processingAt: string | null;
  paidAt: string | null;
  rejectedAt: string | null;
  cancelledAt: string | null;
};

export type WalletSummary = {
  currency: string;
  availableBalance: number;       // may be negative (Pay-at-Hall dues)
  pendingBalance: number;
  inWithdrawal: number;
  totalEarned: number;
  totalWithdrawn: number;
  minWithdrawalAmount: number;
  maxWithdrawalAmount: number;
  settlementHoldHours: number;
  canWithdraw: boolean;
  withdrawBlockedReason: WithdrawBlockedReason | null;
  openWithdrawal: Withdrawal | null;
};

export type WalletEntry = {
  id: number;
  type: WalletEntryType;
  amount: number;                 // > 0 credit, < 0 debit
  currency: string;
  description: string | null;
  bookingId: number | null;
  bookingReference: string | null;
  withdrawalId: number | null;
  availableAt: string;
  pending: boolean;
  createdAt: string;
};

export type PayoutAccount = {
  id: number;
  method: PayoutMethod;
  upiId: string | null;
  accountHolderName: string | null;
  accountNumberMasked: string | null;
  ifscCode: string | null;
  bankName: string | null;
  displayName: string;
  isDefault: boolean;
  createdAt: string;
};

export type AddPayoutAccountInput =
  | { method: 'UPI'; upiId: string; makeDefault?: boolean }
  | { method: 'BANK_ACCOUNT'; accountHolderName: string; accountNumber: string; ifscCode: string;
      bankName?: string; makeDefault?: boolean };

const BASE = '/api/vendors/me';

/** 1. Balance, limits, withdraw-button state, open request. */
export async function getWallet(): Promise<WalletSummary> {
  const { data } = await client.get<WalletSummary>(`${BASE}/wallet`);
  return data;
}

/** 2. Statement, newest first. */
export async function getWalletEntries(params: { type?: WalletEntryType; page?: number; size?: number } = {}):
  Promise<PageResponse<WalletEntry>> {
  const { data } = await client.get<PageResponse<WalletEntry>>(`${BASE}/wallet/entries`, {
    params: { type: params.type, page: params.page ?? 0, size: params.size ?? 20 },
  });
  return data;
}

/** 3. Saved payout accounts, default first. */
export async function getPayoutAccounts(): Promise<PayoutAccount[]> {
  const { data } = await client.get<PayoutAccount[]>(`${BASE}/payout-accounts`);
  return data;
}

/** 4. Add a UPI ID or bank account (201). */
export async function addPayoutAccount(input: AddPayoutAccountInput): Promise<PayoutAccount> {
  const { data } = await client.post<PayoutAccount>(`${BASE}/payout-accounts`, input);
  return data;
}

/** 5. Make default. */
export async function setDefaultPayoutAccount(id: number): Promise<PayoutAccount> {
  const { data } = await client.patch<PayoutAccount>(`${BASE}/payout-accounts/${id}/default`);
  return data;
}

/** 6. Remove (204). */
export async function deletePayoutAccount(id: number): Promise<void> {
  await client.delete(`${BASE}/payout-accounts/${id}`);
}

/** 7. Request a withdrawal (201). amount: rupees, max 2 decimals. */
export async function requestWithdrawal(input: { amount: number; payoutAccountId: number; note?: string }):
  Promise<Withdrawal> {
  const { data } = await client.post<Withdrawal>(`${BASE}/withdrawals`, input);
  return data;
}

/** 8. Withdrawal history, newest first. */
export async function getWithdrawals(params: { status?: WithdrawalStatus; page?: number; size?: number } = {}):
  Promise<PageResponse<Withdrawal>> {
  const { data } = await client.get<PageResponse<Withdrawal>>(`${BASE}/withdrawals`, {
    params: { status: params.status, page: params.page ?? 0, size: params.size ?? 20 },
  });
  return data;
}

/** 9. One withdrawal (404 when not this vendor's). */
export async function getWithdrawal(id: number): Promise<Withdrawal> {
  const { data } = await client.get<Withdrawal>(`${BASE}/withdrawals/${id}`);
  return data;
}

/** 10. Cancel while PENDING (409 WITHDRAWAL_STATUS_CONFLICT afterwards). */
export async function cancelWithdrawal(id: number): Promise<Withdrawal> {
  const { data } = await client.post<Withdrawal>(`${BASE}/withdrawals/${id}/cancel`);
  return data;
}
```

---

## 3. Helpers — `src/utils/wallet.ts` (paste as-is)

```ts
// src/utils/wallet.ts
import type { AxiosError } from 'axios';
import type { Tone } from '../components/ui';
import type { WalletEntryType, WithdrawalStatus, WithdrawBlockedReason } from '../api/wallet';

export const WITHDRAWAL_STATUS: Record<WithdrawalStatus, { label: string; tone: Tone; hint: string }> = {
  PENDING: { label: 'Requested', tone: 'warning', hint: 'Waiting for the SPACZ team. You can still cancel it.' },
  PROCESSING: { label: 'Processing', tone: 'brand', hint: 'The money is being sent to your account.' },
  PAID: { label: 'Paid', tone: 'success', hint: 'Sent. Use the transfer reference (UTR) to find it in your bank app.' },
  REJECTED: { label: 'Declined', tone: 'danger', hint: 'The amount is back in your wallet.' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', hint: 'The amount is back in your wallet.' },
};

export const ENTRY_LABEL: Record<WalletEntryType, { label: string; icon: string }> = {
  BOOKING_EARNING: { label: 'Booking earning', icon: 'arrow-down-circle' },
  BOOKING_REFUND: { label: 'Refund to student', icon: 'return-up-back' },
  PAY_AT_HALL_FEE: { label: 'Pay at Hall charges', icon: 'storefront' },
  WITHDRAWAL: { label: 'Withdrawal', icon: 'arrow-up-circle' },
  WITHDRAWAL_REVERSAL: { label: 'Withdrawal returned', icon: 'refresh-circle' },
};

/** Filter chips on the statement screen. */
export const ENTRY_FILTERS: { value: WalletEntryType | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'BOOKING_EARNING', label: 'Earnings' },
  { value: 'WITHDRAWAL', label: 'Withdrawals' },
  { value: 'PAY_AT_HALL_FEE', label: 'Charges' },
  { value: 'BOOKING_REFUND', label: 'Refunds' },
];

export function blockedMessage(reason: WithdrawBlockedReason | null, min: number): string | null {
  switch (reason) {
    case 'VENDOR_NOT_APPROVED': return 'Withdrawals are available once your partner account is approved.';
    case 'WITHDRAWAL_IN_PROGRESS': return 'You have a withdrawal in progress.';
    case 'NO_PAYOUT_ACCOUNT': return 'Add a UPI ID or bank account to withdraw.';
    case 'BELOW_MINIMUM': return `You can withdraw once your available balance reaches ₹${min}.`;
    default: return null;
  }
}

// Client-side checks. Same rules as the server (which stays the source of truth).
export const UPI_RE = /^[a-z0-9._-]{2,256}@[a-z][a-z0-9]{1,63}$/;
export const ACCOUNT_RE = /^[0-9]{9,18}$/;
export const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/;
export const HOLDER_RE = /^[\p{L} .'&()-]{2,120}$/u;

/** "1,000.5" → 1000.5; null when not a valid amount with ≤ 2 decimals. */
export function parseAmount(text: string): number | null {
  const clean = text.replace(/[,\s₹]/g, '');
  if (!/^\d+(\.\d{1,2})?$/.test(clean)) return null;
  const n = Number(clean);
  return n > 0 ? n : null;
}

/** Backend error code (ApiError.error), e.g. INSUFFICIENT_BALANCE. */
export function apiErrorCode(err: unknown): string | undefined {
  return ((err as AxiosError<any>)?.response?.data as any)?.error;
}

/** INVALID_PAYOUT_ACCOUNT messages start with the field: "ifscCode: Enter a valid IFSC…". */
export function payoutFieldError(err: unknown): { field?: string; message?: string } {
  const data = (err as AxiosError<any>)?.response?.data as any;
  if (data?.error !== 'INVALID_PAYOUT_ACCOUNT' || typeof data?.message !== 'string') return {};
  const i = data.message.indexOf(':');
  return i > 0 ? { field: data.message.slice(0, i), message: data.message.slice(i + 1).trim() } : { message: data.message };
}
```

---

## 4. Screens

All screens: `<Screen title=… onBack={() => navigation.goBack()}>`; loading → `<StateView kind="loading">`;
error → `<StateView kind="error" actionLabel="Retry" onAction={load}>`; pull to refresh with
`RefreshControl` (see `PayAtHallPendingScreen`). Reload on focus with `useFocusEffect` so balances are fresh
after a withdrawal / account change:

```ts
import { useFocusEffect } from '@react-navigation/native';
useFocusEffect(useCallback(() => { load(); }, [load]));
```

### 4.1 `WalletScreen` (route `Wallet`)

**Calls:** `getWallet()` + `getWalletEntries({ size: 5 })` in parallel (`Promise.all`).

**Layout (top → bottom):**
1. **Balance card** (gradient like the Home header, or `Card` with `colors.homeCard`):
   - "Available to withdraw" + `money(availableBalance)` large.
     If `availableBalance < 0`: show `money(-availableBalance)` with label "You owe SPACZ" in `danger`.
   - Under it: "₹{pendingBalance} pending · available within {settlementHoldHours} h" (hide when 0).
   - **Withdraw** button → `navigation.navigate('Withdraw')`, `disabled={!canWithdraw}`.
   - When disabled: `blockedMessage(withdrawBlockedReason, minWithdrawalAmount)` as helper text;
     for `NO_PAYOUT_ACCOUNT` make it a link → `AddPayoutAccount`.
2. **Open withdrawal card** (only if `openWithdrawal`): reference, amount, `Badge` from `WITHDRAWAL_STATUS`,
   payoutDisplayName, requested time; tap → `WithdrawalDetail { withdrawalId }`.
3. **StatGrid** (`StatTile` ×3): Total earned · Withdrawn · In withdrawal.
4. **Menu rows:** "Payout accounts" → `PayoutAccounts` · "Withdrawal history" → `Withdrawals`.
5. **Recent activity** (`SectionHeader title="Recent activity" action="See all"` → `WalletStatement`):
   the 5 entries rendered with the `EntryRow` component from §4.2.
6. Small info text: "Online payments are added after the student pays and can be withdrawn after
   {settlementHoldHours} hours. Pay at Hall cash is already with you."

Skeleton:

```tsx
// src/screens/WalletScreen.tsx
import React, { useCallback, useState } from 'react';
import { RefreshControl, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/types';
import { Badge, Card, Screen, SectionHeader, StateView, StatGrid, StatTile } from '../components/ui';
import { Button } from '../components';
import Typography from '../components/Typography/Typography';
import tokens from '../ui/tokens';
import { apiErrorMessage } from '../api/client';
import { getWallet, getWalletEntries, WalletEntry, WalletSummary } from '../api/wallet';
import { blockedMessage, WITHDRAWAL_STATUS } from '../utils/wallet';
import { money } from '../utils/format';

type Props = NativeStackScreenProps<RootStackParamList, 'Wallet'>;
const c = tokens.colors;

export default function WalletScreen({ navigation }: Props) {
  const [wallet, setWallet] = useState<WalletSummary | null>(null);
  const [recent, setRecent] = useState<WalletEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [w, e] = await Promise.all([getWallet(), getWalletEntries({ size: 5 })]);
      setWallet(w); setRecent(e.content); setError(null);
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load wallet'));
    } finally {
      setLoading(false);
    }
  }, []);
  useFocusEffect(useCallback(() => { load(); }, [load]));

  if (!wallet) {
    return (
      <Screen title="Wallet" onBack={() => navigation.goBack()}>
        {error ? <StateView kind="error" title="Couldn't load wallet" message={error} actionLabel="Retry" onAction={load} />
          : <StateView kind="loading" title="Loading wallet…" />}
      </Screen>
    );
  }
  const owes = wallet.availableBalance < 0;
  const blocked = blockedMessage(wallet.withdrawBlockedReason, wallet.minWithdrawalAmount);

  return (
    <Screen title="Wallet" subtitle="Earnings & payouts" onBack={() => navigation.goBack()}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={c.primary} colors={[c.primary]} />}>
      <Card>
        <Typography style={{ color: c.paragraph }}>{owes ? 'You owe SPACZ' : 'Available to withdraw'}</Typography>
        <Typography weight="bold" style={{ fontSize: 32, color: owes ? c.danger : c.ink }}>
          {money(Math.abs(wallet.availableBalance))}
        </Typography>
        {wallet.pendingBalance > 0 && (
          <Typography style={{ color: c.paragraph, fontSize: 12 }}>
            {money(wallet.pendingBalance)} pending · available within {wallet.settlementHoldHours} h
          </Typography>
        )}
        <Button title="Withdraw" disabled={!wallet.canWithdraw} onPress={() => navigation.navigate('Withdraw')} />
        {!!blocked && (
          <Typography
            style={{ fontSize: 12, color: c.paragraph, marginTop: 6 }}
            onPress={wallet.withdrawBlockedReason === 'NO_PAYOUT_ACCOUNT' ? () => navigation.navigate('AddPayoutAccount') : undefined}>
            {blocked}
          </Typography>
        )}
      </Card>

      {wallet.openWithdrawal && (
        <Card onPress={() => navigation.navigate('WithdrawalDetail', { withdrawalId: wallet.openWithdrawal!.id })}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <Typography weight="semibold">{money(wallet.openWithdrawal.amount)}</Typography>
            <Badge {...WITHDRAWAL_STATUS[wallet.openWithdrawal.status]} />
          </View>
          <Typography style={{ fontSize: 12, color: c.paragraph }}>
            {wallet.openWithdrawal.reference} · to {wallet.openWithdrawal.payoutDisplayName}
          </Typography>
        </Card>
      )}

      <StatGrid>
        <StatTile label="Total earned" value={money(wallet.totalEarned)} tone="success" icon="trending-up" />
        <StatTile label="Withdrawn" value={money(wallet.totalWithdrawn)} icon="arrow-up-circle" />
        <StatTile label="In withdrawal" value={money(wallet.inWithdrawal)} tone="warning" icon="time" />
      </StatGrid>

      {/* menu rows: Payout accounts → PayoutAccounts, Withdrawal history → Withdrawals */}

      <SectionHeader title="Recent activity" action="See all" onAction={() => navigation.navigate('WalletStatement')} />
      {recent.length === 0
        ? <StateView kind="empty" title="No activity yet" message="Online booking payments will appear here." />
        : recent.map(e => <EntryRow key={e.id} entry={e} navigation={navigation} />)}
    </Screen>
  );
}
// EntryRow: see §4.2 (export it from WalletStatementScreen or put it in src/components/WalletEntryRow.tsx)
```

> Note: `Badge` takes `{ label, tone }`; spreading `WITHDRAWAL_STATUS[...]` also passes `hint`, which `Badge`
> ignores. Pass `label`/`tone` explicitly if TypeScript complains.

**Home card (optional):** on `HomeScreen`, a small card "Wallet · {money(availableBalance)} available" that
opens `Wallet` (call `getWallet()` with the dashboard load; ignore its errors).

### 4.2 `WalletStatementScreen` (route `WalletStatement`)

- Chips from `ENTRY_FILTERS` (`ALL` → no `type` param). Changing a chip resets to page 0.
- `FlatList` with infinite scroll: `onEndReached` → load `page + 1` until `last === true`.
- Group headers by day (reuse `dayBucket()` from `notifications/meta.ts`) — optional.

**`EntryRow`** (put in `src/components/WalletEntryRow.tsx`, used by Wallet + Statement):

| Part | Value |
|---|---|
| icon | `ENTRY_LABEL[type].icon` (Ionicons), green for `amount > 0`, red for `< 0` |
| title | `ENTRY_LABEL[type].label` |
| subtitle | `bookingReference` or `description` · `formatDate(createdAt, true)` |
| amount | `${amount > 0 ? '+' : '−'}${money(Math.abs(amount))}`, green / red |
| tag | if `pending`: `Badge label={'Pending · ' + formatDate(availableAt, true)} tone="warning"` |
| tap | `bookingId` → `BookingDetail { bookingId }`; `withdrawalId` → `WithdrawalDetail { withdrawalId }` |

### 4.3 `PayoutAccountsScreen` (route `PayoutAccounts`)

- `getPayoutAccounts()` on focus. Empty → `StateView kind="empty" title="No payout accounts"
  actionLabel="Add account" onAction={() => navigation.navigate('AddPayoutAccount')}`.
- Card per account:
  - icon `phone-portrait-outline` (UPI) / `business-outline` (bank), `displayName` bold;
  - UPI: `upiId`; bank: `accountHolderName`, `accountNumberMasked`, `ifscCode`;
  - `Badge label="Default" tone="brand"` when `isDefault`.
- Actions (long-press or a `…` button → `Alert.alert` sheet):
  - "Set as default" → `setDefaultPayoutAccount(id)` → reload.
  - "Remove" → confirm `Alert` → `deletePayoutAccount(id)` → reload. Note in the confirm text:
    "Withdrawals already requested to this account are not affected."
- Footer button "Add UPI ID / bank account" → `AddPayoutAccount`; hide it when 5 accounts exist.
- `pickForWithdraw: true`: tapping a card does `navigation.navigate('Withdraw', { payoutAccountId: id })`
  (native-stack `navigate` returns to the existing Withdraw screen with new params).

### 4.4 `AddPayoutAccountScreen` (route `AddPayoutAccount`)

- `Segmented` at the top: `UPI` | `Bank account` (initial: `route.params?.method ?? 'UPI'`).
- **UPI form:** `FormField label="UPI ID" placeholder="name@okaxis" autoCapitalize="none" keyboardType="email-address"`.
  Validate `UPI_RE.test(value.trim().toLowerCase())`.
- **Bank form:**
  - "Account holder name" (`HOLDER_RE`),
  - "Account number" (`keyboardType="number-pad"`, `secureTextEntry`, `ACCOUNT_RE` after removing spaces),
  - "Re-enter account number" (must match; **client-side only**, not sent),
  - "IFSC" (`autoCapitalize="characters"`, `IFSC_RE` on upper-case),
  - "Bank name" (optional).
- Checkbox / switch "Make this my default account" → `makeDefault`.
- Submit → `addPayoutAccount(input)` → `navigation.goBack()` (toast "Account added").
- Errors:
  - `INVALID_PAYOUT_ACCOUNT` → `payoutFieldError(err)` → show `message` under that field
    (`upiId`, `accountHolderName`, `accountNumber`, `ifscCode`);
  - `DUPLICATE_RESOURCE` → "This account is already saved.";
  - `PAYOUT_ACCOUNT_LIMIT` → `Alert` with the server message;
  - otherwise `apiErrorMessage(err)`.
- Disable the submit button while busy (avoid double submit).

### 4.5 `WithdrawScreen` (route `Withdraw`)

**Load:** `Promise.all([getWallet(), getPayoutAccounts()])`. If `!wallet.canWithdraw`, show the
`blockedMessage` in a `StateView kind="empty"` (with "Add account" action for `NO_PAYOUT_ACCOUNT`, or
"View withdrawal" → `WithdrawalDetail` for `WITHDRAWAL_IN_PROGRESS`).

**Form:**
1. Header: "Available ₹{availableBalance}".
2. Amount input (`keyboardType="decimal-pad"`, prefix ₹) + **Max** chip
   (sets `Math.min(availableBalance, maxWithdrawalAmount)`). Optional quick chips ₹500 / ₹1000 / ₹5000
   (only those ≤ available).
3. Client validation with `parseAmount()`:
   - invalid → "Enter a valid amount";
   - `< minWithdrawalAmount` → "Minimum is ₹{min}";
   - `> maxWithdrawalAmount` → "Maximum per request is ₹{max}";
   - `> availableBalance` → "You can withdraw up to ₹{available}".
4. "Send to" row: the selected account (`route.params?.payoutAccountId` → that account, else the default),
   showing `displayName`; "Change" → `PayoutAccounts { pickForWithdraw: true }`.
5. Note field (optional, max 255).
6. **Withdraw** button → confirm `Alert`: "Withdraw ₹{amount} to {displayName}?" → `requestWithdrawal(...)`.
   - Success → `navigation.replace('WithdrawalDetail', { withdrawalId: res.id })`.
   - Errors (`apiErrorCode(err)`):

| code | UI |
|---|---|
| `INSUFFICIENT_BALANCE` | show server message under amount; reload wallet (balance changed) |
| `WITHDRAWAL_BELOW_MINIMUM` / `WITHDRAWAL_ABOVE_MAXIMUM` | message under amount |
| `WITHDRAWAL_IN_PROGRESS` | Alert, then go back to `Wallet` |
| `VENDOR_NOT_APPROVED` | Alert with message |
| `NOT_FOUND` (account deleted) | "That account was removed. Pick another." + reload accounts |
| other | `apiErrorMessage(err)` |

Keep the button disabled while the request is in flight.

### 4.6 `WithdrawalsScreen` (route `Withdrawals`)

- Chips: All · Requested (`PENDING`) · Processing · Paid · Declined (`REJECTED`) · Cancelled.
- Paged `FlatList` (`getWithdrawals({ status, page })`), infinite scroll until `last`.
- Row: `money(amount)` bold, `Badge` (`WITHDRAWAL_STATUS[status]`), `payoutDisplayName`, `reference`,
  `formatDate(requestedAt, true)`; for `PAID` also "UTR {payoutReference}". Tap → `WithdrawalDetail`.
- Empty: "No withdrawals yet".

### 4.7 `WithdrawalDetailScreen` (route `WithdrawalDetail`)

- `getWithdrawal(route.params.withdrawalId)`; `404` → `StateView kind="empty" title="Withdrawal not found"`.
- Header: amount (large), `Badge`, `WITHDRAWAL_STATUS[status].hint`.
- `KeyValue` rows: Reference · Requested on · Sent to (`payoutDisplayName`) · UPI ID or
  Account holder / Account (`accountNumberMasked`) / IFSC / Bank · Note.
- Status-specific:
  - `PAID`: "Transfer reference (UTR)" = `payoutReference` with a copy button
    (`expo-clipboard` if you add it; otherwise selectable `Typography`), "Paid on" `paidAt`.
  - `REJECTED`: red box with `rejectionReason`, "Declined on" `rejectedAt`.
  - `CANCELLED`: "Cancelled on" `cancelledAt`.
- **Timeline** (reuse the look of `PaymentTimeline` if suitable): Requested → Processing → Paid, or
  Requested → Declined / Cancelled, using the timestamps (a step is done when its timestamp is set).
- **Cancel request** button only when `status === 'PENDING'` → confirm Alert →
  `cancelWithdrawal(id)` → replace state with the response. On `WITHDRAWAL_STATUS_CONFLICT`: Alert
  "An admin has already started processing this withdrawal." + reload.
- Pull to refresh (status changes when the admin acts; the push also brings the user here).

---

## 5. Notifications integration

**`src/api/notifications.ts`**

```ts
export type NotificationCategory =
  | 'REGISTRATION' | 'ACCOUNT' | 'APPROVAL' | 'REQUEST' | 'BOOKING' | 'ENROLLMENT'
  | 'UPLOAD' | 'PAYOUT' | 'OFFER' | 'ANNOUNCEMENT' | 'ACTIVITY' | 'ADMIN' | 'SYSTEM';

export const VENDOR_CATEGORIES: NotificationCategory[] = [
  'BOOKING', 'PAYOUT', 'APPROVAL', 'ACCOUNT', 'ANNOUNCEMENT', 'ACTIVITY', 'UPLOAD', 'REGISTRATION',
];
```

**`src/notifications/meta.ts`**

```ts
// CATEGORY_META
PAYOUT: { label: 'Payouts', icon: 'wallet', fg: c.successText, bg: c.successSoft },
// CHIP_LABEL
PAYOUT: 'Payouts',
```

**`src/notifications/routing.ts`** — in `navigateFor`'s `switch (data.screen)`:

```ts
    case 'vendor-withdrawal': {
      const withdrawalId = num(data.withdrawalId) ?? entityId;
      if (withdrawalId) return nav.navigate('WithdrawalDetail', { withdrawalId });
      break;
    }
```

Android channel `spacz_payout` is created automatically if you build channels from
`GET /api/notifications/categories`.

Notification types the vendor receives: `WITHDRAWAL_REQUESTED` (in-app), `WITHDRAWAL_PROCESSING`,
`WITHDRAWAL_PAID`, `WITHDRAWAL_REJECTED` (push), `WITHDRAWAL_CANCELLED` (in-app). Optional: when one
arrives in the foreground and the Wallet / WithdrawalDetail screen is open, reload it.

---

## 6. Testing the flow end to end

The backend needs the wallet build deployed (studyhall-service **and** notification-service, for the new
`PAYOUT` category).

1. Log in as an **approved** partner.
2. **Wallet** shows ₹0 and "Add a UPI ID or bank account". Add a UPI ID → reason changes to `BELOW_MINIMUM`.
3. Make an **online** booking at this partner's hall from the student app (Razorpay test mode).
   - The wallet shows it as **pending** (48 h).
   - To skip the hold on a test DB:
     `update vendor_wallet_entries set available_at = now() - interval '1 minute' where vendor_id = <id>;`
4. **Withdraw:**
   - amount < 100 → minimum error;
   - amount > available → insufficient error;
   - a valid amount → WithdrawalDetail shows **Requested**.
5. While it's pending:
   - **Withdraw** is disabled ("withdrawal in progress");
   - **Cancel** → amount back in the wallet.
6. Request again. Then, as an **admin** (admin panel, or curl with an admin token):
   ```
   POST /api/admin/withdrawals/{id}/processing   {}                                   → push "being processed"
   POST /api/admin/withdrawals/{id}/paid         {"payoutReference":"UTR123456789"}   → push "paid", UTR shown
   POST /api/admin/withdrawals/{id}/reject       {"reason":"UPI name mismatch"}       → push "declined", amount back
   ```
7. Tap each push or inbox item and check that it opens **WithdrawalDetail**.
8. Check the **Statement**: earning (+), withdrawal (−), and reversal (+) after a reject or cancel.

---

## 7. Error codes quick reference

| `error` | HTTP | Where | Message to show |
|---|---|---|---|
| `INVALID_PAYOUT_ACCOUNT` | 422 | add account | field message (via `payoutFieldError`) |
| `PAYOUT_ACCOUNT_LIMIT` | 422 | add account | server message |
| `DUPLICATE_RESOURCE` | 409 | add account | "This account is already saved." |
| `VENDOR_NOT_APPROVED` | 422 | withdraw | server message |
| `WITHDRAWAL_BELOW_MINIMUM` / `WITHDRAWAL_ABOVE_MAXIMUM` | 422 | withdraw | server message |
| `INSUFFICIENT_BALANCE` | 422 | withdraw | server message (contains the max), reload wallet |
| `WITHDRAWAL_IN_PROGRESS` | 409 | withdraw | "You already have a withdrawal in progress." |
| `WITHDRAWAL_STATUS_CONFLICT` | 409 | cancel | "Already being processed — can't cancel." |
| `NOT_FOUND` | 404 | detail / withdraw (account) | "Not found" state / pick another account |
| `VALIDATION_ERROR` | 400 | any form | `apiErrorMessage(err)` (lists fieldErrors) |
