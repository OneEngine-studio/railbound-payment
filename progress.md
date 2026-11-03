# Railbound Asset Portal — Project Progress Tracker

**Project:** Railbound Battle Royale — Payment & Asset Portal  
**Root Path:** `D:\MyProjects\railbound-asset-portal\`  
**Last updated:** 2026-11-03  
**Design Philosophy:** Minimalist, high-performance, resilient, zero dark patterns  
**Authority Boundary:** MongoDB is the authoritative system of record for Wallets, Orders, Ledger, and Entitlements; Nakama provides authoritative Player Identity.

---

## Executive Status

The portal is functionally complete **for the §16 first-demonstration gates**, and
the payment provider is now a **real PayHere integration**, not a mock. It is
**not** yet a complete implementation of the full `instructions.md` blueprint —
see "Known Scope Gaps vs `instructions.md`" below:

- PayHere Checkout API is the default production provider.
- PayHere `hash` + `md5sig` verification is implemented from the official
  contract (MD5, not HMAC).
- Paid callbacks credit Real Coins exactly once through a MongoDB transaction.
- Duplicate, forged, wrong-amount and wrong-currency callbacks are rejected or
  safely ignored.
- PayHere Merchant API reconciliation and controlled refunds are implemented.
- The mock provider remains available only for local development/tests and is
  disabled by default.
- Backend tests: **18/18 pass**.
- Frontend production build/type-check: **passes**.

**Credential-gated item:** a live PayHere sandbox payment has not been executed
in this environment because no real merchant credentials/public HTTPS callback
URL were available. The signed-callback path is covered by integration tests and
the acceptance script; see “Remaining Work”.

---

## Phase Status Summary

| Phase | Description | Status | Gate Criteria / Evidence |
|---|---|---|---|
| **Phase 0** | Discovery & architecture decisions | **COMPLETED** | Authority boundaries applied: MongoDB wallet/orders/ledger/entitlements; Nakama identity adapter. Architecture and payment decisions documented in code comments, `README.md`, `docs/12`, `docs/13`. |
| **Phase 1** | D-drive setup & foundation | **COMPLETED** | Single-node replica set `rs0` on port 27018 (`local-data\mongo`); Maven compile clean; MongoDB transactions pass. |
| **Phase 2** | Shared authentication & security | **COMPLETED** | Nakama JWT session verification; roles (`PLAYER`, `ADMIN`, `CONTENT_MANAGER`, `FINANCE_SUPPORT`, `SUPER_ADMIN`); IDOR checks; configurable CORS. |
| **Phase 3** | Wallet, ledger & top-up | **COMPLETED** | Real PayHere top-up + dev-only mock; 1000+150 bundle credits exactly 1150 RC once; duplicate callbacks do not re-credit; ledger invariant holds. |
| **Phase 4** | Web portal & Unity top-up UI | **COMPLETED** | Player top-up page, PayHere hosted bridge, checkout-return polling, Unity `PaymentManager.cs` browser hand-off and authoritative wallet refresh. |
| **Phase 5** | Real Coin armory shop | **COMPLETED** | Atomic coin debit (1150 − 450 = 700 for ACOG), entitlement creation, non-stackable uniqueness, optimistic wallet versioning. |
| **Phase 6** | Admin portal & publishing | **PARTIAL** | Item draft CRUD, publish/archive, announcement create/publish and the admin dashboard work. Missing: `/admin/items/{sku}/versions`, `/admin/items/{sku}/validate`, `/admin/uploads/presign`, `/admin/releases*`, `/admin/coin-bundles` and the matching pages. |
| **Phase 7** | Unity runtime content delivery | **PARTIAL (stub backend)** | `AssetBundleManifestHandler.cs` exists in `game-integration/unity`, but `GET /game/releases/latest` returns a hardcoded constant and there are no `item_versions` / `content_releases` collections or upload pipeline behind it. |
| **Phase 8** | Real PayHere sandbox/live provider | **IMPLEMENTED — LIVE VERIFICATION PENDING CREDENTIALS** | Real Checkout + Merchant API adapters; signed callback integration tests pass. Live sandbox payment requires `PAYHERE_MERCHANT_ID`, `PAYHERE_MERCHANT_SECRET`, public HTTPS `PAYMENT_PUBLIC_BASE_URL`, and a PayHere-registered frontend domain. |
| **Phase 9** | Testing, hardening & invariants | **PARTIAL (unit/integration suite only)** | 18/18 JUnit tests pass (8 adapter, 6 PayHere flow, 4 wallet/shop). Forged/duplicate/wrong-amount/wrong-currency cases covered. No load, concurrency-stress, contract, or backup/restore test suite yet. |
| **Phase 10** | DevOps & local infrastructure | **PARTIAL** | Docker Compose with D: volumes; startup/shutdown scripts; Next.js production build passes. PayHere env vars wired into Compose. No CI pipeline (`.github/workflows` absent), no staging/production separation, no monitoring. |

---

## Real PayHere Integration (Phase 8 detail)

### Provider configuration

| Setting | Environment variable | Default |
|---|---|---|
| Default provider | `PAYMENT_DEFAULT_PROVIDER` | `payhere` |
| Public callback origin | `PAYMENT_PUBLIC_BASE_URL` | `http://localhost:8080` (warning; PayHere cannot call localhost) |
| Frontend/bridge origin | `PAYMENT_FRONTEND_BASE_URL` | `http://localhost:3000` |
| Merchant id | `PAYHERE_MERCHANT_ID` | empty |
| Merchant secret | `PAYHERE_MERCHANT_SECRET` | empty |
| Sandbox switch | `PAYHERE_SANDBOX` | `true` |
| Currency | `PAYHERE_CURRENCY` | `USD` |
| Merchant API app id | `PAYHERE_APP_ID` | empty |
| Merchant API app secret | `PAYHERE_APP_SECRET` | empty |
| Mock provider | `APP_PAYMENT_MOCK_ENABLED` | `false` |
| CORS origins | `CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:3000,http://127.0.0.1:3000` |

PayHere is deliberately **fail-closed**: a missing merchant id/secret returns a
clear `503 PAYMENT_NOT_CONFIGURED` instead of silently falling back to mock.

### Checkout flow

1. Player calls `POST /api/v1/payments/checkout-sessions` with only
   `bundleSku`, `provider` and an `Idempotency-Key`.
2. Backend derives the user from the token, reads the active bundle and snapshots
   `coinsToCredit`, `amountMinor` and `currency` into a `PENDING` order.
3. Backend creates a 32-byte opaque checkout token and returns:

   ```text
   {PAYMENT_FRONTEND_BASE_URL}/checkout/hosted?orderId=...&token=...
   ```

4. The Next.js bridge fetches
   `GET /api/v1/payments/hosted/{orderId}?token=...`, which returns the PayHere
   action URL plus server-signed form fields.
5. The bridge auto-submits a normal HTML POST to PayHere from the **frontend
   origin** (required for PayHere's `Referer`/domain validation).

### Signature contracts

```text
checkout hash =
  UPPER(MD5(merchant_id + order_id + amount + currency +
            UPPER(MD5(merchant_secret))))

notify md5sig =
  UPPER(MD5(merchant_id + order_id + payhere_amount + payhere_currency +
            status_code + UPPER(MD5(merchant_secret))))
```

- Amounts are formatted with exactly two decimals from integer minor units.
- Signature comparison is constant-time.
- Merchant id mismatch and invalid `md5sig` return HTTP 401 before any state
  change.
- The merchant secret never leaves the backend.

### Fulfilment and idempotency

A verified paid callback runs one MongoDB transaction:

1. Load the order and confirm the provider matches.
2. Reject if already in a refund lifecycle, cancelled or expired.
3. Validate `payhere_amount` and `payhere_currency` against the immutable order
   snapshot.
4. Set order `COMPLETED`, store `payment_id` and provider status code.
5. Credit the wallet once.
6. Append one immutable `TOPUP` ledger entry keyed by
   `("PAYMENT_ORDER", orderId)`.
7. Insert one `payment_events` row keyed by `(provider, providerEventId)`.

Retries are acknowledged without side effects. Concurrent duplicates are handled
by the unique ledger reference and unique event index. Delayed pending/failed
callbacks cannot regress completed, refunded, cancelled or expired orders.

### PayHere status mapping

| `status_code` | Outcome | Order action |
|---|---|---|
| `2` | PAID | Verify amount/currency, credit once |
| `0` | PENDING | `PROCESSING`; no credit |
| `-1` | CANCELLED | `CANCELLED`; no credit |
| `-2` | FAILED | `FAILED`; no credit |
| `-3` | CHARGEDBACK | `REFUND_PENDING`; no automatic wallet debit |

### Callback endpoint

```text
POST {PAYMENT_PUBLIC_BASE_URL}/api/v1/payments/webhooks/payhere
Content-Type: application/x-www-form-urlencoded
```

The endpoint is public (provider callback) but every request must pass signature
verification. It is CSRF-exempt/stateless and does not require a player JWT.

### Reconciliation and refunds

- `POST /api/v1/admin/payments/{orderId}/reconcile` uses the PayHere Retrieval
  API (OAuth client credentials + Bearer token). It never trusts a
  browser-supplied status.
- `POST /api/v1/admin/refunds` runs a persisted `refund_cases` workflow:
  - provider refund happens outside any DB transaction,
  - coin reversal happens in a transaction only if the wallet still has enough
    coins,
  - spent coins produce `REVIEW_REQUIRED`, never a negative balance.
- `REFUNDED`, `CHARGEBACKED`, `REFUND REQUESTED` and `REFUND PROCESSING`
  retrieval statuses move orders into `REFUND_PENDING` for review.

Finance endpoints are restricted to `ADMIN`, `SUPER_ADMIN` and
`FINANCE_SUPPORT`.

---

## Verification Evidence (2026-11-03)

### Backend tests

```powershell
cd backend
mvn.cmd test
```

Result:

```text
PayHereAdapterTest ............... 8 tests, 0 failures, 0 errors
PayHerePaymentFlowTests .......... 6 tests, 0 failures, 0 errors
PortalApplicationTests ........... 4 tests, 0 failures, 0 errors
TOTAL ............................ 18 tests, 0 failures, 0 errors
BUILD SUCCESS
```

Coverage highlights:

- PayHere checkout hash test vector.
- PayHere `md5sig` test vector for status `2`.
- 1150 RC credited exactly once for `bundle_rc_1000` / USD 9.99.
- Same callback replayed → no second credit.
- Different `payment_id` for the same order → no second credit.
- Forged `md5sig` → rejected, no state change.
- Wrong amount → rejected, no credit.
- Wrong currency → rejected, no credit.
- Pending/cancelled callbacks → no credit.
- Wallet/ledger invariant `sum(deltas) == wallet.realCoins`.

### Frontend build

```powershell
cd frontend
npm.cmd run build
```

Result: `✓ Compiled successfully`, 26/26 static pages generated, including
`/checkout/hosted`, `/checkout/return`, `/admin/orders`, `/admin/refunds`.

### Test hygiene fix

`PortalApplicationTests` previously reused a globally unique ledger reference
(`ref_seed_01`) across Maven runs. That caused a false failure on the second run.
The reference is now unique per test user, so the suite is repeatable against a
persistent MongoDB replica set.

---

## Verified Acceptance Gates (Instructions.md Section 16)

1. [x] **Sign-in via game account:** Nakama-style identity tokens (`/auth/me`,
   `/auth/mock-token`).
2. [x] **Real Coin display:** authoritative balance rendered; Game Coins remain
   deferred/disabled.
3. [x] **1000+150 bundle checkout:** `bundle_rc_1000` (USD 9.99) creates an
   immutable 1150 RC snapshot and PayHere hosted checkout form.
4. [x] **1,150 Real Coins credit:** verified signed callback credits exactly
   1150 and appends one ledger entry.
5. [x] **ACOG 450 coin purchase:** balance becomes exactly 700.
6. [x] **Inventory entitlements:** ACOG entitlement visible in web inventory and
   Unity bridge with server validation.
7. [x] **News synchronization:** published announcements appear in the web feed
   and `GET /game/sync`.
8. [x] **Edge-case rejections:** duplicate callbacks ignored; forged signatures
   return 401; duplicate item purchases return 409; insufficient funds return
   400; wrong amount/currency callbacks rejected.

---

## Local Development & Service Endpoints

- **MongoDB replica set (`rs0`):**
  `mongodb://127.0.0.1:27018/railbound_portal?replicaSet=rs0`
- **MongoDB data volume:** `D:\MyProjects\railbound-asset-portal\local-data\mongo`
- **Spring Boot backend:** `http://localhost:8080/api/v1`
- **OpenAPI / Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Next.js portal:** `http://localhost:3000`
- **Admin console:** `http://localhost:3000/admin`
- **PayHere sandbox checkout:** `https://sandbox.payhere.lk/pay/checkout`
- **PayHere live checkout:** `https://www.payhere.lk/pay/checkout`
- **PayHere callback:** `{PAYMENT_PUBLIC_BASE_URL}/api/v1/payments/webhooks/payhere`

### Useful commands

```powershell
# Backend tests (MongoDB rs0 must be running)
cd backend
mvn.cmd test

# Run backend
mvn.cmd spring-boot:run

# Frontend dev/build
cd ..\frontend
npm.cmd run dev
npm.cmd run build

# Signed PayHere callback acceptance
$env:PAYHERE_MERCHANT_ID="<id>"
$env:PAYHERE_MERCHANT_SECRET="<secret>"
.\tests\e2e-acceptance-check.ps1

# Mock-provider acceptance (dev only)
.\tests\e2e-acceptance-check.ps1 -UseMock
```

---

## Documentation

- `README.md` — setup, architecture, testing, payment safety model.
- `docs/13-payhere-integration.md` — PayHere configuration, sandbox steps,
  hash/`md5sig`, callback contract, go-live checklist, troubleshooting.
- `docs/12-refunds-reconciliation.md` — refund/chargeback lifecycle and Merchant
  API reconciliation.
- `instructions.md` — original product/technical blueprint.
- `progress.md` — this file.

**Documentation gap:** the original blueprint also lists `01`–`11` documents
(requirements, architecture, data model, OpenAPI YAML, sequence diagrams, threat
model, full test plan, deployment runbook, ADRs). Those are not present as
standalone files in this checkout; the implemented behavior is documented in the
README, the two operational docs above, code comments, and the live Swagger/API
surface.

---

## Known Scope Gaps vs `instructions.md`

The core player journeys (§16) work end-to-end, but these blueprint items are not
implemented or only stubbed:

| Area | Gap |
|---|---|
| Data model (§4) | No `item_versions`, `content_releases`, `notifications`, `outbox_events` or `feature_flags` collections. `ShopItem.currentContentVersion` is a single string, not a versioned history. |
| Outbox / notifications | Documented as the delivery path but no outbox worker, no Nakama notification publisher, no `notifications` API (`/players/me/notifications`). |
| Admin content pipeline (§7) | Missing item-version registration, compatibility validation, upload presign (S3/MinIO), release approve/publish, coin-bundle admin and the `/admin/releases`, `/admin/coin-bundles` pages. |
| Game integration (§9) | `GET /game/releases/latest` is a hardcoded stub; no signed manifest, checksum registry or client content delivery. `GET /game/config` is static. |
| Identity (§5) | Uses a portal JWT issuing `/auth/mock-token`; real Nakama token verification/refresh is not wired. No CSRF protection or rate limiting on auth/checkout/support. |
| Testing (§12) | No load, concurrency-stress, contract, upload-abuse or backup-restore tests; acceptance coverage is the 18 JUnit tests plus the PowerShell script. |
| DevOps (§14) | No GitHub Actions, no staging/production split, no secret manager, no metrics/alerting, no automated backups. |
| Documentation (§15) | `docs/01`–`11` and `docs/adr/` are absent (see note above). |

---

## Remaining Work / Credential-Gated Steps

These require real PayHere merchant credentials and a public deployment; they
cannot be completed from this local environment:

1. **Live/sandbox E2E payment**
   - Set real `PAYHERE_MERCHANT_ID` and `PAYHERE_MERCHANT_SECRET`.
   - Register the frontend domain in PayHere.
   - Expose the backend at a public HTTPS URL and set
     `PAYMENT_PUBLIC_BASE_URL`.
   - Complete a sandbox card payment and verify one 1150 RC credit.
2. **Merchant API live check**
   - Set `PAYHERE_APP_ID` / `PAYHERE_APP_SECRET`.
   - Whitelist the backend domain/IP in PayHere.
   - Run one reconciliation and one sandbox refund.
3. **Production hardening**
   - Put credentials in a secret manager.
   - Confirm PayHere live IP/domain whitelisting.
   - Enable monitoring/alerts for stale `PROCESSING` orders and callback
     failures.
   - Schedule periodic reconciliation.
4. **Optional:** add the remaining formal documentation files (`01`–`11`) and a
   partial-refund admin flow.

---

## Security Notes

- No raw card data is handled by the portal; PayHere hosts the payment page.
- No payment credentials are stored in the frontend, Unity, MongoDB, logs, or
  Git.
- The checkout token is opaque and required by the hosted bridge; it is not an
  authentication token and cannot authorize wallet changes.
- Webhook processing is provider-signed; a browser redirect alone can never
  credit coins.
- Admin/finance endpoints are role-restricted; refunds are auditable.
- CORS origins are environment-configurable; do not use `*` with credentials in
  production.
- `.env.example` contains placeholders only; real merchant credentials stay out of
  Git.

## Repository Hygiene

- A root `.gitignore` covers `.env*` (except examples), `node_modules/`, `.next/`,
  `target/`, `local-data/`, `local-logs/`, Unity build output, logs and
  certificates.
- Build output (`backend/target`, `frontend/.next`, `local-logs`) was previously
  tracked and has been removed from the index with `git rm -r --cached`; the files
  stay on disk. Commit that cleanup once so the ignore rules take effect for
  everyone.

