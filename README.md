# Flow Dent — Dental Supplies Store

Full-stack B2B e-commerce and back-office app for a dental supplies business: a bilingual **English / Arabic (RTL)** storefront, plus an admin panel that tracks clients (clinics and doctors), providers, purchases, returns, stock, expenses and profit. All money is in **Egyptian pounds (EGP)**.

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
| `STRIPE_CURRENCY` | Currency for prices & payments | `egp` |
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
On first start (empty database) the seeder creates the current catalogue: the 13 **Ora** products
(Flow Dent's own brand) in 4 categories, with English and Arabic names and SKUs `ORA-001`…`ORA-013`.
The cost price is the USD import cost from the price list × 52 EGP. The selling price starts equal to
the cost; **Admin → Products** flags these until you set real selling prices. Opening stock is 0: enter
your counted quantities in **Admin → Stock**, or record purchases. It also adds 3 example providers and
4 example clients. Products have no photos yet; add them from the admin panel.

The catalogue is only seeded into an empty database. If you already ran an earlier version, start with a
fresh `ecommerce_db` to get it.

Ora also has its own page at `/ora`, linked from the navbar, the home page and the footer.

## Features

**Storefront**
- English / Arabic switcher (saved in `localStorage` as `flowdent_lang`); `<html dir/lang>` flips the whole layout to RTL.
- Localised product and category names/descriptions.
- Category filtering, sorting, full-text search (English and Arabic fields).
- Cart persisted in the browser, limited by available stock.
- Checkout as guest or signed-in user: home delivery or shop pickup; pay online (Stripe), cash on delivery, or card machine (POS) on delivery.
- Order history and profile with saved addresses (default address pre-fills checkout).

**Operations / back office** (`/admin`, ADMIN role, all amounts in EGP)
- **Clients (buyers):** clinics, doctors, labs and hospitals added by staff (no online account needed). Each client has an account page with orders, payments received, returns and the **balance they owe**. Online orders attach to the matching client automatically (by account, email or phone).
- **Sales entered by staff:** phone / WhatsApp orders with per-line special prices, payment on account (pay later), cash, card machine or bank transfer, and an optional amount received now. Stock is deducted immediately.
- **Providers (sellers):** contacts, purchases, payments made, returns and the **balance you owe** each provider.
- **Purchases:** goods bought from a provider with extra costs (shipping, customs, other). On receipt, stock is added and extra costs are spread over the items by value, so each product's cost price is its true landed cost (weighted average).
- **Returns:** from a client (refund as credit or cash; optionally back into stock) and to a provider (stock removed; credit or cash back).
- **Stock:** quantity, reorder level, quantity on order, cost, margin and stock value per product; quick adjustments, write-offs and stock counts; full history of every stock change (sales, purchases, returns, adjustments).
- **Expenses:** rent, salaries, transport, etc. by month and category.
- **Finance summary:** for any period — sales, returns, cost of goods, gross profit, expenses, write-offs, net profit, money in / out, extra purchase costs, profit by category, a 12-month view, plus what clients owe you, what you owe providers and current stock value.

**Admin panel** (`/admin`)
- Dashboard: revenue, orders, customers, 14-day revenue chart, orders by status, payment mix, top products, low stock, recent orders.
- CRUD for categories and products with image upload (≤ 5 MB, stored in `uploads/`).
- Orders list with status updates. Statuses offered match the delivery method; cancelling restores stock; pay-on-receipt orders are marked paid when delivered / picked up.
- Excel import (`.xlsx`) for categories and products, downloadable templates, and export of current data. Rows are matched by English name (update if it exists, create otherwise).

### How balances and profit are calculated

- **Client owes** = sales (not cancelled) − payments received − returns settled as credit.
- **You owe a provider** = goods received − payments made − returns settled as credit.
- **Net profit** = sales − client returns − cost of goods sold − expenses − write-offs.
  Cost of goods uses each item's cost at the moment of sale; extra purchase costs are already inside it,
  so don't also enter purchase shipping or customs as expenses.

## Tests

Both suites run automatically on GitHub (Actions → **Tests**) for every push and pull request.

### Backend — `cd backend-service && mvn test`
No MySQL needed: tests use an in-memory H2 database (`src/test/resources/application-test.yml`).

| Kind | What is covered |
|---|---|
| Unit (JUnit 5 + Mockito) | Stock ledger (never below zero, weighted-average cost), purchases (extra costs spread into landed cost, receive/cancel rules), orders and staff sales (DB prices, special prices, partial payment, cancel restores stock, COD settles on delivery), returns, client/provider balances, finance figures, JWT |
| Scenario (Spring Boot + MockMvc) | Full B2B cycle through the REST API — buy with shipping/customs → sell on credit → collect → client return → return to provider → expense → stock, balances and profit add up; purchase lifecycle; stock adjustments; guest checkout; roles and security; Excel import |

### Frontend — `cd frontend && npm test` (or `npm run test:ci` for a single run)
Jest + React Testing Library: formatting (EGP), English/Arabic translations, cart and language (RTL) contexts,
product card, login, guest checkout, and the operations screens (clients and payments, sales, purchases,
returns, stock, finance) plus role-based routing.

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
| Clients | `GET/POST /admin/clients`, `GET/PUT/DELETE /admin/clients/{id}`, `POST /admin/clients/sales`, `GET/POST /admin/clients/payments`, `DELETE /admin/clients/payments/{id}` | ADMIN |
| Providers | `GET/POST /admin/suppliers`, `GET/PUT/DELETE /admin/suppliers/{id}`, `GET/POST /admin/suppliers/payments`, `DELETE /admin/suppliers/payments/{id}` | ADMIN |
| Purchases | `GET/POST /admin/purchases`, `GET/PUT /admin/purchases/{id}`, `POST /admin/purchases/{id}/receive`, `POST /admin/purchases/{id}/cancel` | ADMIN |
| Returns | `GET/POST /admin/returns` | ADMIN |
| Stock | `GET /admin/stock`, `GET /admin/stock/movements`, `POST /admin/stock/adjust`, `POST /admin/stock/count` | ADMIN |
| Expenses | `GET/POST /admin/expenses`, `PUT/DELETE /admin/expenses/{id}` | ADMIN |
| Finance | `GET /admin/finance/summary?from=&to=`, `GET /admin/finance/monthly?months=12` | ADMIN |
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
