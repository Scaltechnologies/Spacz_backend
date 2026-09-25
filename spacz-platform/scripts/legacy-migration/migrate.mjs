#!/usr/bin/env node
// One-time migration of the legacy MySQL `spacz` database (spacz monolith + spacz-partner-service)
// into the SPACZ microservices. Writes ONLY through the services' internal import APIs, so every
// service stays the sole writer of its own database. Safe to re-run: imports are idempotent on legacy IDs.
//
//   cd scripts/legacy-migration && npm install
//   node migrate.mjs --dry-run      # read + validate + report, write nothing
//   node migrate.mjs                # migrate
//
// Configuration (environment; INTERNAL_API_KEY is read from ../../.env when not set):
//   MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD=root MYSQL_DATABASE=spacz
//   AUTH_URL=http://localhost:8081 USER_URL=http://localhost:8082 STUDYHALL_URL=http://localhost:8083
//
// Mapping (see docs/MIGRATION_PLAN.md):
//   user_login + owner  → auth account (VENDOR, phone login) + studyhall vendor profile (APPROVED)
//   property            → study hall (ACTIVE)          image → study hall image
//   block (+ amenity)   → block with prices and AC/WIFI/WATER/LOCKER/NEWSPAPER amenities
//   seat                → seat (auto-placed on the block grid; is_reserved → RESERVED)
//   aspirant_user       → auth account (USER) + user profile   (Aadhaar and addresses are NOT migrated)
//   booking             → CONFIRMED booking (+ hall enrollment)

import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import mysql from 'mysql2/promise';

const DRY_RUN = process.argv.includes('--dry-run');
const envFile = new URL('../../.env', import.meta.url);
const dotenv = existsSync(envFile)
  ? Object.fromEntries(readFileSync(envFile, 'utf8').split(/\r?\n/)
      .filter(l => l && !l.startsWith('#') && l.includes('='))
      .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]))
  : {};
const cfg = {
  mysql: {
    host: process.env.MYSQL_HOST ?? 'localhost',
    port: Number(process.env.MYSQL_PORT ?? 3306),
    user: process.env.MYSQL_USER ?? 'root',
    password: process.env.MYSQL_PASSWORD ?? 'root',
    database: process.env.MYSQL_DATABASE ?? 'spacz',
    // DATE columns as plain 'YYYY-MM-DD' strings: JS Dates at local midnight shift by a day when serialised.
    dateStrings: true,
  },
  auth: process.env.AUTH_URL ?? `http://localhost:${dotenv.AUTH_HOST_PORT ?? 8081}`,
  user: process.env.USER_URL ?? `http://localhost:${dotenv.USER_HOST_PORT ?? 8082}`,
  studyhall: process.env.STUDYHALL_URL ?? `http://localhost:${dotenv.STUDYHALL_HOST_PORT ?? 8083}`,
  key: process.env.INTERNAL_API_KEY ?? dotenv.INTERNAL_API_KEY,
};
if (!DRY_RUN && !cfg.key) {
  console.error('INTERNAL_API_KEY is required (env or ../../.env)');
  process.exit(1);
}

const report = {
  startedAt: new Date().toISOString(), dryRun: DRY_RUN,
  counts: {}, vendors: [], students: [], bookings: [], skipped: [], warnings: [],
};
const skip = (what, id, reason) => report.skipped.push({ what, legacyId: id, reason });

async function call(base, method, path, body) {
  const res = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', 'X-Internal-Api-Key': cfg.key, 'X-Source-Service': 'legacy-migration' },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${path} → HTTP ${res.status}: ${text}`);
  }
  return text ? JSON.parse(text) : null;
}

const bit = v => v === true || v === 1 || (Buffer.isBuffer(v) && v[0] === 1);
const blank = v => v === null || v === undefined || String(v).trim() === '';

function splitName(name) {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return { firstName: 'Student', lastName: null };
  return { firstName: parts[0].slice(0, 80), lastName: parts.slice(1).join(' ').slice(0, 80) || null };
}

// -------------------------------------------------------------------------------------------------
const db = await mysql.createConnection(cfg.mysql);
const q = async sql => (await db.query(sql))[0];
const logins = await q('select * from user_login');
const owners = await q('select * from owner');
const properties = await q('select * from property');
const images = await q('select * from image');
const blocks = await q('select * from block');
const amenities = await q('select * from amenity');
const seats = await q('select * from seat');
const aspirants = await q('select * from aspirant_user');
const bookings = await q('select * from booking');
await db.end();
report.counts.legacy = {
  user_login: logins.length, owner: owners.length, property: properties.length, image: images.length,
  block: blocks.length, amenity: amenities.length, seat: seats.length, aspirant_user: aspirants.length,
  booking: bookings.length,
};
console.log(`Legacy rows: ${JSON.stringify(report.counts.legacy)}${DRY_RUN ? '  (dry run)' : ''}`);

const loginById = new Map(logins.map(l => [l.login_id, l]));
const usedLogins = new Set();

// 1. Owners → vendor accounts + vendor profiles with all their listings -------------------------
for (const owner of owners) {
  const login = owner.login_id != null ? loginById.get(owner.login_id) : null;
  if (login) usedLogins.add(login.login_id);
  const phone = !blank(login?.phone_number) ? login.phone_number : owner.owner_phone_number;
  const email = blank(owner.owner_email) ? null : owner.owner_email;
  if (blank(phone) && !email) {
    skip('owner', owner.owner_id, 'no phone number or email to log in with');
    continue;
  }
  const payload = {
    legacyOwnerId: owner.owner_id, ownerName: owner.owner_name, ownerEmail: email,
    ownerPhoneNumber: owner.owner_phone_number, address: owner.address,
    properties: properties.filter(p => p.owner_id === owner.owner_id).map(p => ({
      legacyPropertyId: p.property_id, propertyName: p.property_name, address: p.address,
      googleCoordinates: p.google_coordinates,
      images: images.filter(i => i.property_id === p.property_id)
        .map(i => ({ legacyImageId: i.image_id, imageUrl: i.image_url })),
      blocks: blocks.filter(b => b.property_id === p.property_id).map(b => {
        const a = amenities.find(x => x.block_id === b.block_id);
        return {
          legacyBlockId: b.block_id, blockName: b.block_name,
          blockDailyPrice: b.block_daily_price, blockMonthlyPrice: b.block_monthly_price,
          amenity: a ? { ac: bit(a.ac), wifi: bit(a.wifi), water: bit(a.water), lockers: bit(a.lockers), newspapers: bit(a.newspapers) } : null,
          seats: seats.filter(s => s.block_id === b.block_id).map(s => ({
            legacySeatId: s.seat_id, seatNumber: s.seat_number, reserved: bit(s.is_reserved), seatPrice: s.seat_price,
          })),
        };
      }),
    })),
  };
  if (DRY_RUN) {
    report.vendors.push({ legacyOwnerId: owner.owner_id, phone, email, properties: payload.properties.length });
    continue;
  }
  try {
    const account = await call(cfg.auth, 'POST', '/internal/accounts/import',
      { legacyLoginId: owner.login_id ?? null, phone: blank(phone) ? null : phone, email, role: 'VENDOR' });
    if (account.role !== 'VENDOR') {
      skip('owner', owner.owner_id, `phone/email already belongs to a ${account.role} account (${account.accountId})`);
      continue;
    }
    const result = await call(cfg.studyhall, 'POST', '/internal/import/vendors', { vendorId: account.accountId, ...payload });
    report.vendors.push({ legacyOwnerId: owner.owner_id, accountId: account.accountId, ...result });
    result.warnings.forEach(w => report.warnings.push(`owner ${owner.owner_id}: ${w}`));
    console.log(`owner ${owner.owner_id} → vendor ${account.accountId} (${result.created ? 'imported' : 'already imported'})`);
  } catch (err) {
    skip('owner', owner.owner_id, err.message);
  }
}

// 2. Aspirants → student accounts + profiles -----------------------------------------------------
const accountByAspirant = new Map();
for (const a of aspirants) {
  const phone = blank(a.phone_number) ? null : a.phone_number;
  const email = blank(a.email) ? null : a.email;
  if (!phone && !email) {
    skip('aspirant_user', a.aspirant_user_id, 'no phone number or email to log in with');
    continue;
  }
  const login = phone ? logins.find(l => l.phone_number === phone && !usedLogins.has(l.login_id)) : null;
  if (login) usedLogins.add(login.login_id);
  if (!blank(a.aadhar_number) || !blank(a.current_address) || !blank(a.permanent_address)) {
    report.warnings.push(`aspirant ${a.aspirant_user_id}: Aadhaar/addresses not migrated (not part of the new profile model)`);
  }
  if (DRY_RUN) {
    report.students.push({ legacyAspirantId: a.aspirant_user_id, phone, email });
    continue;
  }
  try {
    const account = await call(cfg.auth, 'POST', '/internal/accounts/import',
      { legacyLoginId: login?.login_id ?? null, phone, email, role: 'USER' });
    if (account.role !== 'USER') {
      skip('aspirant_user', a.aspirant_user_id, `phone/email already belongs to a ${account.role} account (${account.accountId})`);
      continue;
    }
    const { firstName, lastName } = splitName(a.name);
    await call(cfg.user, 'POST', '/internal/users', { userId: account.accountId, email, firstName, lastName, phone });
    accountByAspirant.set(a.aspirant_user_id, account.accountId);
    report.students.push({ legacyAspirantId: a.aspirant_user_id, accountId: account.accountId, created: account.created });
  } catch (err) {
    skip('aspirant_user', a.aspirant_user_id, err.message);
  }
}
for (const l of logins) {
  if (!usedLogins.has(l.login_id)) {
    skip('user_login', l.login_id, 'login without an owner or aspirant profile (nothing to migrate; the person can register with phone OTP)');
  }
}

// 3. Bookings ------------------------------------------------------------------------------------
for (const b of bookings) {
  if (!b.start_date || !b.end_date || b.seat_id == null || b.aspirant_user_id == null) {
    skip('booking', b.booking_id, 'incomplete booking (dates, seat or aspirant missing)');
    continue;
  }
  const userId = accountByAspirant.get(b.aspirant_user_id);
  if (DRY_RUN) {
    report.bookings.push({ legacyBookingId: b.booking_id });
    continue;
  }
  if (!userId) {
    skip('booking', b.booking_id, `aspirant ${b.aspirant_user_id} was not migrated`);
    continue;
  }
  try {
    const fmt = d => String(d).slice(0, 10);
    const result = await call(cfg.studyhall, 'POST', '/internal/import/bookings', {
      legacyBookingId: b.booking_id, userId, legacySeatId: b.seat_id, startDate: fmt(b.start_date), endDate: fmt(b.end_date),
    });
    report.bookings.push(result);
    if (result.outcome.startsWith('SKIPPED')) skip('booking', b.booking_id, result.outcome);
  } catch (err) {
    skip('booking', b.booking_id, err.message);
  }
}

// -------------------------------------------------------------------------------------------------
report.finishedAt = new Date().toISOString();
report.counts.migrated = {
  vendors: report.vendors.filter(v => v.vendorProfileId || DRY_RUN).length,
  students: report.students.length,
  bookings: report.bookings.filter(b => DRY_RUN || b.outcome === 'CREATED' || b.outcome === 'ALREADY_IMPORTED').length,
  skipped: report.skipped.length,
  warnings: report.warnings.length,
};
const dir = new URL('./reports/', import.meta.url);
mkdirSync(dir, { recursive: true });
const file = new URL(`migration-${report.startedAt.replace(/[:.]/g, '-')}${DRY_RUN ? '-dry-run' : ''}.json`, dir);
writeFileSync(file, JSON.stringify(report, null, 2));
console.log(`\nDone: ${JSON.stringify(report.counts.migrated)}`);
console.log(`Report (legacy → new ID mapping, skipped rows, warnings): ${file.pathname}`);
if (report.skipped.length) {
  console.log('Skipped:');
  report.skipped.forEach(s => console.log(`  ${s.what} ${s.legacyId}: ${s.reason}`));
}
