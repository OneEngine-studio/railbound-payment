# PayHere Integration Runbook

**Provider:** PayHere (Sri Lanka) — Checkout API + Merchant API
**Backend:** Spring Boot (`backend/`)
**Frontend bridge:** Next.js (`frontend/pages/checkout/hosted.tsx`)
**Status:** Implemented; live/sandbox acceptance still requires real merchant credentials and a public HTTPS callback URL.

PayHere is the default payment provider (`PAYMENT_DEFAULT_PROVIDER=payhere`). The
mock provider exists only for local development/tests and is disabled unless
`APP_PAYMENT_MOCK_ENABLED=true`.

---

## 1. Why PayHere is not HMAC

PayHere uses two MD5 digests, not an HMAC header:

```text
checkout hash =
  UPPER(MD5(merchant_id + order_id + amount + currency +
            UPPER(MD5(merchant_secret))))

notify md5sig =
  UPPER(MD5(merchant_id + order_id + payhere_amount + payhere_currency +
            status_code + UPPER(MD5(merchant_secret))))
```

Important details:

- `amount` must be formatted with exactly two decimals (`9.99`, `1000.00`).
- `merchant_secret` is domain-specific in PayHere. A new domain/app gets a new
  secret from **Portal → Integrations → Add Domain/App**.
- The secret never reaches the browser, Unity, MongoDB, or logs. The backend
  computes `hash` and returns the signed form fields to the bridge page.
- The callback digest is computed from the **exact** posted values, especially
  `payhere_amount` and `payhere_currency`.

Implementation: `backend/src/main/java/com/railbound/portal/payments/PayHereAdapter.java`.

---

## 2. End-to-end flow

```text
Player (web/Unity)
   |
   | POST /api/v1/payments/checkout-sessions
   | { "bundleSku": "bundle_rc_1000", "provider": "payhere" }
   v
Spring Boot
   - authenticates the player from the bearer token
   - reads the trusted bundle from MongoDB
   - snapshots coinsToCredit / amountMinor / currency on the order
   - creates a PENDING order and an opaque checkout token
   - returns checkoutUrl = FRONTEND/checkout/hosted?orderId=...&token=...
   |
   v
Next.js bridge (/checkout/hosted)
   - GET /api/v1/payments/hosted/{orderId}?token=...
   - receives PayHere action URL + signed form fields
   - auto-submits a normal HTML POST to PayHere
   |
   v
PayHere hosted checkout (PCI-DSS scope)
   - player enters card details on PayHere
   - PayHere redirects the browser to return_url/cancel_url
   - PayHere POSTs the authoritative result to notify_url
   |
   v
Spring Boot webhook (/api/v1/payments/webhooks/payhere)
   - verifies merchant_id and md5sig (constant-time compare)
   - validates amount + currency against the immutable order snapshot
   - in one MongoDB transaction:
       order -> COMPLETED
       wallet += coinsToCredit
       one immutable TOPUP ledger entry
       one deduplicated payment_event
   - duplicate/retried callbacks are acknowledged without re-crediting
   |
   v
Frontend /checkout/return
   - polls GET /api/v1/payments/orders/{orderId}
   - shows success only after the server reports COMPLETED
```

**A redirect never grants coins.** Only a verified callback or an authenticated
PayHere Retrieval API lookup can fulfil an order.

### Why the bridge page lives on the frontend origin

PayHere validates the browser `Referer` against the registered merchant domain.
If the auto-submit form were served from the API origin, production checkout
would fail with **Unauthorized Payment Request**. The backend therefore returns
a token-gated bridge URL on `PAYMENT_FRONTEND_BASE_URL`, and the browser posts
the signed form from that origin.

Register the **frontend** origin (not the API origin) as the PayHere domain/app.

---

## 3. Required configuration

Copy `.env.example` to `.env` and set real values outside Git.

| Variable | Purpose | Example |
|---|---|---|
| `PAYMENT_DEFAULT_PROVIDER` | Default provider | `payhere` |
| `PAYMENT_PUBLIC_BASE_URL` | Public HTTPS origin PayHere can reach | `https://pay.example.com` |
| `PAYMENT_FRONTEND_BASE_URL` | Public frontend origin | `https://portal.example.com` |
| `PAYHERE_MERCHANT_ID` | Checkout merchant id | from Portal → Integrations |
| `PAYHERE_MERCHANT_SECRET` | Domain-specific checkout secret | from Portal → Integrations |
| `PAYHERE_SANDBOX` | `true` for sandbox, `false` for live | `true` |
| `PAYHERE_CURRENCY` | Order currency (`USD` or `LKR`) | `USD` |
| `PAYHERE_APP_ID` | Merchant API app id (reconciliation/refunds) | from Settings → API Keys |
| `PAYHERE_APP_SECRET` | Merchant API app secret | from Settings → API Keys |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | Browser origins allowed to call the API | `https://portal.example.com` |
| `APP_PAYMENT_MOCK_ENABLED` | Mock provider (dev only) | `false` |

The callback URL is derived automatically:

```text
{PAYMENT_PUBLIC_BASE_URL}/api/v1/payments/webhooks/payhere
```

It must be publicly reachable over HTTPS. **PayHere cannot call localhost.**
For local testing use a tunnel (Cloudflare Tunnel, ngrok, etc.) and set
`PAYMENT_PUBLIC_BASE_URL` to the tunnel's HTTPS URL.

> If `PAYMENT_PUBLIC_BASE_URL` still contains `localhost`/`127.0.0.1`, the
> backend logs a startup warning. Checkout creation will still work locally,
> but PayHere will not deliver the callback.

---

## 4. Sandbox setup checklist

1. Create/log in to a PayHere sandbox merchant account.
2. Open **Integrations → Add Domain/App**.
3. Add the frontend origin (for local tunnels, the tunnel hostname).
4. Copy the **Merchant ID** and the **Merchant Secret** shown for that domain.
5. Put them in `.env`:
   ```dotenv
   PAYHERE_MERCHANT_ID=...
   PAYHERE_MERCHANT_SECRET=...
   PAYHERE_SANDBOX=true
   PAYHERE_CURRENCY=USD
   PAYMENT_PUBLIC_BASE_URL=https://your-tunnel.example.com
   PAYMENT_FRONTEND_BASE_URL=https://your-tunnel.example.com
   CORS_ALLOWED_ORIGIN_PATTERNS=https://your-tunnel.example.com
   ```
6. Start MongoDB, the backend and the frontend.
7. Sign in as a player, open `/top-up`, choose `bundle_rc_1000`.
8. Confirm the bridge page redirects to `sandbox.payhere.lk`.
9. Pay with a PayHere sandbox test card.
10. Confirm:
    - the browser returns to `/checkout/return?orderId=...`,
    - the order becomes `COMPLETED`,
    - the wallet increases by exactly 1150 RC,
    - exactly one `TOPUP` ledger entry exists,
    - replaying the callback does not credit again.

PayHere sandbox test cards are documented at
<https://support.payhere.lk/sandbox-and-testing>.

### Merchant API (optional but recommended)

Reconciliation and refunds need a separate API key:

1. **Settings → API Keys → Create API Key**.
2. Tick **Automated Charging API** and whitelist the backend domain.
3. Copy **App ID** / **App Secret** into `PAYHERE_APP_ID` / `PAYHERE_APP_SECRET`.
4. For sandbox, use `*` as the allowed domain if domain validation causes
   `Access denied for the domain`.
5. For live, PayHere may require server-IP whitelisting. Contact PayHere support
   with the production egress IP.

These are **not** the Checkout merchant id/secret.

---

## 5. Callback contract

PayHere sends `application/x-www-form-urlencoded` POST parameters:

| Field | Use |
|---|---|
| `merchant_id` | Must match the configured merchant id |
| `order_id` | Internal `ord_...` order reference |
| `payment_id` | PayHere payment reference |
| `payhere_amount` | Amount; part of `md5sig` |
| `payhere_currency` | Currency; part of `md5sig` |
| `status_code` | `2`, `0`, `-1`, `-2`, `-3` |
| `md5sig` | Signature to verify |
| `method` | Optional payment method |
| `custom_1`, `custom_2` | Echoed user id / bundle sku for support |

Status mapping:

| `status_code` | Meaning | Order behavior |
|---|---|---|
| `2` | Success | Verify amount/currency, credit exactly once |
| `0` | Pending | `PROCESSING`; no credit |
| `-1` | Cancelled | `CANCELLED`; no credit |
| `-2` | Failed | `FAILED`; no credit |
| `-3` | Chargeback | `REFUND_PENDING`; never auto-reverse the wallet |

Security rules enforced in code:

- Merchant id mismatch → `401`.
- Invalid `md5sig` → `401`, no state change.
- Unknown order → `404`.
- Wrong amount or currency → `400`, no credit.
- Duplicate event id → acknowledged, no second credit.
- Unique `(provider, providerEventId)` and unique ledger reference constrain
  concurrent retries.
- A terminal `CANCELLED`/`EXPIRED` order is not regressed by a delayed
  pending/failed callback.

---

## 6. Reconciliation

Admins/finance users can reconcile a PayHere order:

```http
POST /api/v1/admin/payments/{orderId}/reconcile
Authorization: Bearer <ADMIN|SUPER_ADMIN|FINANCE_SUPPORT token>
```

The backend calls PayHere's **Retrieval API**:

```http
GET {sandbox|live}/merchant/v1/payment/search?order_id={orderId}
Authorization: Bearer <merchant access token>
```

It never trusts a browser-supplied status. A `RECEIVED` result runs the same
verified fulfilment path as the webhook. `REFUNDED`, `CHARGEBACKED`,
`REFUND REQUESTED`, and `REFUND PROCESSING` move the order into the refund
review lifecycle.

OAuth tokens are cached until shortly before expiry and refreshed once on
`401`. All Merchant API calls happen outside MongoDB transactions.

---

## 7. Refunds

See `docs/12-refunds-reconciliation.md`. Summary:

- Refunds use PayHere's Refund API with `payment_id` (full refund) and are
  restricted to `ADMIN`, `SUPER_ADMIN`, `FINANCE_SUPPORT`.
- A `refund_cases` document is persisted before the provider call.
- Provider refund and coin reversal are separate steps.
- If the player already spent the coins, the case becomes `REVIEW_REQUIRED`
  instead of creating a negative wallet.
- Successful reversals append an immutable `REFUND` ledger entry.

---

## 8. Local test commands

```powershell
# Backend unit + integration tests (needs MongoDB rs0 on port 27018)
cd backend
mvn.cmd test

# Frontend type-check + production build
cd ..\frontend
npm.cmd run build

# Signed callback acceptance script (no PayHere network access needed)
$env:PAYHERE_MERCHANT_ID="<merchant id>"
$env:PAYHERE_MERCHANT_SECRET="<merchant secret>"
.\tests\e2e-acceptance-check.ps1
```

The test suite covers:

- checkout hash and `md5sig` test vectors,
- 1150 RC credit exactly once,
- duplicate callback replay,
- forged signature rejection,
- wrong amount/currency rejection,
- pending/cancelled callbacks never crediting,
- wallet/ledger invariant checks.

---

## 9. Go-live checklist

- [ ] Live PayHere merchant account approved.
- [ ] Production domain added in PayHere; live merchant secret copied.
- [ ] `PAYHERE_SANDBOX=false`.
- [ ] `PAYMENT_PUBLIC_BASE_URL` is public HTTPS.
- [ ] `PAYMENT_FRONTEND_BASE_URL` matches the PayHere-registered domain.
- [ ] `CORS_ALLOWED_ORIGIN_PATTERNS` contains the production portal origin.
- [ ] Checkout credentials and Merchant API credentials are in a secret manager.
- [ ] `APP_PAYMENT_MOCK_ENABLED=false`.
- [ ] Callback URL reachable from the public internet (test with a real live
      low-value payment or PayHere's test tooling).
- [ ] Merchant API server IP whitelisted (if required by PayHere).
- [ ] Refund/support runbook and finance roles verified.
- [ ] Monitoring/alerts enabled for callback failures and stale `PROCESSING`
      orders.
- [ ] Reconciliation job/runbook scheduled.
- [ ] Legal/refund/age disclosures reviewed for the target market.

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `PAYMENT_NOT_CONFIGURED` / HTTP 503 | Missing `PAYHERE_MERCHANT_ID`/`SECRET` | Set both, restart backend |
| `Unauthorized Payment Request` on PayHere | Hash/amount mismatch, wrong domain, or suppressed `Referer` | Recheck formatted amount, registered frontend origin, `Referrer-Policy` |
| `PH-0022` | Wrong live action URL | Must be `https://www.payhere.lk/pay/checkout` |
| No callback / order stays `PROCESSING` | `notify_url` is localhost or blocked | Use a public HTTPS tunnel/domain |
| `md5sig verification failed` | Wrong merchant secret (domain-specific) or modified callback data | Use the secret for the registered domain; do not alter posted fields |
| Callback credits but redirect shows pending | Callback arrived after the return page loaded | Frontend polls; refresh `/checkout/return?orderId=...` |
| Reconciliation returns `PAYMENT_PROVIDER_ERROR` | Missing/invalid Merchant API key, domain/IP restriction | Configure `PAYHERE_APP_ID`/`SECRET`, whitelist domain/IP |
| Refund returns `REVIEW_REQUIRED` | Player already spent the coins | Review entitlements manually; wallet must not go negative |

---

## 11. Known limitations

- No live PayHere merchant credentials are stored in this repository.
- PayHere cannot call `localhost`; real E2E requires a public HTTPS URL.
- The portal collects no phone number yet; PayHere receives a syntactically
  valid placeholder phone for pre-fill.
- Partial refunds are not exposed in the admin UI/API (full refunds only).
- Refund coin reversal is intentionally conservative: spent coins route to
  support review instead of creating negative balances.
