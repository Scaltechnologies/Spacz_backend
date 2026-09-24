#!/usr/bin/env node
// End-to-end smoke test of a running SPACZ stack, entirely through the API gateway.
//
//   node scripts/smoke-test.mjs                      # gateway on http://localhost:8080
//   GATEWAY=http://host:8080 ADMIN_EMAIL=... ADMIN_PASSWORD=... node scripts/smoke-test.mjs
//
// Needs Node 18+ and the bootstrap admin credentials (read from .env when present). The phone-OTP
// steps need OTP_EXPOSE_CODE=true (development only) and are skipped otherwise.

import { readFileSync, existsSync } from 'node:fs';

const GATEWAY = process.env.GATEWAY ?? 'http://localhost:8080';
const envFile = new URL('../.env', import.meta.url);
const env = existsSync(envFile)
  ? Object.fromEntries(readFileSync(envFile, 'utf8').split(/\r?\n/)
      .filter(l => l && !l.startsWith('#') && l.includes('='))
      .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]))
  : {};
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? env.BOOTSTRAP_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? env.BOOTSTRAP_ADMIN_PASSWORD;
const run = Date.now().toString(36);
const phoneSuffix = String(Date.now()).slice(-8);
let passed = 0;

async function call(method, path, { token, body, expect } = {}) {
  const res = await fetch(GATEWAY + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (expect !== undefined && res.status !== expect) {
    console.error(`✗ ${method} ${path}: expected ${expect}, got ${res.status}\n${text}`);
    process.exit(1);
  }
  return { status: res.status, json, headers: res.headers };
}

function check(label, condition, detail = '') {
  if (!condition) {
    console.error(`✗ ${label} ${detail}`);
    process.exit(1);
  }
  passed++;
  console.log(`✓ ${label}`);
}

const inDays = n => { const d = new Date(); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
const sleep = ms => new Promise(r => setTimeout(r, ms));

// ---------------------------------------------------------------------------------------- basics
const health = await call('GET', '/actuator/health', { expect: 200 });
check('gateway is healthy', health.json.status === 'UP');
check('responses carry a correlation id', !!health.headers.get('x-correlation-id'));
const internal = await call('GET', '/internal/accounts/stats');
check('gateway blocks /internal/**', [401, 403, 404].includes(internal.status));
if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  console.error('Set ADMIN_EMAIL / ADMIN_PASSWORD (or BOOTSTRAP_ADMIN_* in .env)');
  process.exit(1);
}
const aTok = (await call('POST', '/api/auth/login', { expect: 200, body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } })).json.accessToken;

// -------------------------------------------------------------------------------------- students
const student = (await call('POST', '/api/auth/register', { expect: 201, body: {
  role: 'USER', email: `asha.${run}@example.com`, password: 'Secret123', firstName: 'Asha', lastName: 'Rao', city: 'Hyderabad' } })).json;
check('student registered through POST /api/auth/register', student.user.role === 'USER');
const sTok = student.accessToken;
const studentId = student.user.id;
check('student profile created in user-service', (await call('GET', '/api/users/me', { token: sTok, expect: 200 })).json.firstName === 'Asha');
const other = (await call('POST', '/api/auth/register/user', { expect: 201, body: {
  email: `ravi.${run}@example.com`, password: 'Secret123', firstName: 'Ravi' } })).json;
const oTok = other.accessToken;
await call('GET', `/api/users/${studentId}`, { token: oTok, expect: 403 });
check('a student cannot read another student\'s profile', true);

const programs = (await call('GET', '/api/programs', { expect: 200 })).json;
const upsc = programs.find(p => p.code === 'UPSC_CSE');
check('exam catalog (served by studyhall-service) includes EAMCET', programs.some(p => p.code === 'EAMCET'));
const choice = (await call('POST', '/api/users/me/programs', { token: sTok, expect: 201,
  body: { programId: upsc.id, startDate: inDays(0), targetDate: inDays(240), status: 'IN_PROGRESS' } })).json;
check('student program choice validated against the catalog and snapshotted', choice.programName === upsc.name);

// ---------------------------------------------------------------------------------------- vendor
let vTok;
let vendorId;
const devOtp = (await call('POST', '/api/auth/otp/request', { expect: 202, body: { phone: `98${phoneSuffix}` } })).json;
if (devOtp.devCode) {
  const reg = await call('POST', '/api/auth/otp/verify', { body: { phone: `98${phoneSuffix}`, code: devOtp.devCode } });
  check('unknown phone + code asks for registration (422 REGISTRATION_REQUIRED)', reg.status === 422);
  const vendor = (await call('POST', '/api/auth/otp/verify', { expect: 200, body: {
    phone: `98${phoneSuffix}`, code: devOtp.devCode, role: 'VENDOR', businessName: `Focus Hall ${run}` } })).json;
  check('vendor registered with phone OTP', vendor.user.role === 'VENDOR' && vendor.user.phone.endsWith(phoneSuffix));
  vTok = vendor.accessToken;
  vendorId = vendor.user.id;
} else {
  console.log('• OTP_EXPOSE_CODE is off: registering the vendor with email instead');
  const vendor = (await call('POST', '/api/auth/register', { expect: 201, body: {
    role: 'VENDOR', email: `owner.${run}@example.com`, password: 'Secret123', businessName: `Focus Hall ${run}`,
    contactName: 'Ravi Kumar', phone: `98${phoneSuffix}` } })).json;
  vTok = vendor.accessToken;
  vendorId = vendor.user.id;
}
const draft = (await call('GET', '/api/vendors/me', { token: vTok, expect: 200 })).json;
check('vendor profile starts as DRAFT with missing fields listed', draft.status === 'DRAFT' && draft.missingForSubmission.length > 0);
await call('POST', '/api/vendors/me/submit', { token: vTok, expect: 422 });
await call('PUT', '/api/vendors/me', { token: vTok, expect: 200, body: {
  businessName: `Focus Hall ${run}`, contactName: 'Ravi Kumar', phone: `+9198${phoneSuffix}`,
  addressLine: 'Plot 12, Ameerpet', city: 'Hyderabad', state: 'Telangana' } });
check('vendor submitted a complete profile for approval',
  (await call('POST', '/api/vendors/me/submit', { token: vTok, expect: 200 })).json.status === 'PENDING');

const hall = (await call('POST', '/api/studyhalls', { token: vTok, expect: 201, body: {
  name: `Focus Hall Ameerpet ${run}`, addressLine: 'Plot 12, Ameerpet', city: 'Hyderabad', state: 'Telangana',
  latitude: 17.4375, longitude: 78.4482, pricePerDay: 120, openingTime: '06:00', closingTime: '22:00' } })).json;
check('study hall created as DRAFT', hall.status === 'DRAFT');
const amenities = (await call('GET', '/api/amenities', { expect: 200 })).json;
const ac = amenities.find(a => a.code === 'AC');
const block = (await call('POST', `/api/studyhalls/${hall.id}/blocks`, { token: vTok, expect: 201, body: {
  name: 'AC Hall', totalRows: 4, totalColumns: 6, gaps: [{ row: 1, column: 3 }, { row: 2, column: 3 }],
  dailyPrice: 150, monthlyPrice: 2500, amenityIds: [ac.id] } })).json;
check('AC block (4x6, 2 gaps) with daily + monthly prices generated 22 seats', block.seats.length === 22);
await call('POST', `/api/studyhalls/${hall.id}/programs`, { token: vTok, expect: 200, body: { programIds: [upsc.id] } });
await call('POST', `/api/studyhalls/${hall.id}/submit`, { token: vTok, expect: 200 });

// ------------------------------------------------------------------------------------- approvals
await call('GET', '/api/admin/dashboard', { token: sTok, expect: 403 });
const queue = (await call('GET', '/api/admin/vendors?status=PENDING&size=100', { token: aTok, expect: 200 })).json;
check('vendor is in the admin approval queue', queue.content.some(v => v.profile.vendorId === vendorId));
const hallApproved = (await call('POST', `/api/admin/studyhalls/${hall.id}/approve`, { token: aTok, expect: 200 })).json;
check('hall approved before its vendor waits in APPROVED', hallApproved.status === 'APPROVED');
await call('POST', `/api/admin/vendors/${vendorId}/reject`, { token: aTok, expect: 400, body: {} });
await call('POST', `/api/admin/vendors/${vendorId}/approve`, { token: aTok, expect: 200 });
const live = (await call('GET', `/api/studyhalls/${hall.id}`, { expect: 200 })).json;
check('approving the vendor took the hall live automatically', live.status === 'ACTIVE');

// ------------------------------------------------------------------------------ search & booking
const search = (await call('GET', `/api/studyhalls?city=Hyderabad&programId=${upsc.id}&amenityIds=${ac.id}&search=${run}`, { expect: 200 })).json;
check('hall found by public search (city + exam + block amenity)', search.content.some(h => h.id === hall.id));
const start = inDays(2);
const map = (await call('GET', `/api/studyhalls/${hall.id}/seats?startDate=${start}&endDate=${inDays(4)}`, { expect: 200 })).json;
const [seat1, seat2, seat3] = map.blocks[0].seats;
check('seat map shows block prices and availability', seat1.available === true && Number(seat1.pricePerDay) === 150);

const booking = (await call('POST', '/api/bookings', { token: sTok, expect: 201, body: {
  studyHallId: hall.id, seatId: seat1.id, startDate: start, endDate: inDays(4), programId: upsc.id } })).json;
check('daily booking held as PENDING, priced by the server (3 x 150)', booking.status === 'PENDING' && Number(booking.totalPrice) === 450);
const clash = await call('POST', '/api/bookings', { token: oTok, body: {
  studyHallId: hall.id, seatId: seat1.id, startDate: inDays(3), endDate: inDays(6) } });
check('overlapping booking rejected (409 SEAT_UNAVAILABLE)', clash.status === 409 && clash.json.error === 'SEAT_UNAVAILABLE');
const monthly = (await call('POST', '/api/bookings', { token: oTok, expect: 201, body: {
  studyHallId: hall.id, seatId: seat2.id, plan: 'MONTHLY', startDate: start, months: 2 } })).json;
check('monthly booking priced at the block monthly rate (2 x 2500)', Number(monthly.totalPrice) === 5000 && monthly.units === 2);
await call('POST', `/api/bookings/${booking.id}/confirm`, { token: sTok, expect: 200 });
const enrollments = (await call('GET', '/api/users/me/enrollments', { token: sTok, expect: 200 })).json;
check('confirmed booking enrolled the student in the hall (user → studyhall)',
  enrollments.some(e => e.studyHallId === hall.id && e.status === 'ACTIVE'));

const walkIn = (await call('POST', `/api/studyhalls/${hall.id}/enrollments`, { token: vTok, expect: 201, body: {
  guestName: 'Kiran Walk-in', guestPhone: '+919811100000', seatId: seat3.id, startDate: start } })).json;
check('vendor added a walk-in student holding a seat', walkIn.source === 'WALK_IN');
await call('POST', '/api/bookings', { token: oTok, expect: 422, body: {
  studyHallId: hall.id, seatId: seat3.id, startDate: inDays(10), endDate: inDays(10) } });
check('the walk-in\'s seat is no longer bookable online', true);
const students = (await call('GET', '/api/vendors/me/students', { token: vTok, expect: 200 })).json;
check('vendor sees both students with contact details',
  students.totalElements === 2 && students.content.some(s => s.student.name === 'Asha Rao'));

// --------------------------------------------------------------------------- legacy Partner API
const owners = (await call('GET', '/api/owners', { token: vTok, expect: 200 })).json;
check('legacy Partner API shows the same data (GET /api/owners)',
  owners.length === 1 && owners[0].properties[0].blocks[0].blockMonthlyPrice === 2500);
const legacyProperty = (await call('POST', '/api/properties', { token: vTok, expect: 200, body: {
  propertyName: `Partner App Hall ${run}`, address: 'SR Nagar', googleCoordinates: '17.44,78.44', owner: { ownerId: owners[0].ownerId } } })).json;
check('property created through the legacy API is submitted for approval',
  (await call('GET', `/api/studyhalls/${legacyProperty.propertyId}`, { token: vTok, expect: 200 })).json.status === 'PENDING_APPROVAL');

// ------------------------------------------------------------------------------ admin oversight
const newProgram = await call('POST', '/api/programs', { token: aTok, expect: 201, body: {
  code: `RBI_${run}`.toUpperCase(), name: `RBI Grade B ${run}`, category: 'Banking' } });
check('admin manages the exam catalog through /api/programs', !!newProgram.json.id);
const dash = (await call('GET', '/api/admin/dashboard', { token: aTok, expect: 200 })).json;
check('dashboard aggregates auth + studyhall stats',
  dash.unavailableServices.length === 0 && dash.bookings.activeEnrollments >= 2 && dash.studyHalls.activePrograms > 20);
const detail = (await call('GET', `/api/admin/users/${studentId}`, { token: aTok, expect: 200 })).json;
check('admin user detail composes profile, exam choices and enrollments',
  detail.profile.firstName === 'Asha' && detail.programs.length === 1 && detail.enrollments.length >= 1);
await call('POST', `/api/admin/vendors/${vendorId}/suspend`, { token: aTok, expect: 200, body: { reason: 'Smoke test' } });
await call('GET', `/api/studyhalls/${hall.id}`, { expect: 404 });
check('suspending the vendor hides the hall', true);
await call('POST', `/api/admin/vendors/${vendorId}/activate`, { token: aTok, expect: 200 });

await sleep(1500);   // domain events are delivered asynchronously after commit
const vendorAudit = (await call('GET', `/api/admin/audit-logs?entityType=VENDOR&entityId=${vendorId}&size=50`, { token: aTok, expect: 200 })).json;
const actions = vendorAudit.content.map(a => a.action);
check('audit log has admin actions AND events from auth/studyhall',
  ['VENDOR_REGISTERED', 'VENDOR_SUBMITTED', 'VENDOR_APPROVED', 'VENDOR_SUSPENDED', 'VENDOR_ACTIVATED'].every(a => actions.includes(a)),
  actions.join(','));
const hallAudit = (await call('GET', `/api/admin/audit-logs?action=STUDY_HALL_CREATED&entityId=${hall.id}`, { token: aTok, expect: 200 })).json;
check('study hall creation audited by studyhall-service', hallAudit.content[0]?.sourceService === 'studyhall-service');
const activity = (await call('GET', '/api/users/me/activity', { token: sTok, expect: 200 })).json;
check('booking events reached the student\'s activity (studyhall → user)',
  activity.content.some(a => a.type === 'BOOKING_CONFIRMED'), activity.content.map(a => a.type).join(','));
check('admin profile available', (await call('GET', '/api/admin/me', { token: aTok, expect: 200 })).json.adminUserId > 0);

const refreshed = (await call('POST', '/api/auth/refresh', { expect: 200, body: { refreshToken: student.refreshToken } })).json;
await call('POST', '/api/auth/refresh', { expect: 401, body: { refreshToken: student.refreshToken } });
check('refresh tokens rotate and cannot be replayed', !!refreshed.accessToken);

console.log(`\nAll ${passed} checks passed against ${GATEWAY}`);
