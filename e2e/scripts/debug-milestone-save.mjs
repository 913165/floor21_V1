/**
 * Debug helper: login as E2E partner, load Milestone setup (Clients), POST save with slab dates.
 * Full Playwright flow uses centralized Milestone Templates + Payment schedule auto-materialize instead.
 */
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const base = (process.env.FLOOR21_BASE_URL || 'http://localhost/floor21').replace(/\/$/, '');
const flow = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', '.flow-state.json'), 'utf8'));
const email = flow.user1.email;
const password = flow.user1.password;
const buildingId = flow.buildingId;
const booking = flow.bookings?.[0];
if (!booking?.bookingId) {
  console.error('No booking in flow state');
  process.exit(1);
}
const bookingId = booking.bookingId;

const jar = new Map();
function parseSetCookie(headers) {
  for (const h of headers) {
    const [pair] = h.split(';');
    const eq = pair.indexOf('=');
    if (eq > 0) jar.set(pair.slice(0, eq), pair.slice(eq + 1));
  }
}
function cookieHeader() {
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
}
async function fetchFollow(url, opts = {}) {
  const res = await fetch(url, { ...opts, headers: { ...opts.headers, Cookie: cookieHeader() }, redirect: 'manual' });
  parseSetCookie(res.headers.getSetCookie?.() || []);
  return res;
}
function extractCsrf(html) {
  return html.match(/name="_csrf"\s+value="([^"]+)"/)?.[1] ?? '';
}

let res = await fetchFollow(`${base}/login`);
let html = await res.text();
let csrf = extractCsrf(html);
res = await fetchFollow(`${base}/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ username: email, password, _csrf: csrf }),
});
if (res.status !== 302 && res.status !== 303) {
  console.error('Login failed', res.status);
  process.exit(1);
}

const setupUrl = `${base}/clients/milestone-setup?buildingId=${buildingId}&bookingId=${bookingId}`;
res = await fetchFollow(setupUrl);
html = await res.text();
csrf = extractCsrf(html);

const lineBlocks = [...html.matchAll(/name="lines\[(\d+)\]\.id"\s+value="([^"]+)"/g)];
console.log('Slab rows in form:', lineBlocks.length);
lineBlocks.forEach(([, idx, id]) => {
  const dueName = `lines[${idx}].dueDate`;
  const dueMatch = html.match(new RegExp(`name="${dueName.replace(/\[/g, '\\[').replace(/\]/g, '\\]')}"[^>]*`));
  console.log(`  row ${idx}: id=${id.slice(0, 8)}… due field: ${dueMatch?.[0]?.slice(0, 80) ?? 'MISSING'}`);
});
if (lineBlocks.length === 0) {
  console.error('No slab rows — materialize first');
  process.exit(1);
}

const body = new URLSearchParams();
body.set('_csrf', csrf);
body.set('bookingId', bookingId);
body.set('buildingId', buildingId);
for (const [, idx, id] of lineBlocks) {
  body.set(`lines[${idx}].id`, id);
  body.set(`lines[${idx}].dueDate`, '2026-03-28');
  const label = html.match(new RegExp(`name="lines\\[${idx}\\]\\.milestoneLabel"\\s+value="([^"]*)"`))?.[1] ?? 'Slab';
  body.set(`lines[${idx}].milestoneLabel`, idx === '0' ? 'E2E-DATE-TEST-SLAB' : label);
  const pct = html.match(new RegExp(`name="lines\\[${idx}\\]\\.percent"\\s+value="([^"]*)"`))?.[1] ?? '10';
  body.set(`lines[${idx}].percent`, pct);
  const agreed = html.match(new RegExp(`name="lines\\[${idx}\\]\\.agreedAmount"\\s+value="([^"]*)"`))?.[1] ?? '0';
  body.set(`lines[${idx}].agreedAmount`, idx === '0' ? '99999' : agreed);
  body.set(`lines[${idx}].extraAmount`, '0');
}

res = await fetchFollow(`${base}/clients/milestone-setup/save`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body,
});
console.log('POST save status:', res.status);
let loc = res.headers.get('location');
console.log('POST location:', loc);
if (res.status === 302 && loc) {
  if (loc.startsWith('/')) loc = base + loc;
  res = await fetchFollow(loc);
  html = await res.text();
}
const saved = html.includes('Milestone schedule saved');
const successMsg = html.match(/alert-success[^>]*>[\s\S]*?<\/div>/)?.[0] ?? '';
const dateInputs = [...html.matchAll(/name="lines\[(\d+)\]\.dueDate"[^>]*value="([^"]*)"/g)];
console.log('Success flash:', saved);
console.log('Success HTML:', successMsg.slice(0, 200));
console.log('Due date fields after reload:', dateInputs.map((m) => `[${m[1]}]=${m[2] || '(empty)'}`).join(', ') || '(none found)');
console.log('Row0 label persisted:', html.includes('E2E-DATE-TEST-SLAB'));
console.log('Row0 agreed 99999 persisted:', /99[,.]?999/.test(html));
console.log('POST body sample:', body.toString().slice(0, 300));
