// Flash-sale rush: many buyers try to buy the same limited deal at the same instant.
//
//   k6 run -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... loadtest/flash-sale.js
//
// Optional: -e BUYERS=1000 -e STOCK=100 -e BASE_URL=http://localhost:8080 -e WARMUP_SECONDS=60
//
// setup() creates and publishes a deal. Each virtual user then registers and signs in as its own
// customer, waits for a shared start time, and makes exactly one purchase attempt, so every purchase
// arrives in the same instant. Sign-ups are spread over the first half of the warm-up, as real
// customers arrive over time; only the purchases are meant to be simultaneous. Afterwards, check the
// database: the number of orders for the deal must equal STOCK.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BUYERS = Number(__ENV.BUYERS || 1000);
const STOCK = Number(__ENV.STOCK || 100);
const WARMUP_SECONDS = Number(__ENV.WARMUP_SECONDS || 60);
const IMAGE = open('./deal.png', 'b');

const accepted = new Counter('purchases_accepted');
const soldOut = new Counter('purchases_sold_out');
const unexpected = new Counter('purchases_unexpected');
const lateStarters = new Counter('buyers_late_to_start');
const signUpFailures = new Counter('buyers_sign_up_failed');

export const options = {
  setupTimeout: '2m',
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: BUYERS, iterations: 1, maxDuration: `${WARMUP_SECONDS + 120}s` },
  },
  thresholds: {
    purchases_accepted: [`count==${STOCK}`],
    purchases_unexpected: ['count==0'],
    buyers_late_to_start: ['count==0'],
    buyers_sign_up_failed: ['count==0'],
    // Also makes k6 report purchase latency on its own, apart from sign-up and login.
    'http_req_duration{name:purchase}': ['p(95)<2000'],
  },
};

function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { headers };
}

function login(email, password) {
  const response = http.post(`${BASE_URL}/api/identity/login`, JSON.stringify({ email, password }),
    { ...jsonHeaders(), tags: { name: 'login' } });
  return check(response, { 'signed in': (r) => r.status === 200 }) ? response.json('accessToken') : null;
}

export function setup() {
  const admin = login(__ENV.ADMIN_EMAIL, __ENV.ADMIN_PASSWORD);

  const upload = http.post(`${BASE_URL}/api/catalog/admin/images`,
    JSON.stringify({ contentType: 'image/png', contentLength: IMAGE.byteLength }), jsonHeaders(admin)).json();
  check(http.put(upload.uploadUrl, IMAGE, { headers: { 'Content-Type': 'image/png' } }),
    { 'image uploaded': (r) => r.status === 200 });

  const now = Date.now();
  const deal = http.post(`${BASE_URL}/api/catalog/admin/deals`, JSON.stringify({
    title: `Load test ${new Date(now).toISOString()}`,
    description: 'Created by the k6 flash-sale load test.',
    imageKey: upload.key,
    rrpCents: 10000,
    priceCents: 5000,
    stock: STOCK,
    perCustomerLimit: 1,
    startsAt: new Date(now - 60_000).toISOString(),
    endsAt: new Date(now + 3_600_000).toISOString(),
  }), jsonHeaders(admin)).json();
  check(http.post(`${BASE_URL}/api/catalog/admin/deals/${deal.id}/publish`, null, jsonHeaders(admin)),
    { 'deal published': (r) => r.status === 200 });

  return { dealId: deal.id, imageKey: upload.key, runId: now, startAt: now + WARMUP_SECONDS * 1000 };
}

export default function (data) {
  sleep((__VU / BUYERS) * (WARMUP_SECONDS / 2));
  const email = `buyer-${data.runId}-${__VU}@loadtest.example`;
  http.post(`${BASE_URL}/api/identity/register`,
    JSON.stringify({ email, password: 'loadtest-password', displayName: `Buyer ${__VU}` }),
    { ...jsonHeaders(), tags: { name: 'register' } });
  const token = login(email, 'loadtest-password');
  if (!token) {
    signUpFailures.add(1);
    return;
  }

  const wait = data.startAt - Date.now();
  if (wait > 0) {
    sleep(wait / 1000);
  } else {
    lateStarters.add(1);
  }

  const response = http.post(`${BASE_URL}/api/flash-sale/deals/${data.dealId}/purchases`, JSON.stringify({
    quantity: 1,
    shippingAddress: {
      recipientName: `Buyer ${__VU}`, phone: '021 555 0100', line1: '1 Queen Street',
      city: 'Auckland', postcode: '1010', ruralDelivery: false,
    },
  }), { ...jsonHeaders(token), tags: { name: 'purchase' } });

  if (response.status === 202) {
    accepted.add(1);
  } else if (response.status === 409) {
    soldOut.add(1);
  } else {
    unexpected.add(1);
    console.warn(`VU ${__VU}: HTTP ${response.status} ${response.body}`);
  }
}

export function teardown(data) {
  console.log(`deal ${data.dealId} image ${data.imageKey}`);
}
