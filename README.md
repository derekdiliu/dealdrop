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
        ├── catalog      deals, products, images (S3)
        ├── order        cart, orders, outbox, unpaid-order timeout
        ├── payment      Stripe payments, webhooks, refunds
        └── flash-sale   atomic stock reservation (Redis + Lua), purchase limits, async order creation

PostgreSQL (database per service) · Redis · RabbitMQ
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
- **Data:** PostgreSQL 17, Redis 7.4, RabbitMQ 4.1
- **Frontend:** Next.js 16, TypeScript, Tailwind CSS
- **CI:** GitHub Actions

## New Zealand specifics

- Prices are stored as integer cents in NZD and displayed GST-inclusive (15%).
- Deal start and end times use the `Pacific/Auckland` time zone, including daylight saving.
- NZ address format (street, suburb, city, postcode) with North Island, South Island and rural delivery shipping zones.

## Getting started

Prerequisites: JDK 21, Node.js 22, Docker.

```bash
# Infrastructure: Postgres, Redis, RabbitMQ
docker compose up -d --wait

# Backend
./mvnw verify
java -jar services/gateway/target/gateway-0.1.0-SNAPSHOT.jar   # or run any service from your IDE

# Frontend
cd frontend
npm install
npm run dev
```

RabbitMQ management UI: http://localhost:15672 (dealdrop / dealdrop).

## Roadmap

- [x] **M0** Project skeleton, local infrastructure, CI
- [ ] **M1** Identity and gateway: registration, login, JWT validation
- [ ] **M2** Catalog: deal management, image upload, storefront listing
- [ ] **M3** Orders and payments: Stripe checkout, webhooks, unpaid-order timeout
- [ ] **M4** Flash sale: atomic stock reservation, purchase limits, load test with zero overselling
- [ ] **M5** Storefront polish and deployment
