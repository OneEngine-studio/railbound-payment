# PayHere payment gateway - setup and operations

This project accepts real-money top-ups through **PayHere** (https://www.payhere.lk). It works in two
modes that share the same server code:

| Mode | Use | Money moves? |
|---|---|---|
| `mock` | Local development without any account (default) | No |
| `payhere` + `PAYHERE_SANDBOX=true` | Testing against PayHere's sandbox | No (test cards) |
| `payhere` + `PAYHERE_SANDBOX=false` | Production | **Yes** |

## How a payment flows

1. The player picks a bundle on `/top-up`. The browser calls `POST /api/v1/payments/checkout-sessions`.
   The server creates an order with a **price/coin snapshot taken from the bundle in the database**
   (the client cannot change the price) and returns a `checkoutUrl`.
2. The player lands on `/checkout/pay`, enters billing details (PayHere requires them; they are passed
   through and **not stored**), and the server returns the PayHere form fields **including the `hash`
   it computed with your merchant secret**.
3. The browser POSTs the form to PayHere's hosted page. Card details are entered **on PayHere**, never on
   Railbound.
4. PayHere calls your **`notify_url`** (`POST /api/v1/payments/webhooks/payhere`) with the result and an
   `md5sig`. The server verifies `md5sig`, `merchant_id`, **amount and currency against the order**, and
   only then credits the coins - exactly once.
5. PayHere redirects the browser to `/checkout/return`, which polls the order. The redirect itself never
   grants coins.

Every step of the money path is idempotent (duplicate notifications are ignored) and every coin movement
has an immutable ledger entry.

## 1. Create PayHere accounts

1. Register a merchant account at https://www.payhere.lk and complete PayHere's business verification
   (you need a registered business and a Sri Lankan bank account for payouts - see PayHere's own
   requirements; this can take days).
2. Register a **sandbox** account at https://sandbox.payhere.lk for testing.
3. In the sandbox merchant dashboard open **Settings > Domains & Credentials**, add the domain you will
   test from, and note the **Merchant ID** and **Merchant Secret** shown for it. (Check PayHere's current
   rules for `localhost` / tunnel domains; if `localhost` is rejected, register your tunnel domain.)

Keep the Merchant Secret private. Never commit it or paste it into the frontend.

## 2. Make `notify_url` reachable (development)

PayHere's servers must be able to call your backend, so `localhost` will not work for the notification.
Use a tunnel, for example:

```powershell
ngrok http 8080
```

Copy the HTTPS forwarding URL, e.g. `https://abcd-1234.ngrok-free.app`. Your notify URL is then
`https://abcd-1234.ngrok-free.app/api/v1/payments/webhooks/payhere`.

(No tunnel? Skip to "Test without a tunnel" below.)

## 3. Start the backend with PayHere enabled

In the PowerShell window where you start the backend:

```powershell
cd D:\MyProjects\railbound-asset-portal\backend
$env:PAYHERE_ENABLED         = "true"
$env:PAYHERE_SANDBOX         = "true"
$env:PAYHERE_MERCHANT_ID     = "<sandbox merchant id>"
$env:PAYHERE_MERCHANT_SECRET = "<sandbox merchant secret>"
$env:PAYHERE_NOTIFY_URL      = "https://abcd-1234.ngrok-free.app/api/v1/payments/webhooks/payhere"
$env:PAYMENT_DEFAULT_PROVIDER = "payhere"
mvn spring-boot:run
```

If you start everything with `infrastructure\scripts\start-all.ps1`, set the same `$env:` variables in
that PowerShell window first; the backend inherits them.

PayHere stays **disabled** unless merchant id, secret and notify URL are all set (the backend logs a
warning if you enable it half-configured).

The frontend needs no PayHere settings. `GET /api/v1/payments/providers` tells it which providers are
live; with both enabled the top-up page shows a "Pay with" selector.

## 4. Test with the sandbox

1. Sign in, open **Top-up**, choose a bundle, fill in the billing form, continue to PayHere.
2. Pay with a **sandbox test card** from PayHere's sandbox documentation (use the numbers listed
   there - do not use a real card in sandbox).
3. You should be sent back to `/checkout/return`, which turns green once PayHere's notification has been
   verified, and the wallet shows the coins.
4. Check **Admin > Audit** for `PAYMENT_COMPLETED`.

### Test without a tunnel

You can simulate PayHere's notification locally. Start a PayHere top-up, copy the `orderId` from the
`/checkout/pay?orderId=...` URL, and run:

```powershell
cd D:\MyProjects\railbound-asset-portal
powershell -ExecutionPolicy Bypass -File tests\payhere-simulate-notify.ps1 `
  -OrderId ord_xxxxxxxx -Amount 9.99 -Currency USD `
  -MerchantId "<merchant id>" -MerchantSecret "<merchant secret>"
```

Useful variations: `-StatusCode -2` (failed), `-StatusCode -1` (cancelled), `-StatusCode -3`
(chargeback), `-Amount 1.00` (wrong amount - the order is parked for review and nothing is credited).
Replaying the same command is safely ignored.

## 5. Currency and pricing

PayHere checkout accepts the currencies enabled on your PayHere account (commonly LKR and USD). The
order uses the **bundle's own currency**; the seeded bundles are priced in **USD**. If you sell in LKR,
change the bundle prices/currency in `DataInitializer.java` (for a fresh database) or in the
`coin_bundles` collection, and make sure that currency is enabled in your PayHere account. A payment
whose amount or currency differs from the order is never credited automatically (see below).

## 6. Operations

| Situation | What the system does | What you do |
|---|---|---|
| Duplicate / retried notification | Ignored; no double credit | Nothing |
| Notification with a bad signature | HTTP 401, nothing changes | Nothing (check logs if frequent) |
| Customer pays but amount/currency differs from the order | Order -> `REVIEW_REQUIRED`, **no credit** | Admin > Refunds page > *Payments awaiting review*: verify in the PayHere dashboard, then Approve or Reject |
| Customer fails, then retries and pays | Order ends `COMPLETED` (credited once) | Nothing |
| Customer pays after the 30-minute window | Still credited (`PROCESSED_LATE`) | Nothing |
| Unpaid order older than 30 minutes | Marked `EXPIRED` by a background job | Nothing |
| Refund request | Admin > Refunds > *Create refund case*: coins are removed from the wallet if the player still has them; otherwise `NEEDS_REVIEW` (never a negative balance) | After coins are reversed, refund the customer in the **PayHere dashboard**, then press *Confirm refunded* |
| Chargeback reported by PayHere | Case opened automatically; coins reversed if still available | Review `NEEDS_REVIEW` cases |

`POST /api/v1/admin/payments/{orderId}/reconcile` checks one order's status against the wallet ledger.

**Not automated:** the money side of a refund (done in the PayHere dashboard) and fetching a payment's
status directly from PayHere's API. Both are deliberately manual and audited.

## 7. Going live checklist

- [ ] PayHere live merchant account approved; live **Merchant ID/Secret** for your **live domain**.
- [ ] Backend runs with `SPRING_PROFILES_ACTIVE=prod` (disables mock gateway and mock login, and refuses
      to start without real secrets).
- [ ] Set `PAYHERE_SANDBOX=false`, `PAYHERE_MERCHANT_ID`, `PAYHERE_MERCHANT_SECRET`,
      `PAYHERE_NOTIFY_URL` (public **HTTPS**), `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`.
- [ ] Replace `JWT_SECRET` and `INTERNAL_SERVICE_KEY` with new random values (32+ characters).
- [ ] Configure `NAKAMA_SESSION_ENCRYPTION_KEY` and have the Unity client log in through
      `POST /api/v1/auth/nakama-login` (see below); promote admin users in the database by hand.
- [ ] MongoDB is a replica set with authentication and backups; the app database user is least-privilege.
- [ ] Terms/refund policy page is live and linked, as PayHere requires for approval.
- [ ] Do one small live payment and one refund end to end before announcing.

## Nakama login

`POST /api/v1/auth/nakama-login` with `{ "token": "<Nakama session token>" }` verifies the token's HS256
signature using `NAKAMA_SESSION_ENCRYPTION_KEY` (must equal Nakama's `session.encryption_key`), checks
expiry, and returns a portal session. The role is **never** taken from the client. Confirm the claim names
(`uid`, `usn`, `exp`) against your Nakama version when you wire it up.
