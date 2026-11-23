You are a senior React Native (Expo) engineer and product designer working in the SPACZ Partner app at
`C:\Users\USER\Desktop\spacz\Spacz_Partner\Spacz_Partner` (Expo, TypeScript, React Navigation native-stack,
axios). Your job: build the **Transactions & History** screens, and bring the existing **Wallet & Withdrawals**
screens to the same design quality, so the "money" side of the app feels like one polished, distinctive
feature.

## Specs (read fully before writing code)
- `C:\Users\USER\Desktop\Spacz_backend\spacz-platform\docs\PARTNER_APP_TRANSACTIONS_HISTORY.md` is the main
  spec. It has the routes, JSON shapes, paste-ready `src/api/history.ts` and `src/utils/history.ts`, the
  navigation changes and the screen specs.
- `C:\Users\USER\Desktop\Spacz_backend\spacz-platform\docs\PARTNER_APP_WALLET_IMPLEMENTATION.md` covers the
  wallet screens, which already exist as `WalletScreen`, `WalletStatementScreen`, `WithdrawScreen`,
  `WithdrawalsScreen`, `WithdrawalDetailScreen`, `PayoutAccountsScreen` and `AddPayoutAccountScreen`.
- `C:\Users\USER\Desktop\Spacz_backend\spacz-platform\docs\PARTNER_APP_WALLET.md` is the wallet API
  reference.
Treat the docs as the contract for endpoints, fields, enums and error codes. Do not invent endpoints or
fields. If a doc and the backend seem to disagree, stop and tell me.

## Study the app first (match it, don't reinvent it)
Before designing, read these files:
- `src/ui/tokens.ts`: colors, spacing, radius, Montserrat typography. Notable colors: primary `#1A73E9`,
  home gradient `#4776E6 → #8E54E9`, ink, muted, surface, the success/warning/danger soft tints.
- `src/components/ui.tsx`: `Screen`, `Card`, `Badge`, `StatTile`, `StatGrid`, `SectionHeader`, `KeyValue`,
  `Segmented`, `StateView`, `FormField`, `Chip`, `shadow`.
- `src/components/Typography`, `src/components/Button`, `HallPicker`, `PaymentTimeline`, `RevenueChart`.
- `src/api/client.ts` (`apiErrorMessage`) and `src/utils/format.ts` (`money`, `formatDate`, `bookingTone`).
- `src/notifications/meta.ts` (`timeAgo`, `dayBucket`).
- Reference screens: `HomeScreen`, `PayAtHallPendingScreen`, `NotificationsScreen`, `BookingDetailScreen`,
  `ProfileScreen`.
Use the tokens and existing components everywhere. No hard-coded colors or fonts, and no new UI library or
dependency unless I approve it. Anything reusable goes in `src/components/`.

## What to build
1. Add `src/api/history.ts` and `src/utils/history.ts` from the spec. Add `transactions` to `BookingHistory` in
   `src/api/partner.ts`.
2. New screens:
   - `TransactionsScreen`: range chips, hall picker, Online/Pay at Hall switch, summary hero, list grouped
     by day, infinite scroll.
   - `TransactionDetailScreen`: receipt-style.
   - `BookingsHistoryScreen`: paged, status chips.
   - `PayAtHallHistoryScreen`: paged, status chips, "Collect" shortcut for open orders.
3. Extend `BookingDetailScreen` with the Money section: price breakdown, payment attempts (failed ones
   included), transactions, and a timeline with actor labels.
4. Register the routes in `navigation/types.ts` and `AppNavigator.tsx`. Add the Profile menu rows and the
   cross-links listed in the spec: Wallet → Transactions, Pay at Hall pending → History, booking →
   transaction.
5. Make the existing wallet screens consistent with the new ones (same hero, rows, chips, empty states). Keep
   their behaviour and API calls.

## Design direction (make it distinctive, still on-brand)
- **Money hero cards.** Use the home gradient (`homeHeaderGradStart → homeHeaderGradEnd`, already used in the
  app via `expo-linear-gradient`) for the Wallet balance and the Transactions "Net" summary. Show the main
  figure large (Montserrat Bold, `size.special1`) with a soft secondary line. Add a subtle split bar for
  Online vs Pay at Hall.
- **One shared money row component** (`MoneyRow`), used for wallet entries, transactions and withdrawals.
  It has a round tinted icon (soft tint background + strong foreground from tokens), a title, a muted
  subtitle, an amount on the right (green `+` / red `−`, tabular-looking), and an optional status `Badge`.
- **Day-grouped lists** with sticky-feeling section headers (Today / Yesterday / This week / date).
- **Receipt screens** (transaction and withdrawal detail): a card styled like a receipt with a dashed
  separator, reference with a copy action, a status pill, and key/value rows. Add a Share action via React
  Native `Share`.
- **Timelines**: a vertical stepper (reuse or extend `PaymentTimeline`). Done steps are brand blue, a
  failed or declined step is danger, pending steps are muted.
- **Filters**: horizontal scroll of pill `Chip`s plus `Segmented`. Show active filters clearly and offer a
  one-tap "Reset".
- **Every state is designed**: skeleton or `StateView` loading, friendly empty states with an icon and a
  next action, and inline error and retry. Use the copy from the spec (e.g. "Transactions = what students
  paid; Wallet = what SPACZ pays you").
- **Feel**: pull-to-refresh everywhere, `useFocusEffect` reload, press feedback on rows, and a small
  fade/slide-in for list items with `react-native-reanimated` (already installed). Keep it subtle and fast.
- **Accessibility and layout**: `accessibilityRole` and labels on tappable rows, contrast from tokens, no
  clipped text at small widths, safe areas respected. Make sure it works on Android, iOS and web
  (react-native-web).

## Engineering rules
- TypeScript strict-friendly. No `any` in new code except where the spec's API types require it.
- Infinite scroll: guard against double loads, stop at `last === true`, reset to page 0 when a filter
  changes, and cancel or ignore stale responses when filters change quickly.
- Use `apiErrorMessage` for errors and branch on the backend `error` codes listed in the spec
  (`INVALID_DATE_RANGE`, `NOT_FOUND`, …).
- Dates sent to the API use `ymdIST()` (yyyy-MM-dd, India time). Format amounts with `money()`.
- Keep changes focused. Don't refactor unrelated screens or change existing behaviour outside this
  feature.

## Process
1. Start with a short plan: files to add or change, the shared components you'll create, and a one-line
   description of each screen's layout. Then implement.
2. After implementing, run `npx tsc --noEmit` and fix every error. Start the app with
   `npx expo start --web` (or Android) and walk the checklist in §7 of the spec. Note anything you couldn't
   verify.
3. Finish with a summary: files changed, new components, screenshots or descriptions of each screen,
   anything still open, and anything that needs the backend (e.g. the studyhall-service redeploy for
   `GET /api/vendors/me/transactions/{id}`).
