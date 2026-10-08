# Flowdent — Dental Supplies Store

Full-stack e-commerce app for dental supplies with a bilingual **English / Arabic (RTL)** storefront and an admin panel.

| Module | Tech | Port |
|---|---|---|
| `eureka-server` | Spring Cloud Netflix Eureka | 8761 |
| `api-gateway` | Spring Cloud Gateway (routes `/api/**` → `lb://backend-service`) | 8080 |
| `backend-service` | Spring Boot 3.2.3 REST API · JPA/MySQL · JWT · Stripe · Apache POI | 8081 |
| `frontend` | React 19 (Create React App) · react-router 7 · Stripe Elements | 3000 |

```
browser :3000 ──(CRA proxy)──► api-gateway :8080 ──(Eureka lookup)──► backend-service :8081 ──► MySQL
                                     └──────────── eureka-server :8761 ◄────────────┘
```

## Prerequisites

- Java 17+ and Maven 3.9+
- Node.js 18+ and npm
- MySQL 8 running locally. The default datasource is `root` / `root` on `localhost:3306`;
  the `ecommerce_db` database is created automatically (`createDatabaseIfNotExist=true`)
  and tables are managed by Hibernate (`ddl-auto=update`).

## Configuration

Secrets are read from environment variables; the defaults in `application.yml` are safe placeholders.
See [`.env.example`](.env.example) for the full list.

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection | local `ecommerce_db`, `root`/`root` |
| `JWT_SECRET` | HS256 signing secret (≥ 32 chars) | dev placeholder |
| `JWT_EXPIRATION` | Token lifetime in ms | `86400000` (24h) |
| `STRIPE_SECRET_KEY` | Stripe **test** secret key | `sk_test_placeholder` |
| `STRIPE_PUBLISHABLE_KEY` | Stripe **test** publishable key | `pk_test_placeholder` |
| `STRIPE_CURRENCY` | Currency for prices & payments | `usd` |
| `UPLOAD_DIR` | Folder for uploaded product images | `uploads` |

With the placeholder Stripe keys the app runs normally; the "Pay online by card" option is simply
disabled at checkout. Set real **test** keys to enable it (test card `4242 4242 4242 4242`).

## Run locally

Start the services in this order, each in its own terminal:

```bash
# 1) Service discovery
cd eureka-server && mvn spring-boot:run

# 2) Backend API (export env vars first if you have them)
cd backend-service && mvn spring-boot:run

# 3) Gateway
cd api-gateway && mvn spring-boot:run

# 4) Frontend
cd frontend && npm install && npm start
```

Open http://localhost:3000. The Eureka dashboard is at http://localhost:8761.
Give the gateway ~30 seconds after the backend registers before the first request.

## Seeded accounts

| Login | Password | Role | Access |
|---|---|---|---|
| `admin` | `admin` | ADMIN | Full admin panel |
| `orderadmin` | `orderadmin` | ORDER_ADMIN | Dashboard + orders only |

**Change these passwords before any non-local deployment.**
On first start the seeder also creates 8 dental categories and 31 products with English and Arabic
names/descriptions. Seed images are Unsplash URLs; if any fail to load, a placeholder is shown and
you can replace them from the admin panel.

## Features

**Storefront**
- English / Arabic switcher (saved in `localStorage` as `flowdent_lang`); `<html dir/lang>` flips the whole layout to RTL.
- Localised product and category names/descriptions.
- Category filtering, sorting, full-text search (English and Arabic fields).
- Cart persisted in the browser, limited by available stock.
- Checkout as guest or signed-in user: home delivery or shop pickup; pay online (Stripe), cash on delivery, or card machine (POS) on delivery.
- Order history and profile with saved addresses (default address pre-fills checkout).

**Admin panel** (`/admin`)
- Dashboard: revenue, orders, customers, 14-day revenue chart, orders by status, payment mix, top products, low stock, recent orders.
- CRUD for categories and products with image upload (≤ 5 MB, stored in `uploads/`).
- Orders list with status updates. Statuses offered match the delivery method; cancelling restores stock; pay-on-receipt orders are marked paid when delivered / picked up.
- Customers list with order count and total spent.
- Excel import (`.xlsx`) for categories and products, downloadable templates, and export of current data. Rows are matched by English name (update if it exists, create otherwise).

## REST API (all under `/api`)

| Area | Endpoints | Access |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | public (`/me` needs token) |
| Products | `GET /products`, `GET /products/{id}`, `GET /products/category/{id}`, `GET /products/search?q=` | public |
| | `POST /products`, `PUT /products/{id}`, `DELETE /products/{id}` | ADMIN |
| Categories | `GET /categories`, `GET /categories/{id}` | public |
| | `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}` | ADMIN |
| Orders | `POST /orders`, `GET /orders/my`, `GET /orders/{id}` | authenticated |
| | `POST /orders/guest` | public |
| Profile | `GET/PUT /profile`, `GET/POST /profile/addresses`, `PUT/DELETE /profile/addresses/{id}` | authenticated |
| Payment | `GET /payment/config`, `POST /payment/create-intent`, `POST /payment/confirm` | public |
| Images | `POST /images/upload` (ADMIN), `GET /images/{filename}` (public) | |
| Admin | `GET /admin/dashboard`, `GET /admin/orders`, `PUT /admin/orders/{id}/status` | ADMIN, ORDER_ADMIN |
| | `GET /admin/customers` | ADMIN |
| Excel | `POST /admin/import/products`, `POST /admin/import/categories`, `GET /admin/import/template/{products,categories}`, `GET /admin/import/export/{products,categories}` | ADMIN |

### Online payment flow

1. Client creates the order (`paymentMethod = ONLINE_PAYMENT`) — prices and totals are always computed on the server.
2. `POST /payment/create-intent { orderId }` creates a Stripe PaymentIntent for the stored order total.
3. Stripe Elements confirms the card in the browser.
4. `POST /payment/confirm { orderId, paymentIntentId }` re-checks the intent with Stripe and marks the order paid and `CONFIRMED`.

## Project layout

```
flowdent/
├── eureka-server/        Eureka registry
├── api-gateway/          Spring Cloud Gateway + CORS
├── backend-service/
│   └── src/main/java/com/flowdent/backend/
│       ├── config/       DataSeeder
│       ├── controller/   REST controllers
│       ├── dto/          Request/response records
│       ├── exception/    ApiException + global handler
│       ├── model/        JPA entities & enums
│       ├── repository/   Spring Data repositories
│       ├── security/     JWT util/filter, security config
│       └── service/      Order and Excel services
└── frontend/
    └── src/
        ├── context/      LangContext, AuthContext, CartContext
        ├── i18n/         en.js, ar.js
        ├── components/   Navbar, AdminLayout, Modal, StripePaymentForm, …
        └── pages/        storefront pages + admin/
```
