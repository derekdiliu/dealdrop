# dealdrop

Limited-time flash deals for New Zealand shoppers: a small number of deals go live at set times,
stock is strictly limited, and checkout is paid in NZD via Stripe.

The project focuses on the hard parts of flash-sale e-commerce: burst traffic, atomic stock
reservation with zero overselling, and keeping orders, payments and stock consistent across services.

## Architecture

```
Next.js storefront
        │
     gateway ──── JWT validation, rate limiting, routing
        │
        ├── identity     users, addresses, login (OAuth2 / JWT)
        ├── catalog      deals, pricing, images (Cloudflare R2)
        ├── order        orders, stock and purchase limits, outbox, unpaid-order timeout
        ├── payment      Stripe payments, webhooks, refunds
        └── flash-sale   atomic stock reservation (Redis + Lua), purchase limits, async order creation

PostgreSQL (database per service) · Redis · RabbitMQ · Cloudflare R2
```

| Service    | Port |
|------------|------|
| gateway    | 8080 |
| identity   | 8081 |
| catalog    | 8082 |
| order      | 8083 |
| payment    | 8084 |
| flash-sale | 8085 |
| frontend   | 3000 |

## Tech stack

- **Backend:** Java 21, Spring Boot 4.1, Spring Cloud Gateway (MVC, virtual threads)
- **Data:** PostgreSQL 17, Redis 7.4, RabbitMQ 4.1, Cloudflare R2 (S3 API)
- **Frontend:** Next.js 16, TypeScript, Tailwind CSS
- **CI:** GitHub Actions

## New Zealand specifics

- Prices are stored as integer cents in NZD and displayed GST-inclusive (15%).
- Deal start and end times use the `Pacific/Auckland` time zone, including daylight saving.
- NZ address format (street, suburb, city, postcode) with North Island, South Island and rural delivery shipping zones.

## Getting started

Prerequisites: JDK 21, Node.js 22, Docker, a Cloudflare R2 bucket for deal images, and a Stripe
account in test mode.

```bash
# Local settings: admin account, R2 and Stripe credentials (see .env.example)
cp .env.example .env

# Infrastructure: Postgres, Redis, RabbitMQ
docker compose up -d --wait

# Backend
./mvnw verify
java -jar services/gateway/target/gateway-0.1.0-SNAPSHOT.jar   # or run any service from your IDE

# Frontend: talks to the gateway; Stripe publishable key optional (see frontend/.env.example)
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

RabbitMQ management UI: http://localhost:15672 (dealdrop / dealdrop).

Integration tests use Testcontainers, so Docker must be running for `./mvnw verify`. Frontend unit
tests (Vitest) cover NZ time conversion, the deal form's validation and the sign-in return path; run
them with `npm test` in `frontend`, nothing else needs to be running.

## Authentication

`identity` signs RS256 access tokens and publishes its public key at `/.well-known/jwks.json`.
The gateway and every downstream service verify tokens against that key set, so a request is
checked at the edge and again by the service that handles it.

```bash
curl -X POST localhost:8080/api/identity/register -H 'Content-Type: application/json' \
  -d '{"email":"kiri@example.co.nz","password":"kia-ora-1234","displayName":"Kiri"}'

TOKEN=$(curl -s -X POST localhost:8080/api/identity/login -H 'Content-Type: application/json' \
  -d '{"email":"kiri@example.co.nz","password":"kia-ora-1234"}' | jq -r .accessToken)

curl localhost:8080/api/identity/me -H "Authorization: Bearer $TOKEN"
```

Errors use RFC 9457 problem details. Without configured keys, identity generates an ephemeral
key pair on startup; set `DEALDROP_JWT_PRIVATEKEY` and `DEALDROP_JWT_PUBLICKEY` to PEM resources
(e.g. `file:/run/secrets/jwt-private.pem`) to keep tokens valid across restarts.

## Deals and images

Admins manage deals through the back-office API; the storefront only sees published deals that
have not ended. Prices are NZD cents, GST-inclusive, and responses include the GST component.
Times must carry an offset (e.g. `2026-11-20T12:00:00+13:00`) so daylight saving is never ambiguous.

Images are uploaded straight to R2 with a presigned URL, so image bytes never pass through the
backend. The signature fixes the content type and size (JPEG, PNG or WebP, up to 5 MB).

```bash
ADMIN="Authorization: Bearer $ADMIN_TOKEN"

# 1. Get an upload URL, then PUT the file to it
curl -X POST localhost:8080/api/catalog/admin/images -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"contentType":"image/jpeg","contentLength":123456}'
curl -X PUT "$UPLOAD_URL" -H 'Content-Type: image/jpeg' --data-binary @throw.jpg

# 2. Create a draft deal with the returned key, then publish it
curl -X POST localhost:8080/api/catalog/admin/deals -H "$ADMIN" -H 'Content-Type: application/json' -d '{
  "title": "Merino throw", "description": "100% NZ merino.", "imageKey": "deals/....jpg",
  "rrpCents": 15900, "priceCents": 7999, "stock": 50, "perCustomerLimit": 2,
  "startsAt": "2026-11-20T12:00:00+13:00", "endsAt": "2026-11-22T12:00:00+13:00"}'
curl -X POST localhost:8080/api/catalog/admin/deals/$DEAL_ID/publish -H "$ADMIN"

# Storefront (no token)
curl localhost:8080/api/catalog/deals
```

Published deals are locked; drafts can be edited or deleted. Tests run against S3Mock instead of R2.

## Flash sale

Checkout is "buy now" on a single deal: a flash sale reserves stock at the moment of purchase, so a
cart would only defer the sold-out surprise. A sale's burst of purchases is decided in Redis and only
admitted purchases reach the database:

```
POST /api/flash-sale/deals/{id}/purchases
  │
  ▼
flash-sale ── Lua script, atomic in Redis: rate limit → per-customer limit → stock ──► 409 / 429
  │ admitted: 202 Accepted + purchase id (also the future order id)
  ▼
RabbitMQ order.place-requests (publisher confirms: accepted means stored)
  ▼
order ── Postgres transaction (below) ──► order.placed / order.rejected / order.cancelled
  ▼
flash-sale: purchase status for the client to poll; stock returned to Redis on reject or cancel
```

- **Postgres is the source of truth; Redis is the gate.** If Redis ever over-admits (say it lost its
  data and reseeded), the database rejects the extra orders and flash-sale marks the deal sold out.
- **Every step is idempotent.** Order placement checks for an existing order id, cancellation is a
  conditional update, and Redis releases stock at most once per order, so redelivered messages are harmless.
- **The gateway forwards over a bounded connection pool.** Passing a burst on one-to-one opens a
  connection per request to the backend and overflows its accept queue; capping the pool makes the
  excess wait at the edge instead. The load test below found this.

## Orders

The order service turns each admitted purchase into an order in one transaction:

1. Claim the customer's allowance for the deal (`INSERT ... ON CONFLICT DO UPDATE ... WHERE total <= limit`)
2. Reserve stock (`UPDATE ... SET remaining = remaining - n WHERE remaining >= n`)
3. Save the order as `PENDING_PAYMENT`, with prices snapshotted from the catalog
4. Write a payment-timeout message to the outbox

Each step is a single conditional statement, so concurrent buyers can never oversell or exceed a
limit; a test fires 20 concurrent placements at 5 units and expects exactly 5 orders.

Shipping is a flat rate by island, worked out from the postcode (0-6 North Island, 7-9 South Island),
plus a surcharge for rural delivery addresses. GST is shown on the order total.

**Unpaid orders** are cancelled after 15 minutes using RabbitMQ message TTL and dead-lettering:

```
order + outbox row ──(relay, publisher confirms)──► payment-timeout.waiting  (TTL = payment window)
                                                        │ expires, dead-lettered
                                                        ▼
                     cancel if still unpaid ◄── payment-timeout.expired ──(retries exhausted)──► .failed
```

Cancelling is a conditional update (`WHERE status = 'PENDING_PAYMENT'`) that returns the stock and the
customer's allowance, so a duplicate message or a race with a customer cancellation is harmless.

Admins can list every order, newest first and filtered by `status` and `dealId`, at
`GET /api/order/admin/orders`; `GET /api/payment/admin/payments/{orderId}` shows the order's payment,
including whether it was refunded.

## Payments

Payments use Stripe in NZD. The browser collects card details with Stripe.js, so they never touch
this system; the payment service only creates the PaymentIntent and listens for Stripe's webhook.

```
POST /api/payment/orders/{id}/payment-intent   (customer's token is forwarded to fetch the order,
  │                                              which also proves the order is theirs)
  ▼
Stripe PaymentIntent (idempotency key = order id) ──► client secret ──► Stripe.js in the browser
  ...customer pays...
Stripe ──webhook, signature verified──► payment ──payment.succeeded──► order: PENDING_PAYMENT → PAID
```

- **The webhook is the source of truth**, not the browser: payments can complete after the page closes,
  for example after a 3-D Secure check. The webhook only returns 200 once the event is safely in
  RabbitMQ; otherwise Stripe retries it.
- **A payment that lands after the order timed out is refunded automatically.** Marking an order paid is
  a conditional update (`WHERE status = 'PENDING_PAYMENT' AND total_cents = :amount`); if it no longer
  applies, the order service publishes `order.payment-rejected` and the payment service refunds in full.
  Cancelled orders also cancel their PaymentIntent, so this is rare.
- Stripe sits behind an interface; tests use a mock, plus Stripe's official `stripe-mock` image to check
  real API requests, and sign webhook payloads with a test secret.

Locally, Stripe needs a public URL to deliver webhooks to; the Stripe CLI forwards them:

```bash
stripe listen --forward-to localhost:8080/api/payment/webhooks/stripe   # prints the whsec_ secret
```

## Storefront

Next.js (App Router, Cache Components). Page shells are prerendered; deals, orders and the signed-in
user stream in per request.

- **Sign-in uses the backend-for-frontend pattern.** The access token lives in an `httpOnly` cookie
  that page scripts can't read. Server components and server actions attach it to API calls, and the
  few browser-side calls (polling a purchase, starting a payment) go through small Next.js route
  handlers that do the same, so the token never reaches client JavaScript and no CORS setup is needed.
- **Checkout:** address form → flash-sale purchase (202) → the page polls until the order exists →
  order page with a countdown to the payment deadline, Stripe's Payment Element, and cancel.
- Signing in or out ends with a full page load, so nothing cached for the previous identity is reused.
- **Back office** (`/admin`, shown to tokens whose `roles` include `ADMIN`): deal list, draft form with
  times entered in NZ time and sent with their offset, publish and delete; orders filtered by status or
  deal, each with its delivery address and payment, including refunds. Images go from the browser
  straight to R2 with the presigned URL, so the bucket needs a CORS rule allowing `PUT` with
  `Content-Type` from the site's origin.

## Load test

[`loadtest/flash-sale.js`](loadtest/flash-sale.js) (k6) creates a deal, signs up one customer per
virtual user, then has them all buy at the same instant, through the gateway:

```bash
k6 run -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... loadtest/flash-sale.js   # 1000 buyers, 100 units
```

Result on a laptop (Apple Silicon) also running k6, all five services, Postgres, Redis and RabbitMQ:

| | |
|---|---|
| Buyers / units | 1000 / 100 |
| Accepted / sold out / errors | 100 / 900 / 0 |
| Orders in Postgres | 100, one per customer; remaining stock 0 in Postgres and Redis |
| Purchase latency (all 1000 at once) | median 391 ms, p95 522 ms, max 536 ms |
| Last order persisted | 593 ms after the first |

Afterwards every dead-letter queue and the outbox were empty.

## Roadmap

- [x] **M0** Project skeleton, local infrastructure, CI
- [x] **M1** Identity and gateway: registration, login, JWT validation
- [x] **M2** Catalog: deal management, image upload, storefront listing
- [x] **M3a** Orders: atomic stock and purchase limits, NZ shipping, outbox, unpaid-order timeout
- [x] **M3b** Payments: Stripe PaymentIntents, webhooks, automatic refunds
- [x] **M4** Flash sale: Redis gate, async order creation, load test with zero overselling
- [x] **M5a** Storefront: sign-in, checkout, order tracking, Stripe payment page
- [x] **M5b** Back-office UI: deal list, draft form, image upload, publish
- [ ] **M5c** Deployment (VPS + Vercel)
- [ ] Refresh tokens and login rate limiting
