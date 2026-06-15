/**
 * Login as E2E partner, create client if needed, POST /bookings/save — print response body.
 * Usage: node scripts/debug-booking-save.mjs
 */
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const base = process.env.FLOOR21_BASE_URL || 'http://localhost/floor21';
const flow = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', '.flow-state.json'), 'utf8'));
const email = flow.user1.email;
const password = flow.user1.password;
const flatId = flow.assignToUser1[0];

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
  const res = await fetch(url, {
    ...opts,
    headers: { ...opts.headers, Cookie: cookieHeader() },
    redirect: 'manual',
  });
  parseSetCookie(res.headers.getSetCookie?.() || []);
  return res;
}

function extractCsrf(html) {
  const m = html.match(/name="_csrf"\s+value="([^"]+)"/);
  return m?.[1] ?? '';
}

// Login
let res = await fetchFollow(`${base}/login`);
let html = await res.text();
let csrf = extractCsrf(html);

const loginBody = new URLSearchParams({
  username: email,
  password,
  _csrf: csrf,
});
res = await fetchFollow(`${base}/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: loginBody,
});
if (res.status !== 302 && res.status !== 303) {
  console.error('Login failed', res.status, await res.text());
  process.exit(1);
}

// New booking form
res = await fetchFollow(`${base}/bookings/new`);
html = await res.text();
csrf = extractCsrf(html);

const clientMatch = html.match(/<option[^>]+value="([0-9a-f-]+)"[^>]*>[^<]+<\/option>/i);
const clientId = clientMatch?.[1];
if (!clientId) {
  console.error('No client in dropdown — create a client first');
  process.exit(1);
}

const flatInForm = html.includes(`value="${flatId}"`);
console.log('Partner:', email);
console.log('Flat in form:', flatInForm, flatId);
console.log('Client:', clientId);

const saveBody = new URLSearchParams({
  _csrf: csrf,
  'client.id': clientId,
  'flat.id': flatId,
  bookingDate: '2026-06-15',
  considerationAmt: '5000000',
  quotedAmount: '',
  brokerage: '',
  tds: '',
  gst: '',
  finalAmount: '',
  dueAmountDate: '',
  bookingIntimationDate: '',
  nocRequestDate: '',
  'broker.id': '',
  'executive.id': '',
  marketValue: '',
  stampDutyAmount: '',
  registrationAmount: '',
});

res = await fetchFollow(`${base}/bookings/save`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: saveBody,
});

console.log('POST /bookings/save status:', res.status);
const body = await res.text();
const title = body.match(/<title>([^<]*)<\/title>/i)?.[1];
const errMsg = body.match(/Something went wrong[^<]*|cannot convert|Exception|EL1001E[^<]*/gi);
console.log('Title:', title);
console.log('Snippets:', errMsg?.slice(0, 5) ?? body.slice(0, 800));
