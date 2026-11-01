# SPACZ Admin Console — Frontend Plan

The complete blueprint for the SPACZ **Admin Console**: tech stack, architecture, every route, every
screen, the exact backend call behind each one, TypeScript types that match the Java DTOs, the
design system, and a phased build order.

Everything below is derived from the current backend in `spacz-platform` (admin-service,
studyhall-service catalogs, auth-service, gateway rules). Where the UI needs something the backend
does not provide yet, it is called out in **§13 Backend gaps** rather than invented.

---

## Table of contents

1. [Goals & principles](#1-goals--principles)
2. [Tech stack](#2-tech-stack)
3. [Project structure](#3-project-structure)
4. [Route map (all routes)](#4-route-map-all-routes)
5. [Layout & navigation shell](#5-layout--navigation-shell)
6. [Authentication & session](#6-authentication--session)
7. [API layer](#7-api-layer)
8. [TypeScript domain types](#8-typescript-domain-types)
9. [Screen-by-screen specification](#9-screen-by-screen-specification)
10. [Status → actions matrix](#10-status--actions-matrix)
11. [Design system](#11-design-system)
12. [Cross-cutting UX patterns](#12-cross-cutting-ux-patterns)
13. [Backend gaps & recommended additions](#13-backend-gaps--recommended-additions)
14. [Testing, quality & deployment](#14-testing-quality--deployment)
15. [Phased roadmap](#15-phased-roadmap)

---

## 1. Goals & principles

The admin's job on SPACZ is: **approve** vendors and study halls, **moderate** (suspend/activate)
accounts, vendors and halls, **curate** the exam (program) and amenity catalogs, **oversee**
bookings, and **audit** everything.

Design principles:

| Principle | What it means in the UI |
|---|---|
| **Queue-first** | The two approval queues (vendors `PENDING`, halls `PENDING_APPROVAL`) are the most important thing. Badge counts in the sidebar, a "Review queue" on the dashboard, keyboard-driven approve/reject. |
| **Every action explains itself** | Reject/suspend always ask for a reason (backend requires it). Show the reason back everywhere (`statusReason`). Every action ends in the audit log, so link to it. |
| **URL is state** | Filters, pagination, sort, open tab and open drawer live in the query string. Every view is shareable and survives refresh. |
| **Degrade gracefully** | The dashboard and user detail can be partial (`unavailableServices`, `warnings`). Show what loaded, flag what did not. |
| **Fast & calm** | Skeletons instead of spinners, optimistic badges, cached lists, command palette (`Ctrl/⌘ K`), dark mode, generous whitespace. |

---

## 2. Tech stack
a
| Concern | Choice | Why |
|---|---|---|
| Build | **Vite 6 + React 19 + TypeScript (strict)** | Matches `VITE_API_BASE_URL` already used in `FRONTEND_INTEGRATION.md`; `http://localhost:5173` is already in the gateway's default CORS list. |
| Routing | **React Router v7** (data/library mode) with lazy routes | Nested layouts, route-level code splitting, loaders optional. |
| Server state | **TanStack Query v5** | Caching, background refetch, invalidation after mutations, pagination with `placeholderData: keepPreviousData`. |
| Client state | **Zustand** (tiny) | Session (access token in memory), UI prefs (sidebar collapsed, density). |
| UI kit | **Tailwind CSS v4 + shadcn/ui** (Radix primitives) | Accessible, fully themeable, modern look; components are copied into the repo so they are ours. |
| Icons | **lucide-react** | Also used to render amenity `icon` strings (`"wifi"` → `<Wifi/>`). |
| Tables | **TanStack Table v8** | Server-side pagination/sort, column visibility, row selection. |
| Forms | **react-hook-form + zod** | Zod schemas mirror the Java bean-validation rules (§8). |
| Charts | **Recharts** (via shadcn `chart` component) | Donut/bar/radial for the dashboard. |
| Toasts | **sonner** | Success/error toasts with correlation ID. |
| Command palette | **cmdk** (shadcn `command`) | Jump to any page / search users, vendors, halls. |
| Motion | **motion** (framer-motion) | Subtle page/drawer transitions, number count-up on KPI cards. |
| Dates | **date-fns** + `date-fns-tz` | Business time zone is `Asia/Kolkata`; API p    |timestamps are UTC ISO-8601. |

+| Maps (optional) | **react-leaflet** + OpenStreetMap tiles | Show hall `latitude/longitude` on the hall detail page. |
 r
| Lint/format | ESLint (typescript-eslint), Prettier, `eslint-plugin-react-hooks` | |
| Tests | **Vitest + Testing Library + MSW**, **Playwright** for E2E | MSW mocks the gateway with the exact DTO shapes. |

Fonts: **Inter** (UI) and **JetBrains Mono** (IDs, booking references, correlation IDs).

---

## 3. Project structure

A feature-sliced layout: each admin domain owns its API hooks, types, components and pages.

```
spacz-admin-web/
├── .env.example                     VITE_API_BASE_URL=http://localhost:8080
├── index.html
├── vite.config.ts                   path alias "@/"
├── tailwind / components.json       shadcn config
└── src/
    ├── main.tsx                     providers: QueryClient, Router, Theme, Toaster
    ├── app/
    │   ├── router.tsx               all routes (§4), lazy imports, guards
    │   ├── providers.tsx
    │   └── query-client.ts          defaults: staleTime 30s, retry rules (no retry on 4xx)
    ├── layouts/
    │   ├── AuthLayout.tsx           split-screen login
    │   └── AdminLayout.tsx          sidebar + topbar + <Outlet/>
    ├── lib/
    │   ├── api/
    │   │   ├── client.ts            fetch wrapper, bearer token, single-flight refresh, ApiError
    │   │   ├── errors.ts            ApiError type, error-code → message map
    │   │   ├── pagination.ts        PageResponse<T>, toSearchParams(), sort helpers
    │   │   └── query-keys.ts        central query key factory
    │   ├── auth/
    │   │   ├── session-store.ts     Zustand: accessToken (memory), user
    │   │   ├── RequireAdmin.tsx     route guard
    │   │   └── useAuth.ts
    │   ├── format.ts                currency (INR), dates (IST), phone masking, relative time
    │   ├── status.ts                status → label/colour/icon maps (§11.3)
    │   └── utils.ts                 cn(), downloadCsv()
    ├── components/
    │   ├── ui/                      shadcn primitives (button, dialog, sheet, table, badge, ...)
    │   ├── data-table/              DataTable, Toolbar, FacetedFilter, Pagination, ColumnToggle
    │   ├── StatusBadge.tsx
    │   ├── KpiCard.tsx
    │   ├── PageHeader.tsx           title, description, breadcrumbs, actions slot
    │   ├── ReasonDialog.tsx         reject / suspend / activate dialogs
    │   ├── ConfirmDialog.tsx
    │   ├── EmptyState.tsx
    │   ├── ErrorState.tsx           shows ApiError.message + correlationId (copy button)
    │   ├── PartialDataBanner.tsx    for unavailableServices / warnings
    │   ├── JsonViewer.tsx           collapsible raw view (audit, debugging)
    │   ├── Timeline.tsx             activity + audit history
    │   ├── SeatGrid.tsx             read-only block grid renderer
    │   └── CommandMenu.tsx          ⌘K palette
    ├── features/
    │   ├── auth/         api.ts  pages/LoginPage.tsx  components/OtpForm.tsx
    │   ├── dashboard/    api.ts  pages/DashboardPage.tsx  components/*
    │   ├── approvals/    pages/ApprovalsPage.tsx (unified queue)
    │   ├── users/        api.ts  types.ts  pages/UsersListPage.tsx  pages/UserDetailPage.tsx
    │   ├── vendors/      api.ts  types.ts  pages/VendorsListPage.tsx  pages/VendorDetailPage.tsx
    │   ├── studyhalls/   api.ts  types.ts  pages/StudyHallsListPage.tsx  pages/StudyHallDetailPage.tsx
    │   ├── bookings/     api.ts  types.ts  pages/BookingsPage.tsx  components/BookingDrawer.tsx
    │   ├── programs/     api.ts  types.ts  pages/ProgramsPage.tsx  components/ProgramFormSheet.tsx
    │   ├── amenities/    api.ts  types.ts  pages/AmenitiesPage.tsx  components/AmenityFormSheet.tsx
    │   ├── audit/        api.ts  types.ts  pages/AuditLogPage.tsx
    │   └── account/      api.ts  pages/ProfilePage.tsx  pages/SecurityPage.tsx
    ├── hooks/            useDebounce, useUrlState, useHotkeys, useMediaQuery
    └── styles/           globals.css (tokens, light/dark)
```

Rules:
- Pages never call `fetch` directly; they use the feature's hooks (`useVendors`, `useApproveVendor`…).
- One file per resource for API hooks (`features/vendors/api.ts`) with queries + mutations.
- Types are hand-written to match DTOs (§8) — or generated from `http://localhost:8080/v3/api-docs/admin-service`
  with `openapi-typescript` (recommended once the API is stable).

---

## 4. Route map (all routes)

All routes except `/login` sit under `AdminLayout` and the `RequireAdmin` guard.
List pages keep filters in the query string (shown in the "URL state" column).

| # | Route | Page | Backend calls | URL state |
|---|---|---|---|---|
| 1 | `/login` | Login (email+password / phone OTP) | `POST /api/auth/login` · `POST /api/auth/otp/request` · `POST /api/auth/otp/verify` | `?redirect=` |
| 2 | `/` | → redirect to `/dashboard` | — | |
| 3 | `/dashboard` | Overview: KPIs, charts, review queue, recent activity | `GET /api/admin/dashboard` · `GET /api/admin/vendors?status=PENDING&size=5` · `GET /api/admin/studyhalls?status=PENDING_APPROVAL&size=5` · `GET /api/admin/audit-logs?size=8` | |
| 4 | `/approvals` | Unified review queue (tabs) | as above, full pages | `?tab=vendors\|halls&page=` |
| 5 | `/users` | All accounts | `GET /api/admin/users` | `?search=&role=&status=&page=&size=&sort=` |
| 6 | `/users/:id` | Account detail (tabs) | `GET /api/admin/users/{id}` | `?tab=overview\|exams\|enrollments\|activity\|bookings\|audit` |
| 6a | `/users/:id?tab=activity` | Student activity | `GET /api/admin/users/{id}/activity` | `&page=` |
| 6b | `/users/:id?tab=bookings` | Student bookings | `GET /api/admin/bookings?userId={id}` | `&status=&page=` |
| 6c | `/users/:id?tab=audit` | History of this account | `GET /api/admin/audit-logs?entityType=USER_ACCOUNT&entityId={id}` (+ `actorUserId={id}`) | |
| 7 | `/vendors` | Vendors / institutes | `GET /api/admin/vendors` | `?search=&status=&city=&page=&size=&sort=` |
| 8 | `/vendors/:vendorId` | Vendor detail (tabs) | `GET /api/admin/vendors/{vendorId}` | `?tab=overview\|halls\|bookings\|audit` |
| 8a | `/vendors/:vendorId?tab=halls` | Vendor's halls | `GET /api/admin/studyhalls?vendorId={vendorId}` | `&status=` |
| 8b | `/vendors/:vendorId?tab=audit` | Vendor history | `GET /api/admin/audit-logs?entityType=VENDOR&entityId={vendorId}` | |
| 9 | `/studyhalls` | Study halls (table / card grid toggle) | `GET /api/admin/studyhalls` | `?search=&status=&city=&vendorId=&view=table\|grid&page=` |
| 10 | `/studyhalls/:id` | Hall detail (tabs) | `GET /api/admin/studyhalls/{id}` | `?tab=overview\|seating\|bookings\|audit` |
| 10a | `/studyhalls/:id?tab=seating` | Blocks + seat map | `blocks[]` from detail; live halls: `GET /api/studyhalls/{id}/seats?startDate=&endDate=` | `&date=` |
| 10b | `/studyhalls/:id?tab=bookings` | Hall bookings | `GET /api/admin/bookings?studyHallId={id}` | `&status=&from=&to=` |
| 10c | `/studyhalls/:id?tab=audit` | Hall history | `GET /api/admin/audit-logs?entityType=STUDY_HALL&entityId={id}` | |
| 11 | `/bookings` | All bookings | `GET /api/admin/bookings` | `?studyHallId=&userId=&status=&from=&to=&page=&sort=` |
| 11a | `/bookings?open=:bookingId` | Booking drawer | row data (+ `GET /api/bookings/{id}` if opened by link) | |
| 12 | `/catalog/programs` | Exams / courses catalog | `GET /api/programs/all` · `POST /api/programs` · `PUT /api/programs/{id}` · `DELETE /api/programs/{id}` | `?search=&category=&active=&page=` |
| 13 | `/catalog/amenities` | Amenity catalog | `GET /api/amenities/all` · `POST /api/amenities` · `PUT /api/amenities/{id}` | `?search=&active=` (client-side) |
| 14 | `/audit-logs` | Audit log explorer | `GET /api/admin/audit-logs` | `?search=&action=&actorRole=&actorUserId=&entityType=&entityId=&from=&to=&page=` |
| 15 | `/settings/profile` | My admin profile | `GET /api/admin/me` · `PUT /api/admin/me` · `GET /api/auth/me` | |
| 16 | `/settings/security` | Password | `PUT /api/auth/me/password` | |
| 17 | `/settings/appearance` | Theme, density, sidebar | local only | |
| 18 | `/403` | Forbidden (logged in, not ADMIN) | — | |
| 19 | `*` | 404 | — | |

### Router sketch

```tsx
// src/app/router.tsx
export const router = createBrowserRouter([
  { path: '/login', element: <AuthLayout />, children: [{ index: true, lazy: () => import('@/features/auth/pages/LoginPage') }] },
  {
    element: <RequireAdmin><AdminLayout /></RequireAdmin>,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard',          lazy: () => import('@/features/dashboard/pages/DashboardPage') },
      { path: 'approvals',          lazy: () => import('@/features/approvals/pages/ApprovalsPage') },
      { path: 'users',              lazy: () => import('@/features/users/pages/UsersListPage') },
      { path: 'users/:id',          lazy: () => import('@/features/users/pages/UserDetailPage') },
      { path: 'vendors',            lazy: () => import('@/features/vendors/pages/VendorsListPage') },
      { path: 'vendors/:vendorId',  lazy: () => import('@/features/vendors/pages/VendorDetailPage') },
      { path: 'studyhalls',         lazy: () => import('@/features/studyhalls/pages/StudyHallsListPage') },
      { path: 'studyhalls/:id',     lazy: () => import('@/features/studyhalls/pages/StudyHallDetailPage') },
      { path: 'bookings',           lazy: () => import('@/features/bookings/pages/BookingsPage') },
      { path: 'catalog/programs',   lazy: () => import('@/features/programs/pages/ProgramsPage') },
      { path: 'catalog/amenities',  lazy: () => import('@/features/amenities/pages/AmenitiesPage') },
      { path: 'audit-logs',         lazy: () => import('@/features/audit/pages/AuditLogPage') },
      { path: 'settings', element: <SettingsLayout />, children: [
        { index: true, element: <Navigate to="profile" replace /> },
        { path: 'profile',    lazy: () => import('@/features/account/pages/ProfilePage') },
        { path: 'security',   lazy: () => import('@/features/account/pages/SecurityPage') },
        { path: 'appearance', lazy: () => import('@/features/account/pages/AppearancePage') },
      ]},
      { path: '403', element: <Forbidden /> },
      { path: '*',   element: <NotFound /> },
    ],
  },
]);
```

---

## 5. Layout & navigation shell

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ▣ SPACZ Admin   │  Dashboard › Vendors › Sunrise Academy     ⌘K Search   ☾  ◉ │  ← Topbar (64px)
├─────────────────┼────────────────────────────────────────────────────────────┤
│ OVERVIEW        │                                                            │
│  ▦ Dashboard    │   Page header: title · description · primary actions       │
│  ✓ Approvals ⑦  │  ────────────────────────────────────────────────────────  │
│ MANAGE          │                                                            │
│  👤 Users        │   Content (max-width 1440, 24–32px padding)                │
│  🏢 Vendors   ③  │                                                            │
│  🏛 Study halls ④│                                                            │
│  🎫 Bookings     │                                                            │
│ CATALOG         │                                                            │
│  🎓 Exams        │                                                            │
│  ✦ Amenities    │                                                            │
│ SYSTEM          │                                                            │
│  🛡 Audit log    │                                                            │
│  ⚙ Settings     │                                                            │
│                 │                                                            │
│ ⚠ 1 service down│  ← health pill (from dashboard.unavailableServices)        │
│ [◉ Priya S.  ▾] │                                                            │
└─────────────────┴────────────────────────────────────────────────────────────┘
```

| Element | Behaviour |
|---|---|
| **Sidebar** | 260px, collapsible to a 72px icon rail (state persisted). Groups: Overview, Manage, Catalog, System. Badges: Approvals = pending vendors + pending halls; Vendors = `pendingVendors`; Study halls = `pendingApproval` (from `GET /api/admin/dashboard`, refetched every 60s and after every approve/reject). |
| **Topbar** | Breadcrumbs (from route `handle.crumb`), global search button (opens ⌘K), theme toggle, user menu (Profile, Security, Log out). |
| **Mobile (< 1024px)** | Sidebar becomes a `Sheet` drawer from a hamburger; tables collapse to card lists; detail tabs become a horizontal scroll strip. |
| **Command palette (⌘K)** | Static: "Go to …" for every route, "Review pending vendors", "New program", "Toggle theme". Dynamic (debounced 250ms, top 5 each): users (`/api/admin/users?search=`), vendors (`/api/admin/vendors?search=`), halls (`/api/admin/studyhalls?search=`). Also accepts `#123` to jump to user/vendor/hall by ID. |
| **Keyboard** | `g d` dashboard, `g a` approvals, `g u` users, `g v` vendors, `g h` halls, `g b` bookings, `/` focus table search, `?` shortcuts help. In review screens: `a` approve, `r` reject, `j/k` next/previous. |

---

## 6. Authentication & session

### Login page (`/login`)

Split-screen: left 55% brand panel (gradient mesh, product illustration, tagline "Run every study hall
on SPACZ from one place"), right 45% the form card.

Two tabs:

1. **Email** — `POST /api/auth/login {email, password}`. (`email` may also be a phone number.)
2. **Phone OTP** — `POST /api/auth/otp/request {phone}` → 6-box OTP input with a resend countdown from
   `resendAfterSeconds` → `POST /api/auth/otp/verify {phone, code}`.
   Never send `role` here: admins are never created by OTP sign-up. A `422 REGISTRATION_REQUIRED`
   means "this number has no account" — show it as an error, do not offer registration.

After success the response is `{accessToken, refreshToken, tokenType, expiresIn, user}`:

```ts
if (res.user.role !== 'ADMIN') {
  await api('/api/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: res.refreshToken }) });
  throw new Error('This console is for SPACZ administrators only.');
}
setSession(res);                       // access token in memory, refresh token in storage
navigate(searchParams.get('redirect') ?? '/dashboard', { replace: true });
```

Error mapping on the login form:

| Code | Message shown |
|---|---|
| `INVALID_CREDENTIALS` (401) | "Incorrect email or password" / "Wrong or expired code" |
| `ACCOUNT_LOCKED` (403) | "Too many attempts — account temporarily locked" |
| `ACCOUNT_SUSPENDED` / `ACCOUNT_DISABLED` (403) | the server `message` |
| `OTP_TOO_SOON` (429) | "Wait N s before requesting a new code" |
| `OTP_RATE_LIMITED` (429) | "Too many codes requested. Try again later" |
| `INVALID_PHONE` (400) | field error on phone |

### Token handling

| Item | Storage | Notes |
|---|---|---|
| Access token (15 min, `expiresIn: 900`) | **memory only** (Zustand) | never in localStorage |
| Refresh token (7 days, single-use, rotated) | `localStorage['spacz.admin.refresh']` | upgrade path: httpOnly cookie (needs a backend change) |
| User (`id, email, phone, role, status, passwordSet`) | memory | re-fetched with `GET /api/auth/me` on boot |

**Boot sequence** (in `RequireAdmin`): no access token but a refresh token → `POST /api/auth/refresh`
→ `GET /api/auth/me` → role check → render. Show a full-screen branded splash while this runs.

**Single-flight refresh** (critical — the backend detects refresh-token reuse and revokes every
session): one shared promise for all parallel 401s. Then retry the original request once. If refresh
fails → clear the session → `/login?redirect=<current path>`.

**Proactive refresh**: schedule a refresh at `expiresIn - 60s` so long review sessions never see a 401.

**Logout**: `POST /api/auth/logout {refreshToken}` → clear the session → `queryClient.clear()` →
`/login`. Sync logout across tabs with a `storage` event or `BroadcastChannel('spacz-admin')`.

**Suspension edge case**: an access token stays valid for up to 15 minutes after an account is
suspended. On any `403 ACCOUNT_SUSPENDED` from refresh, log out immediately.

---

## 7. API layer

### 7.1 Client

```ts
// src/lib/api/client.ts
export interface ApiError {
  timestamp: string; status: number; error: string; message: string; path: string;
  correlationId?: string; fieldErrors?: { field: string; message: string }[];
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const token = useSession.getState().accessToken;
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    if (await refreshOnce()) return api<T>(path, init, false);
    redirectToLogin();
  }
  if (res.status === 204) return undefined as T;
  const body = await res.json().catch(() => null);
  if (!res.ok) throw (body ?? { status: res.status, error: 'NETWORK', message: res.statusText }) as ApiError;
  return body as T;
}
```

Helper: `qs(params)` drops `undefined`/empty values, and `sort` is passed as `field,dir`.

### 7.2 Query keys

```ts
export const qk = {
  dashboard: ['dashboard'] as const,
  me: ['admin-me'] as const,
  users:   { list: (p: object) => ['users', 'list', p] as const, detail: (id: number) => ['users', id] as const,
             activity: (id: number, p: object) => ['users', id, 'activity', p] as const },
  vendors: { list: (p: object) => ['vendors', 'list', p] as const, detail: (id: number) => ['vendors', id] as const },
  halls:   { list: (p: object) => ['halls', 'list', p] as const, detail: (id: number) => ['halls', id] as const,
             seats: (id: number, d: string) => ['halls', id, 'seats', d] as const },
  bookings:  (p: object) => ['bookings', p] as const,
  programs:  (p: object) => ['programs', p] as const,
  amenities: ['amenities'] as const,
  audit:     (p: object) => ['audit', p] as const,
};
```

### 7.3 Mutation → invalidation table

| Mutation | Invalidate |
|---|---|
| approve/reject/suspend/activate **vendor** | `vendors.*`, `halls.*` (approval can push halls `APPROVED → ACTIVE`; suspension hides them), `dashboard`, `audit` |
| approve/reject/suspend/activate **hall** | `halls.*`, `vendors.detail(vendorId)` (hall counts), `dashboard`, `audit` |
| suspend/activate **user** | `users.*`, `dashboard`, `audit` |
| create/update/deactivate **program** | `programs`, `dashboard` (`activePrograms`), `audit` |
| create/update **amenity** | `amenities`, `audit` |
| update **admin profile** | `me` |

Use `setQueryData` with the returned DTO for the detail page (every moderation endpoint returns the
updated object), then invalidate the lists.

### 7.4 Complete endpoint inventory used by the admin console

All through the gateway (`VITE_API_BASE_URL`, default `http://localhost:8080`). All lists accept
`page` (0-based), `size` (max 100), `sort=field,asc|desc`, and return `PageResponse<T>`.

**Auth (auth-service)**

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/auth/login` | `{email, password}` | `AuthResponse` |
| POST | `/api/auth/otp/request` | `{phone}` | `202 {phone, expiresInSeconds, resendAfterSeconds, devCode?}` |
| POST | `/api/auth/otp/verify` | `{phone, code}` | `AuthResponse` |
| POST | `/api/auth/refresh` | `{refreshToken}` | `AuthResponse` |
| POST | `/api/auth/logout` | `{refreshToken}` | 204 |
| GET | `/api/auth/me` | — | `AccountDto` (+ `passwordSet`) |
| PUT | `/api/auth/me/password` | `{currentPassword?, newPassword}` | 204/200 |

**Admin (admin-service, all `ADMIN`)**

| Method | Path | Query / body | Returns |
|---|---|---|---|
| GET | `/api/admin/dashboard` | — | `DashboardResponse` |
| GET | `/api/admin/me` | — | `AdminProfile` (auto-created on first call) |
| PUT | `/api/admin/me` | `{fullName*, email?, phone?, title?}` | `AdminProfile` |
| GET | `/api/admin/users` | `search` (email/phone contains), `role` ADMIN\|USER\|VENDOR, `status` ACTIVE\|SUSPENDED\|DISABLED; default sort `createdAt,desc` | `Page<Account>` |
| GET | `/api/admin/users/{id}` | — | `AdminUserDetail` |
| GET | `/api/admin/users/{id}/activity` | page params | `Page<Activity>` |
| POST | `/api/admin/users/{id}/suspend` | `{reason*}` | `Account` |
| POST | `/api/admin/users/{id}/activate` | `{reason?}` | `Account` |
| GET | `/api/admin/vendors` | `search` (business/contact name, email, phone), `status`, `city`; default `createdAt,desc` | `Page<AdminVendor>` |
| GET | `/api/admin/vendors/{vendorId}` | — | `{vendor: AdminVendor, account: Account}` |
| POST | `/api/admin/vendors/{vendorId}/approve` | `{reason?}` | `AdminVendor` |
| POST | `/api/admin/vendors/{vendorId}/reject` | `{reason*}` | `AdminVendor` |
| POST | `/api/admin/vendors/{vendorId}/suspend` | `{reason*}` | `AdminVendor` |
| POST | `/api/admin/vendors/{vendorId}/activate` | `{reason?}` | `AdminVendor` |
| GET | `/api/admin/studyhalls` | `search`, `status`, `city`, `vendorId` | `Page<StudyHallSummary>` |
| GET | `/api/admin/studyhalls/{id}` | — | `StudyHallDetail` (any status) |
| POST | `/api/admin/studyhalls/{id}/approve` | `{reason?}` | `StudyHallDetail` |
| POST | `/api/admin/studyhalls/{id}/reject` | `{reason*}` | `StudyHallDetail` |
| POST | `/api/admin/studyhalls/{id}/suspend` | `{reason*}` | `StudyHallDetail` |
| POST | `/api/admin/studyhalls/{id}/activate` | `{reason?}` | `StudyHallDetail` |
| GET | `/api/admin/bookings` | `studyHallId`, `userId`, `status`, `from`, `to` (dates, overlapping range) | `Page<Booking>` |
| GET | `/api/admin/audit-logs` | `search`, `action`, `actorUserId`, `actorRole`, `entityType`, `entityId`, `from`, `to` (ISO instants; `to` exclusive) | `Page<AuditLog>` |

> `vendorId` everywhere is the vendor's **auth account ID** — so `/vendors/:vendorId` and
> `/users/:id` refer to the same person for a vendor. Link the two pages to each other.

**Catalogs (studyhall-service, writes `ADMIN`)**

| Method | Path | Query / body | Returns |
|---|---|---|---|
| GET | `/api/programs/all` | `search`, `category`, `active`; default `size=50, sort=name` | `Page<Program>` |
| POST | `/api/programs` | `{code*, name*, description?, category*, active?}` | 201 `Program` |
| PUT | `/api/programs/{id}` | same | `Program` |
| DELETE | `/api/programs/{id}` | — | 204 (**deactivates**, kept for history) |
| GET | `/api/amenities/all` | — | `Amenity[]` (not paginated) |
| POST | `/api/amenities` | `{code*, name*, icon?, description?, active?}` | 201 `Amenity` |
| PUT | `/api/amenities/{id}` | same (`active:false` retires it) | `Amenity` |

**Public reads reused by the admin UI**

| Method | Path | Use |
|---|---|---|
| GET | `/api/studyhalls/{id}/seats?startDate=&endDate=` | Seat map with availability — **only for live halls** (see §13) |
| GET | `/api/programs`, `/api/amenities` | Active-only lists for filter dropdowns |
| GET | `/api/bookings/{id}` | Booking deep link (ADMIN allowed by studyhall-service) |

---

## 8. TypeScript domain types

These mirror the Java records exactly (field names and nullability).

```ts
// ---------- common ----------
export interface PageResponse<T> {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean;
}
export type Role = 'ADMIN' | 'USER' | 'VENDOR';
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'DISABLED';
export type VendorStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'ACTIVE' | 'INACTIVE';
export type StudyHallStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'ACTIVE' | 'INACTIVE';
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'EXPIRED';
export type BookingPlan = 'DAILY' | 'MONTHLY';
export type EnrollmentStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type EnrollmentSource = 'BOOKING' | 'WALK_IN';
export type SeatStatus = 'AVAILABLE' | 'RESERVED' | 'MAINTENANCE' | 'INACTIVE';
export type SeatType = 'STANDARD' | 'PREMIUM' | 'CABIN' | 'WINDOW' | 'ACCESSIBLE';
export type PreparationStatus = 'PLANNED' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'DROPPED';
export type ActivityType = 'PROFILE_CREATED' | 'PROFILE_UPDATED' | 'PROGRAM_ADDED' | 'PROGRAM_UPDATED' | 'PROGRAM_REMOVED'
  | 'BOOKING_CREATED' | 'BOOKING_CONFIRMED' | 'BOOKING_CANCELLED' | 'BOOKING_COMPLETED' | 'BOOKING_EXPIRED';
export type AuditAction =
  | 'USER_SUSPENDED' | 'USER_ACTIVATED'
  | 'VENDOR_APPROVED' | 'VENDOR_REJECTED' | 'VENDOR_SUSPENDED' | 'VENDOR_ACTIVATED'
  | 'STUDY_HALL_APPROVED' | 'STUDY_HALL_REJECTED' | 'STUDY_HALL_SUSPENDED' | 'STUDY_HALL_ACTIVATED'
  | 'PROGRAM_CREATED' | 'PROGRAM_UPDATED' | 'PROGRAM_DEACTIVATED' | 'AMENITY_CREATED' | 'AMENITY_UPDATED'
  | 'USER_REGISTERED' | 'VENDOR_REGISTERED' | 'VENDOR_SUBMITTED'
  | 'STUDY_HALL_CREATED' | 'STUDY_HALL_UPDATED' | 'STUDY_HALL_SUBMITTED' | 'STUDY_HALL_DELETED';
export type AuditEntityType = 'VENDOR' | 'STUDY_HALL' | 'USER_ACCOUNT' | 'PROGRAM' | 'AMENITY';
export type ActorRole = 'ADMIN' | 'VENDOR' | 'USER' | 'SYSTEM';

// ---------- auth ----------
export interface Account {
  id: number; email: string | null; phone: string | null; role: Role; status: AccountStatus;
  passwordSet: boolean; lastLoginAt: string | null; createdAt: string;
}
export interface AuthResponse { accessToken: string; refreshToken: string; tokenType: 'Bearer'; expiresIn: number; user: Account; }

// ---------- admin ----------
export interface AdminProfile {
  id: number; adminUserId: number; fullName: string | null; email: string | null; phone: string | null;
  title: string | null; lastSeenAt: string | null; createdAt: string; updatedAt: string;
}
export interface DashboardResponse {
  users: { totalUsers: number; activeUsers: number; suspendedUsers: number; totalVendorAccounts: number; totalAdmins: number } | null;
  vendors: { totalVendors: number; draftVendors: number; pendingVendors: number; approvedVendors: number; suspendedVendors: number; rejectedVendors: number } | null;
  studyHalls: { totalStudyHalls: number; activeStudyHalls: number; pendingApproval: number; suspendedStudyHalls: number; totalSeats: number; activePrograms: number } | null;
  bookings: { totalBookings: number; todaysBookings: number; occupiedSeatsToday: number; pendingBookings: number; confirmedBookings: number; activeEnrollments: number } | null;
  unavailableServices: string[];
  generatedAt: string;
}

// ---------- user-service ----------
export interface UserProfile {
  id: number; userId: number; email: string | null; firstName: string; lastName: string | null; phone: string | null;
  profileImageUrl: string | null; dateOfBirth: string | null; city: string | null; state: string | null;
  educationLevel: string | null; preferredCity: string | null; createdAt: string; updatedAt: string;
}
export interface UserProgram {
  id: number; userId: number; programId: number; programCode: string; programName: string;
  startDate: string | null; targetDate: string | null; status: PreparationStatus; notes: string | null; createdAt: string; updatedAt: string;
}
export interface Activity { id: number; type: ActivityType; description: string; referenceType: string | null; referenceId: number | null; createdAt: string; }

// ---------- studyhall-service ----------
export interface VendorProfile {
  id: number; vendorId: number; businessName: string | null; contactName: string | null; email: string | null; phone: string | null;
  addressLine: string | null; city: string | null; state: string | null; pincode: string | null; gstNumber: string | null;
  description: string | null; logoUrl: string | null; status: VendorStatus; statusReason: string | null;
  missingForSubmission: string[]; submittedAt: string | null; approvedAt: string | null; createdAt: string; updatedAt: string;
}
export interface AdminVendor { profile: VendorProfile; studyHallCount: number; activeStudyHallCount: number; }
export interface AdminVendorDetail { vendor: AdminVendor; account?: Account; }

export interface AdminUserDetail {
  account: Account;
  profile?: UserProfile;          // students
  programs?: UserProgram[];       // students
  enrollments?: Enrollment[];     // students
  vendor?: AdminVendor;           // vendors
  warnings?: string[];            // parts that failed to load
}

export interface StudyHallSummary {
  id: number; vendorId: number; vendorBusinessName: string; name: string; addressLine: string; city: string; state: string;
  pricePerDay: number; pricePerMonth: number | null; status: StudyHallStatus; coverImageUrl: string | null; seatCount: number;
}
export interface Image { id: number; url: string; caption: string | null; displayOrder: number; cover: boolean; }
export interface Amenity { id: number; code: string; name: string; icon: string | null; description: string | null; active: boolean; }
export interface ProgramRef { programId: number; code: string; name: string; }
export interface OperatingHours { dayOfWeek: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'; openTime: string | null; closeTime: string | null; closed: boolean; }
export interface BlockSummary { id: number; name: string; totalRows: number; totalColumns: number; seatCount: number; dailyPrice: number; monthlyPrice: number | null; amenities: string[]; }
export interface StudyHallDetail {
  id: number; vendorId: number; vendorBusinessName: string; name: string; description: string | null;
  addressLine: string; city: string; state: string; pincode: string; latitude: number | null; longitude: number | null;
  contactPhone: string | null; contactEmail: string | null; pricePerDay: number; pricePerMonth: number | null; rules: string | null;
  status: StudyHallStatus; statusReason: string | null;
  images: Image[]; amenities: Amenity[]; programs: ProgramRef[]; operatingHours: OperatingHours[]; blocks: BlockSummary[];
  totalSeats: number; submittedAt: string | null; approvedAt: string | null; createdAt: string; updatedAt: string;
}
export interface Seat {
  id: number; blockId: number; seatNumber: string; row: number; column: number; seatType: SeatType; status: SeatStatus;
  pricePerDay: number; pricePerMonth: number | null; available?: boolean;
}
export interface Block {
  id: number; name: string; totalRows: number; totalColumns: number; displayOrder: number;
  dailyPrice: number | null; monthlyPrice: number | null; amenities: Amenity[]; seats: Seat[];
}
export interface SeatMap { studyHallId: number; startDate: string; endDate: string; blocks: Block[]; }

export interface Booking {
  id: number; bookingReference: string; userId: number; studyHallId: number; studyHallName: string; studyHallCity: string;
  seatId: number; seatNumber: string; programId: number | null; programName: string | null; plan: BookingPlan;
  startDate: string; endDate: string; units: number; unitPrice: number; totalPrice: number; status: BookingStatus;
  holdExpiresAt: string | null; confirmedAt: string | null; cancelledAt: string | null; cancellationReason: string | null; createdAt: string;
}
export interface Enrollment {
  id: number; studyHallId: number; studyHallName: string;
  student: { userId: number | null; name: string; phone: string | null; email: string | null; account: boolean };
  program: ProgramRef | null; seatId: number | null; seatNumber: string | null; plan: BookingPlan | null;
  startDate: string; endDate: string | null; status: EnrollmentStatus; source: EnrollmentSource; notes: string | null;
  createdAt: string; updatedAt: string;
}
export interface Program { id: number; code: string; name: string; description: string | null; category: string; active: boolean; }

// ---------- audit ----------
export interface AuditLog {
  id: number; actorUserId: number | null; actorEmail: string | null; actorRole: ActorRole; action: AuditAction;
  entityType: AuditEntityType; entityId: number | null; description: string | null; ipAddress: string | null;
  userAgent: string | null; correlationId: string | null; sourceService: string | null; timestamp: string;
}
```

### Zod schemas (mirror backend validation)

```ts
export const reasonSchema = z.object({ reason: z.string().trim().min(1, 'A reason is required').max(500) });
export const optionalReasonSchema = z.object({ reason: z.string().trim().max(500).optional() });

export const programSchema = z.object({
  code: z.string().trim().min(1).max(40).regex(/^[A-Za-z0-9_-]+$/, "Letters, digits, '_' and '-' only"),
  name: z.string().trim().min(1).max(120),
  description: z.string().max(1000).optional(),
  category: z.string().trim().min(1).max(60),
  active: z.boolean().default(true),
});

export const amenitySchema = z.object({
  code: z.string().trim().min(1).max(40).regex(/^[A-Za-z0-9_-]+$/),
  name: z.string().trim().min(1).max(80),
  icon: z.string().max(60).optional(),
  description: z.string().max(300).optional(),
  active: z.boolean().default(true),
});

export const adminProfileSchema = z.object({
  fullName: z.string().trim().min(1).max(120),
  email: z.string().email().max(254).optional().or(z.literal('')),
  phone: z.string().regex(/^\+?[0-9][0-9 -]{6,18}$/, 'Enter a valid phone number').optional().or(z.literal('')),
  title: z.string().max(80).optional(),
});

export const passwordSchema = z.object({
  currentPassword: z.string().optional(),
  newPassword: z.string().min(8).max(72).regex(/[A-Za-z]/).regex(/\d/, 'Must contain a letter and a digit'),
  confirm: z.string(),
}).refine(v => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });
```

Always also map server `fieldErrors[]` onto the form with `setError(field, {message})`.

---

## 9. Screen-by-screen specification

### 9.1 Dashboard — `/dashboard`

Source: `GET /api/admin/dashboard` (refetch every 60s and on window focus; show "Updated 12s ago"
from `generatedAt`).

```
┌ Good morning, Priya ☀   Here's what's happening on SPACZ today.         [⟳] [Review queue →] ┐
│ ⚠ PartialDataBanner: "Booking stats unavailable — studyhall-service is not responding" (if any) │
├──────────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────────────┤
│ Students     │ Vendors      │ Live halls   │ Seats        │ Bookings     │ Occupied     │
│ 12,480       │ 342          │ 128 / 156    │ 9,240        │ today 214    │ today 3,108  │
│ 12,301 active│ ⑦ pending    │ ④ pending    │ total        │ 48 pending   │ 33.6% of seats│
└──────────────┴──────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
┌ Needs your attention ──────────────────────────┐ ┌ Vendor pipeline (donut) ─────────────┐
│ VENDORS  Sunrise Academy · Hyderabad · 2h ago  │ │ draft · pending · approved ·         │
│          [View] [✓ Approve] [✕ Reject]         │ │ rejected · suspended                 │
│ HALLS    Focus Point Library · Ameerpet · 5h   │ ├ Study hall status (bar) ─────────────┤
│          [View] [✓ Approve] [✕ Reject]         │ │ active · pending · suspended · other │
│ View all 11 →                                  │ ├ Today's occupancy (radial) ──────────┤
└────────────────────────────────────────────────┘ │ occupiedSeatsToday / totalSeats      │
┌ Recent platform activity (audit) ──────────────┐ ├ Bookings funnel ─────────────────────┤
│ ● VENDOR_SUBMITTED  Sunrise Academy   2h       │ │ pending → confirmed; enrollments     │
│ ● STUDY_HALL_APPROVED by priya@…      3h       │ └──────────────────────────────────────┘
│ View audit log →                               │
└────────────────────────────────────────────────┘
```

KPI formulas (all from the single dashboard response):

| Card | Value | Sub-line |
|---|---|---|
| Students | `users.totalUsers` | `activeUsers` active · `suspendedUsers` suspended |
| Vendors | `vendors.totalVendors` | `pendingVendors` pending (amber chip, links to `/approvals?tab=vendors`) |
| Live halls | `studyHalls.activeStudyHalls` / `totalStudyHalls` | `pendingApproval` pending |
| Seats | `studyHalls.totalSeats` | `activePrograms` exams in catalog |
| Bookings today | `bookings.todaysBookings` | `pendingBookings` pending · `totalBookings` all-time |
| Occupancy today | `bookings.occupiedSeatsToday` | `% = occupiedSeatsToday / totalSeats` · `activeEnrollments` enrolled students |

Charts:
- **Vendor pipeline** donut: draft / pending / approved / rejected / suspended.
- **Study hall status** horizontal bar: active / pending / suspended / other (`total - the rest`).
- **Occupancy** radial gauge.
- **Bookings** stacked bar: pending vs confirmed; side stat: active enrollments.

> Trend lines ("+12% this week") need time-series data the backend does not return yet — see §13.
> Don't fake them; ship without trends first.

Null groups (service down) render the KPI cards in a muted "Unavailable" state with a tooltip.

### 9.2 Approvals — `/approvals`

The admin's main workspace. Two tabs with counts: **Vendors (7)**, **Study halls (4)**.

Layout: **master–detail split view** (list 380px on the left, full review panel on the right) on
desktop; on mobile the list opens the detail as a full page.

**Vendors tab** — `GET /api/admin/vendors?status=PENDING&sort=createdAt,asc` (oldest first = FIFO; switch to
`submittedAt,asc` if the vendor sort whitelist allows it). Halls can already sort by `submittedAt,asc`.
List item: logo/initials avatar, business name, contact name, city, "submitted 2 days ago", and a red
"SLA" dot after 48h.
Review panel (`GET /api/admin/vendors/{vendorId}`):
- Header: logo, business name, `StatusBadge`, submitted time.
- **Checklist card**: business name, contact, phone, email, address/city/state/pincode, GST number,
  description, logo — each with ✓/✕ (helps the admin decide quickly). `missingForSubmission[]`
  should be empty for PENDING vendors; show it if not.
- **Login account card** (`account`): email, phone, account status, last login, created at.
- **Halls card**: `studyHallCount` / `activeStudyHallCount` + link to `/studyhalls?vendorId=`.
- **History**: `GET /api/admin/audit-logs?entityType=VENDOR&entityId={vendorId}&size=10` as a timeline
  (registered → submitted → rejected → resubmitted …).
- Sticky action bar: **Approve** (optional note) · **Reject** (reason required) · keyboard `a` / `r`.
  After an action, auto-advance to the next item and show an undo-less toast ("Sunrise Academy approved").

**Study halls tab** — `GET /api/admin/studyhalls?status=PENDING_APPROVAL`.
Review panel (`GET /api/admin/studyhalls/{id}`):
- Image gallery (cover first, lightbox), name, address, city, map pin.
- **Vendor chip** with the vendor's status. If the vendor is not approved yet, show an info callout:
  *"Approving now sets this hall to APPROVED. It goes live automatically when the vendor is approved."*
- Pricing (day / month; "Monthly not offered" if null), rules, contact.
- Amenities (icon chips), exams (program chips), operating hours (7-day table; highlight closed days).
- **Blocks**: cards with `name`, `totalRows × totalColumns`, `seatCount`, effective prices, amenities.
- Quality hints (computed client-side): "No cover image", "No operating hours", "0 seats", "No
  exams selected" — warnings only, they don't block approving.
- Actions: Approve · Reject (reason).

### 9.3 Users — `/users`

`GET /api/admin/users?search=&role=&status=&page=&size=&sort=createdAt,desc`

Toolbar: search ("Email or phone…", debounced 300ms), faceted filter **Role** (Student `USER` /
Vendor `VENDOR` / Admin `ADMIN`), **Status** (Active / Suspended / Disabled), column toggle, density
toggle, "Export CSV" (current page).

Quick-filter segmented control above the table: `All · Students · Vendors · Admins`.

| Column | Field | Render |
|---|---|---|
| User | `email` / `phone` | avatar initials + primary contact, secondary contact below |
| ID | `id` | mono, copy on hover |
| Role | `role` | pill (Student=indigo, Vendor=teal, Admin=violet) |
| Status | `status` | `StatusBadge` |
| Login method | `passwordSet` | "Password" / "OTP only" |
| Last login | `lastLoginAt` | relative ("3 h ago"), full date in tooltip |
| Joined | `createdAt` | date |
| ⋯ | | View · Suspend / Activate · Copy ID · (vendor) Open vendor profile |

Note: search covers email/phone only (not names) — say so in the placeholder.

Sorting: an unknown sort field returns `400 INVALID_SORT`, so only enable sorting on fields the backend
whitelists. Known whitelists today:

| List | Sortable fields |
|---|---|
| Users (`/api/admin/users`) | `createdAt` (the default). Try `lastLoginAt` / `email` in Swagger before enabling them |
| Study halls (`/api/admin/studyhalls`) | `name`, `city`, `createdAt`, `updatedAt`, `submittedAt`, `pricePerDay`, `status` |
| Bookings (`/api/admin/bookings`) | `createdAt`, `startDate`, `endDate`, `status`, `totalPrice` |
| Vendors (`/api/admin/vendors`) | `createdAt` (the default). Check Swagger for others |

### 9.4 User detail — `/users/:id`

`GET /api/admin/users/{id}` → `AdminUserDetail`. If `warnings` is non-empty, show the
`PartialDataBanner` ("Exam choices could not be loaded").

Header: big avatar, name (`profile.firstName lastName` or email/phone), role pill, status badge,
contact chips (copy), joined date, last login. Actions: **Suspend** (reason) / **Activate**; for
vendors "Open vendor profile →". **ADMIN accounts are protected** by the backend — hide Suspend for
`role === 'ADMIN'` and never show it on your own ID.

Tabs:

| Tab | Content | Source |
|---|---|---|
| Overview | Profile card (DOB, city, state, education level, preferred city); account card (login method, status, IDs) | `profile`, `account` |
| Exams | Cards: program name/code, preparation status (`PLANNED/IN_PROGRESS/…` coloured), start → target date progress bar, notes | `programs` |
| Hall memberships | Table: hall (link), seat, exam, plan, start–end, status, source (`BOOKING` / `WALK_IN`) | `enrollments` |
| Bookings | Bookings table filtered by user | `GET /api/admin/bookings?userId={id}` |
| Activity | Vertical timeline with icons per `ActivityType`, infinite scroll | `GET /api/admin/users/{id}/activity` |
| Audit | Admin actions on this account and actions it took | `GET /api/admin/audit-logs?entityType=USER_ACCOUNT&entityId={id}` |

For vendor accounts, replace Exams/Memberships with a **Vendor** tab (the embedded `vendor` object)
and link to `/vendors/:vendorId`.

### 9.5 Vendors — `/vendors`

`GET /api/admin/vendors?search=&status=&city=`

Status tabs on top with counts (from the dashboard): `All · Pending ⑦ · Approved · Draft · Rejected · Suspended`.

| Column | Field |
|---|---|
| Business | `profile.logoUrl` + `businessName`, `contactName` below |
| Contact | `email`, `phone` |
| City | `city`, `state` |
| Halls | `activeStudyHallCount` / `studyHallCount` |
| Status | `StatusBadge` + `statusReason` tooltip |
| Submitted | `submittedAt` |
| Joined | `createdAt` |
| ⋯ | status-dependent actions (§10) |

City filter: free-text combobox (the backend has no cities endpoint; suggest values from the
loaded rows).

### 9.6 Vendor detail — `/vendors/:vendorId`

`GET /api/admin/vendors/{vendorId}` → `{vendor, account}`.

- **Hero**: logo, business name, status badge, `statusReason` callout (red for REJECTED/SUSPENDED),
  action buttons from §10.
- **Status stepper**: `Registered → Profile complete → Submitted → Approved` showing where the vendor is
  (`DRAFT` with `missingForSubmission` items listed).
- Tabs:
  - **Overview** — business details grid (contact, address, GST, description), login account card
    (link to `/users/:vendorId`), key dates (created, submitted, approved).
  - **Study halls** — `GET /api/admin/studyhalls?vendorId=` as a card grid with cover image, status, seats, price.
  - **Bookings** — fetch the vendor's halls, then `GET /api/admin/bookings?studyHallId=` per hall
    (or add a `vendorId` filter, see §13).
  - **Audit** — `GET /api/admin/audit-logs?entityType=VENDOR&entityId={vendorId}`.

Suspending a vendor hides **all** its halls — the confirm dialog must say so and show the hall count:
*"Suspend Sunrise Academy? Its 3 live study halls will be hidden from students and no new bookings can be made."*

### 9.7 Study halls — `/studyhalls`

`GET /api/admin/studyhalls?search=&status=&city=&vendorId=`

View toggle: **Grid** (default; visual cards with cover image, status ribbon, name, city, vendor,
price/day, price/month, seat count) and **Table** (dense).
Status tabs: `All · Pending ④ · Live (ACTIVE) · Approved (waiting for vendor) · Inactive · Suspended · Rejected · Draft`.
Filters: search, city, vendor (async combobox over `/api/admin/vendors?search=`).

### 9.8 Study hall detail — `/studyhalls/:id`

`GET /api/admin/studyhalls/{id}`

- **Hero**: full-width cover image with gradient overlay, name, address, status badge, vendor chip
  (→ `/vendors/:vendorId`), actions (§10). `statusReason` callout.
- Tabs:
  - **Overview** — description, rules, pricing cards, contact, amenities grid with icons, exam chips,
    operating hours table, map (Leaflet) if lat/lng exists, image gallery, timeline of
    `createdAt / submittedAt / approvedAt / updatedAt`.
  - **Seating** — blocks summary cards (rows × columns, seats, effective prices, block amenities).
    For live halls (`ACTIVE`): a full **seat map** from `GET /api/studyhalls/{id}/seats?startDate=&endDate=`
    drawn with `SeatGrid` — CSS grid `totalRows × totalColumns`, seats at `(row, column)` (1-based),
    empty cells are aisles. Colour by `status` and `available` for the chosen date (date picker,
    default today). Legend: Available, Booked, Reserved (walk-in), Maintenance, Inactive. Hover card:
    seat number, type, effective prices.
  - **Bookings** — `GET /api/admin/bookings?studyHallId={id}` with status/date filters and summary chips
    (pending, confirmed, revenue of the visible page).
  - **Audit** — `GET /api/admin/audit-logs?entityType=STUDY_HALL&entityId={id}`.

### 9.9 Bookings — `/bookings`

`GET /api/admin/bookings?studyHallId=&userId=&status=&from=&to=&sort=createdAt,desc`

Toolbar: status tabs (`All · Pending · Confirmed · Completed · Cancelled · Expired`), date-range picker
(`from`/`to` = bookings **overlapping** that range; presets Today, This week, This month), hall
filter (async combobox), user ID filter.

| Column | Field |
|---|---|
| Reference | `bookingReference` (mono, copy) |
| Student | `userId` → link `/users/:userId` (name via user detail is lazy-loaded on hover; see §13) |
| Hall | `studyHallName`, `studyHallCity` → link |
| Seat | `seatNumber` |
| Exam | `programName` |
| Plan | `DAILY` / `MONTHLY` chip |
| Period | `startDate → endDate` (+ `units` days/months) |
| Amount | `totalPrice` (₹, 2 decimals), `unitPrice × units` in tooltip |
| Status | `StatusBadge`; PENDING shows a live countdown to `holdExpiresAt` |
| Created | `createdAt` |

Row click → **BookingDrawer** (right `Sheet`): every field, a status timeline
(`createdAt → confirmedAt | cancelledAt`), `cancellationReason`, links to hall/student. Read-only —
the backend has no admin booking actions.

Footer summary for the current page: count by status and the sum of `totalPrice` for CONFIRMED +
COMPLETED.

### 9.10 Exams / courses catalog — `/catalog/programs`

`GET /api/programs/all?search=&category=&active=&size=50&sort=name`

- Toolbar: search, category select (distinct categories from the loaded data, e.g. Engineering,
  Medical, Civil Services, Banking), Active/Inactive/All toggle, **+ New exam** button.
- Table **grouped by category** (collapsible group headers with counts): code (mono chip), name,
  description (truncate), active switch, actions (Edit, Deactivate).
- **Create/Edit** in a right `Sheet` with `programSchema`. `code` is auto-uppercased; show
  `409 DUPLICATE_RESOURCE` on the code field.
- **Deactivate** = `DELETE /api/programs/{id}` (soft). Confirm: *"Students and halls that reference
  TS EAMCET keep it, but it won't be offered for new choices."* **Reactivate** = `PUT` with `active: true`.
- The active switch in the row calls `PUT` with the full body and `active` toggled (optimistic update,
  rollback on error).

### 9.11 Amenities catalog — `/catalog/amenities`

`GET /api/amenities/all` (whole list; filter/search on the client).

- Responsive **card grid**: big lucide icon (from `icon`, fallback `Sparkles`), name, code, description,
  Active/Retired badge. Hover: Edit.
- **Create/Edit** sheet with an **icon picker** (searchable grid of lucide icons, stores the
  kebab-case name like `wifi`, `snowflake`, `plug`, `locker`, `coffee`, `car`, `book-open`) and a live
  preview of the card.
- No delete endpoint: "Retire" = `PUT` with `active: false`; "Restore" = `active: true`.

### 9.12 Audit log — `/audit-logs`

`GET /api/admin/audit-logs?…` (newest first).

- Filter bar: search (description or actor email), **Action** multi-group select (grouped: Admin
  actions / Platform events), **Actor role** (ADMIN, VENDOR, USER, SYSTEM), **Entity type** (VENDOR,
  STUDY_HALL, USER_ACCOUNT, PROGRAM, AMENITY), Entity ID, date-time range (`to` is exclusive).
  The backend takes one `action` at a time — a multi-select needs one request per action or a
  backend change (§13); start with a single select.
- Table: time (relative + absolute), actor (email + role pill, "System" for null), action (coloured
  verb chip with icon), entity (type + ID, **linked**: VENDOR → `/vendors/:id`, STUDY_HALL →
  `/studyhalls/:id`, USER_ACCOUNT → `/users/:id`, PROGRAM/AMENITY → catalog page), description, source
  service.
- Row expand → details: IP address, user agent (parsed: "Chrome 140 on Windows"), correlation ID
  (copy), raw JSON.
- "Export CSV" of the current filter (pages through the results, capped at e.g. 5,000 rows, with a
  progress toast).

Action colour groups: approve/activate/created = green, reject/suspend/deactivated/deleted = red,
updated/submitted = blue, registered = violet.

### 9.13 Settings

- **Profile** `/settings/profile` — `GET /api/admin/me` + `GET /api/auth/me`. Form: full name*, title,
  contact email, contact phone (`adminProfileSchema`), read-only login email/phone, role, last seen.
- **Security** `/settings/security` — change password (`currentPassword` hidden when
  `passwordSet === false`), "Log out on this device".
- **Appearance** `/settings/appearance` — theme (Light / Dark / System), accent colour, table density
  (comfortable / compact), sidebar collapsed default, reduced motion. Stored in localStorage.

---

## 10. Status → actions matrix

Drive every action button from these maps — never hard-code buttons per page. The backend enforces
the state machine (`422 INVALID_STATUS_TRANSITION`); the UI only hides actions that would fail.

**Vendor** (`/api/admin/vendors/{vendorId}/…`)

| Status | Approve | Reject (reason) | Suspend (reason) | Activate |
|---|:-:|:-:|:-:|:-:|
| DRAFT | | | | |
| PENDING | ✅ | ✅ | | |
| REJECTED | ✅ (re-approve) | | | |
| APPROVED | | | ✅ | |
| ACTIVE | | | ✅ | |
| INACTIVE | | | ✅ | ✅ |
| SUSPENDED | | | | ✅ |

**Study hall** (`/api/admin/studyhalls/{id}/…`)

| Status | Approve | Reject (reason) | Suspend (reason) | Activate |
|---|:-:|:-:|:-:|:-:|
| DRAFT | | | ✅ | |
| PENDING_APPROVAL | ✅ | ✅ | ✅ | |
| APPROVED (waiting for vendor) | | | ✅ | |
| ACTIVE | | | ✅ | |
| INACTIVE | | | ✅ | |
| REJECTED | | | ✅ | |
| SUSPENDED | | | | ✅ |

**Account** (`/api/admin/users/{id}/…`): `ACTIVE → Suspend (reason)`, `SUSPENDED → Activate`.
Hidden for `role === 'ADMIN'` and for the logged-in admin.

```ts
// src/lib/status.ts
export const vendorActions: Record<VendorStatus, Action[]> = {
  DRAFT: [], PENDING: ['approve', 'reject'], REJECTED: ['approve'],
  APPROVED: ['suspend'], ACTIVE: ['suspend'], INACTIVE: ['suspend', 'activate'], SUSPENDED: ['activate'],
};
```

Still handle `422 INVALID_STATUS_TRANSITION` gracefully: toast the message, refetch the entity
(someone else may have acted first).

### ReasonDialog

One reusable dialog for all four actions:

| Action | Tone | Reason | Copy |
|---|---|---|---|
| Approve | primary/green | optional "Note (internal)" | "Approve {name}?" + consequence line |
| Reject | destructive | **required**, 500 chars, counter | "The vendor will see this reason." + quick-pick chips: *Incomplete KYC documents*, *Invalid GST number*, *Photos unclear*, *Address could not be verified* |
| Suspend | destructive | **required** | consequence line (halls hidden / login blocked) |
| Activate | primary | optional | "Restore access for {name}?" |

---

## 11. Design system

### 11.1 Visual direction

**"Calm, confident, focused"** — a study-themed palette (deep indigo for focus, warm amber for
attention), soft surfaces, a 12-column grid, rounded-2xl cards, subtle borders instead of heavy
shadows, and a glassy topbar (`backdrop-blur`). Think Linear / Vercel dashboard polish.

### 11.2 Tokens (`globals.css`, shadcn variables, OKLCH)

```css
:root {
  --radius: 0.875rem;
  --background: oklch(0.985 0.003 265);      /* near-white with a cool tint */
  --foreground: oklch(0.21 0.03 265);
  --card: oklch(1 0 0);
  --muted: oklch(0.96 0.006 265);
  --muted-foreground: oklch(0.52 0.02 265);
  --border: oklch(0.92 0.008 265);
  --primary: oklch(0.51 0.21 275);           /* SPACZ indigo  ≈ #4F46E5 */
  --primary-foreground: oklch(0.98 0 0);
  --accent: oklch(0.80 0.15 75);             /* study-lamp amber ≈ #F59E0B */
  --success: oklch(0.65 0.15 155);           /* emerald */
  --warning: oklch(0.78 0.15 75);            /* amber */
  --destructive: oklch(0.60 0.21 25);        /* rose-red */
  --info: oklch(0.62 0.14 240);              /* sky */
  --sidebar: oklch(0.975 0.006 265);
  --chart-1: oklch(0.51 0.21 275); --chart-2: oklch(0.65 0.15 155); --chart-3: oklch(0.78 0.15 75);
  --chart-4: oklch(0.60 0.21 25);  --chart-5: oklch(0.62 0.14 240);
}
.dark {
  --background: oklch(0.16 0.02 270);        /* deep navy, not pure black */
  --foreground: oklch(0.96 0.005 265);
  --card: oklch(0.20 0.025 270);
  --muted: oklch(0.24 0.02 270);
  --muted-foreground: oklch(0.70 0.02 265);
  --border: oklch(0.28 0.02 270);
  --primary: oklch(0.66 0.18 275);
  --sidebar: oklch(0.18 0.022 270);
}
```

Typography: Inter, `font-feature-settings: "cv11", "ss01"`, tabular numbers (`tabular-nums`) on all
metrics, amounts and IDs. Scale: 12 / 13 (table) / 14 (body) / 16 / 20 / 24 / 30 (page title) / 36 (KPI).

Spacing: 4px base; page padding 24 (md) / 32 (xl); card padding 20–24; table row height 52
(comfortable) / 40 (compact).

### 11.3 Status colours (one source of truth: `lib/status.ts`)

| Status | Colour | Icon |
|---|---|---|
| ACTIVE, APPROVED, CONFIRMED, AVAILABLE | emerald | `CheckCircle2` |
| PENDING, PENDING_APPROVAL | amber (pulsing dot) | `Clock` |
| DRAFT | slate | `PencilLine` |
| REJECTED, CANCELLED | rose | `XCircle` |
| SUSPENDED, DISABLED | red (solid) | `Ban` |
| INACTIVE, EXPIRED | gray | `PauseCircle` / `TimerOff` |
| COMPLETED | sky | `BadgeCheck` |
| RESERVED (seat) | violet | `Lock` |
| MAINTENANCE (seat) | orange | `Wrench` |

Badge style: soft background (`bg-{c}-500/10`), coloured text, 1px `ring-{c}-500/20`, dot or icon,
`text-xs font-medium`, rounded-full.

### 11.4 Component inventory

| Component | Notes |
|---|---|
| `KpiCard` | icon in a tinted circle, label, big number (count-up), sub-line, optional link, skeleton state, "Unavailable" state |
| `DataTable` | TanStack Table, server-side pagination (`page`, `size` 10/20/50/100), sortable headers, sticky header, row hover, row click, column visibility, density, empty/error/loading states, mobile card fallback |
| `FacetedFilter` | popover multi/single select with counts |
| `StatusBadge` | from §11.3 |
| `EntityLink` | avatar + name + hover card preview (vendor/hall/user) |
| `ReasonDialog`, `ConfirmDialog` | §10 |
| `DetailHeader` | hero with avatar/cover, title, badges, meta, actions |
| `InfoGrid` | label/value definition grid with copy buttons |
| `Timeline` | audit + activity |
| `SeatGrid` | read-only seat map, zoom, legend |
| `ImageGallery` | thumbnails + lightbox |
| `HoursTable` | 7-day operating hours |
| `IconPicker` | lucide icon search for amenities |
| `PartialDataBanner`, `ErrorState`, `EmptyState` | consistent states |
| `CopyButton`, `RelativeTime`, `Money` (₹ with `Intl.NumberFormat('en-IN', {style:'currency', currency:'INR'})`) | |

### 11.5 Motion & polish

- Route transitions: 150ms fade + 4px rise. Drawers: spring slide. Respect `prefers-reduced-motion`.
- Skeletons that match the real layout (KPI cards, table rows, detail hero).
- Empty states with friendly illustrations ("No pending approvals 🎉 — you're all caught up").
- Number count-up on KPIs on first load only.
- Toasts: bottom-right; error toasts show `correlationId` with a copy button.
- Focus rings (`ring-2 ring-primary/40`), full keyboard navigation, WCAG AA contrast in both themes.

---

## 12. Cross-cutting UX patterns

| Pattern | Implementation |
|---|---|
| URL state | `useUrlState()` hook over `useSearchParams` with zod parsing and defaults; resets `page` to 0 when a filter changes. |
| Pagination | `placeholderData: keepPreviousData` so tables don't flash; show "1–20 of 342". |
| Search | debounce 300ms; min 2 chars; clear button; `/` shortcut. |
| Errors | `ErrorState` for page-level failures, `toast.error(message)` for mutations, `fieldErrors` → form fields; `503 SERVICE_UNAVAILABLE` → "A SPACZ service is temporarily unavailable" with retry. |
| Retries | TanStack Query: no retry on 4xx, 2 retries with backoff on 5xx/network. |
| 404 on details | `NOT_FOUND` → friendly "This vendor doesn't exist" + back link. |
| Concurrency | After `INVALID_STATUS_TRANSITION` or `CONFLICT`, refetch and show the new status. |
| Time | Show IST (`Asia/Kolkata`) everywhere with a "IST" suffix in tooltips; send `from/to` as `YYYY-MM-DD` for bookings and ISO instants for audit. |
| Money | INR, 2 decimals, `en-IN` grouping (₹1,25,000.00). |
| Phone | display `+91 98765 43210`. |
| Accessibility | Radix primitives, labelled icon buttons, `aria-live` for toasts, table captions. |
| Performance | route-level code splitting, prefetch detail query on row hover (`queryClient.prefetchQuery`), images `loading="lazy"`. |

---

## 13. Backend gaps & recommended additions

The console can be built entirely on today's API. These additions would make specific screens
better; each is optional and independent.

| # | Gap | Effect on the UI today | Suggested backend change |
|---|---|---|---|
| 1 | Dashboard has no time series | No trend lines or "bookings over the last 30 days" chart | `GET /api/admin/dashboard/trends?days=30` → daily counts (registrations, bookings, revenue) from auth/studyhall `/internal/stats` |
| 2 | Seat map is public only for live halls (`GET /api/studyhalls/{id}/seats`: "owner sees it in any status") | Admin can't see the seat layout of a **pending** hall — only `blocks[]` summaries | Let ADMIN read the seat map in any status, or add `GET /api/admin/studyhalls/{id}/seats` |
| 3 | Bookings carry `userId` only | Student name/phone not in the bookings table | Add `studentName` / `studentPhone` to admin `BookingDto` (studyhall already resolves names via user-service for vendors) |
| 4 | No `vendorId` filter on `/api/admin/bookings` | Vendor "Bookings" tab must query hall by hall | Add `vendorId` query param |
| 5 | No admin view of a hall's enrollments | Can't list a hall's students (incl. walk-ins) | `GET /api/admin/studyhalls/{id}/enrollments` |
| 6 | No admin management | New admins only via `BOOTSTRAP_ADMIN_*` | `POST /api/admin/admins` (invite) + list; then add an `/admins` page |
| 7 | Audit `action` filter is single-valued | No multi-select of actions | Accept `action` as a list |
| 8 | User search is email/phone only | Can't search students by name | Search user-service profiles by name too |
| 9 | No booking actions for admins | Booking drawer is read-only (no refunds/cancel) | `POST /api/admin/bookings/{id}/cancel {reason}` (audited) when payments arrive |
| 10 | No file upload | Images are URLs | Pre-signed upload endpoint (only needed if admins upload logos/icons) |
| 11 | Refresh token in JS storage | XSS could steal it | httpOnly, `SameSite=Strict` refresh cookie on `/api/auth/refresh` |
| 12 | No distinct-cities endpoint | City filter is free text | `GET /api/studyhalls/cities` |

---

## 14. Testing, quality & deployment

**Testing**
- **Unit** (Vitest): `status.ts` action maps, formatters, zod schemas, `useUrlState`.
- **Component** (Testing Library + MSW): DataTable filters → correct query string; ReasonDialog
  requires a reason for reject/suspend; dashboard renders partial data when `vendors` is `null`.
- **API client**: single-flight refresh — 3 parallel 401s cause exactly **one** `/api/auth/refresh`.
- **E2E** (Playwright, against `docker compose up` + bootstrap admin): login → approve a pending
  vendor → hall goes ACTIVE → appears in the audit log; suspend/activate a user; create, edit and
  deactivate a program.

**Quality gates**: `tsc --noEmit`, ESLint, Prettier, Vitest coverage ≥ 70% on `lib/` and `features/*/api.ts`,
Lighthouse ≥ 90 (performance and accessibility).

**Environment**

```
# .env.example
VITE_API_BASE_URL=http://localhost:8080
VITE_APP_NAME=SPACZ Admin
```

Add the deployed admin origin to the gateway's `CORS_ALLOWED_ORIGINS` (e.g.
`https://admin.spacz.in`). Dev runs on `http://localhost:5173`, which is already allowed.

**Deployment**: static build (`vite build`) served by Nginx (SPA fallback `try_files $uri /index.html`)
or any CDN. Optional `Dockerfile` (node build stage → nginx stage) and a `admin-web` service in
`docker-compose.yml`. Security headers: CSP (`connect-src` = gateway origin), `X-Frame-Options: DENY`,
`Referrer-Policy: strict-origin-when-cross-origin`.

---

## 15. Phased roadmap

| Phase | Scope | Deliverable |
|---|---|---|
| **0. Foundation** (2–3 days) | Vite + TS + Tailwind + shadcn setup, tokens & dark mode, AdminLayout (sidebar, topbar, breadcrumbs), API client with single-flight refresh, session store, `RequireAdmin`, Login (email + OTP), 403/404 | Log in as the bootstrap admin and see the empty shell |
| **1. Dashboard & approvals** (3–4 days) | Dashboard KPIs + charts + partial-data banner; Approvals split view for vendors and halls; ReasonDialog; sidebar badges; audit preview | The core admin job works end to end |
| **2. Directories** (4–5 days) | DataTable kit; Users list + detail (all tabs); Vendors list + detail; Study halls grid/table + detail (overview, seating, bookings, audit) | Every entity browsable and moderatable |
| **3. Bookings & catalogs** (2–3 days) | Bookings table + drawer; Programs (grouped table, sheet form, activate/deactivate); Amenities (card grid, icon picker) | Catalog curation and booking oversight |
| **4. Audit & settings** (2 days) | Audit log explorer with entity links, row expansion, CSV export; Profile, Security, Appearance | Full traceability |
| **5. Polish** (2–3 days) | ⌘K palette, keyboard shortcuts, motion, empty-state illustrations, mobile pass, accessibility audit, Playwright suite | Production-ready, "very beautiful" console |
| **6. Backend-assisted extras** | Items from §13 (trends chart, admin seat map, student names in bookings, admin management) | Richer insights |

**Definition of done for every screen:** loading skeleton, empty state, error state with correlation
ID, works in dark mode, works at 375px width, keyboard reachable, URL-restorable, and every mutation
invalidates the right queries and shows a toast.
