# Railbound Asset Portal

Player web portal + admin console + payment backend for **Railbound Battle
Royale**. Real Coins are paid currency; MongoDB is the authoritative system of
record for wallets, orders, ledger entries and entitlements. Nakama remains the
authoritative player identity source.

## Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 14, TypeScript, Tailwind CSS |
| Backend | Java 22, Spring Boot 3.3, Spring Security |
| Database | MongoDB 7 single-node replica set (`rs0`) |
| Payments | **PayHere Checkout API + Merchant API** (real integration) |
| Game integration | Unity C# + Nakama REST adapter contracts |

## Repository layout

```text
backend/            Spring Boot API, payments, wallet, ledger, refunds
frontend/           Next.js player + admin routes
game-integration/   Unity C# and Nakama adapter contracts
infrastructure/     Docker Compose and scripts
tests/              Acceptance/E2E PowerShell checks
docs/               Payment, refund and operations documentation
local-data/         MongoDB data (not committed in a clean checkout)
local-logs/         Backend/MongoDB logs
```

## Prerequisites

- Java 22+
- Maven 3.9+
- Node.js 20+ and npm
- MongoDB 7 (`mongod`) with a replica set; a single-node `rs0` is enough
- A PayHere merchant account for real payments (sandbox or live)

## Local setup

### 1. Environment

```powershell
Copy-Item .env.example .env
```

Set at least:

```dotenv
SPRING_DATA_MONGODB_URI=mongodb://127.0.0.1:27018/railbound_portal?replicaSet=rs0
PAYHERE_MERCHANT_ID=
PAYHERE_MERCHANT_SECRET=
PAYHERE_SANDBOX=true
PAYMENT_PUBLIC_BASE_URL=http://localhost:8080
PAYMENT_FRONTEND_BASE_URL=http://localhost:3000
APP_PAYMENT_MOCK_ENABLED=false
```

See `docs/13-payhere-integration.md` for the complete PayHere setup.

### 2. MongoDB replica set

```powershell
# Terminal 1 (adjust the mongod path for your installation)
mongod --replSet rs0 --port 27018 --dbpath D:\MyProjects\railbound-asset-portal\local-data\mongo

# Terminal 2 (once)
mongosh --port 27018 --eval "rs.initiate({_id:'rs0',members:[{_id:0,host:'127.0.0.1:27018'}]})"
```

The backend refuses to rely on transactions unless `rs0` is primary.

### 3. Backend

```powershell
cd backend
mvn.cmd spring-boot:run
```

API: <http://localhost:8080/api/v1>  
Swagger UI: <http://localhost:8080/swagger-ui.html>

### 4. Frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Portal: <http://localhost:3000>

## Testing

```powershell
# Backend unit + integration tests (MongoDB must be running)
cd backend
mvn.cmd test

# Frontend production build + type check
cd ..\frontend
npm.cmd run build

# Signed PayHere callback acceptance (uses backend credentials)
$env:PAYHERE_MERCHANT_ID="<id>"
$env:PAYHERE_MERCHANT_SECRET="<secret>"
.\tests\e2e-acceptance-check.ps1
```

The acceptance script can also use the development mock provider:

```powershell
.\tests\e2e-acceptance-check.ps1 -UseMock
```

Mock payments require `APP_PAYMENT_MOCK_ENABLED=true` and must never be enabled
in production.

## Payment security model

- Client submits only `bundleSku`, provider and an idempotency key.
- Prices and coin amounts are snapshotted from MongoDB; no client price is
  trusted.
- PayHere `hash` and `md5sig` are MD5-based per PayHere's official contract,
  computed server-side only.
- Callbacks are form-encoded; signature, merchant id, amount and currency are
  verified before any state change.
- Fulfilment is a single MongoDB transaction: order `COMPLETED`, one wallet
  credit, one ledger entry, one deduplicated payment event.
- Redirects never grant coins; success is shown only after server confirmation.
- Refunds/chargebacks route to an auditable `refund_cases` workflow and never
  create negative balances.

## Documentation

- `docs/13-payhere-integration.md` — PayHere setup, sandbox, hashes, callback,
  go-live checklist and troubleshooting.
- `docs/12-refunds-reconciliation.md` — refund/chargeback workflow and Merchant
  API reconciliation.
- `instructions.md` — full product/technical blueprint.
- `progress.md` — implementation status and verified gates.

## Docker (optional)

```powershell
cd infrastructure\docker
docker compose up --build
```

PayHere still needs a public HTTPS callback URL; do not leave
`PAYMENT_PUBLIC_BASE_URL` at `localhost` for real payments.
