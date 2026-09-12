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
        ├── order        cart, orders, outbox, unpaid-order timeout
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

Prerequisites: JDK 21, Node.js 22, Docker, and a Cloudflare R2 bucket for deal images.

```bash
# Local settings: admin account and R2 credentials (see .env.example)
cp .env.example .env

# Infrastructure: Postgres, Redis, RabbitMQ
docker compose up -d --wait

# Backend
./mvnw verify
java -jar services/gateway/target/gateway-0.1.0-SNAPSHOT.jar   # or run any service from your IDE

# Frontend (talks to the gateway; override with API_BASE_URL)
cd frontend
npm install
npm run dev
```

RabbitMQ management UI: http://localhost:15672 (dealdrop / dealdrop).

Integration tests use Testcontainers, so Docker must be running for `./mvnw verify`.

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

## Roadmap

- [x] **M0** Project skeleton, local infrastructure, CI
- [x] **M1** Identity and gateway: registration, login, JWT validation
- [x] **M2** Catalog: deal management, image upload, storefront listing
- [ ] **M3** Orders and payments: Stripe checkout, webhooks, unpaid-order timeout
- [ ] **M4** Flash sale: atomic stock reservation, purchase limits, load test with zero overselling
- [ ] **M5** Storefront polish, back-office UI and deployment
- [ ] Refresh tokens and login rate limiting
