# Refunds, Chargebacks and Reconciliation

This document describes the finance-support lifecycle implemented in
`backend/src/main/java/com/railbound/portal/refunds/` and
`PaymentService`.

## 1. Order and refund states

```text
Top-up happy path:
  CREATED -> PENDING -> PROCESSING -> COMPLETED

Alternate terminal paths:
  FAILED / CANCELLED / EXPIRED

Refund lifecycle:
  COMPLETED -> REFUND_PENDING -> REFUNDED
```

`REFUND_PENDING` is deliberately non-terminal: PayHere may accept a fiat refund
while the Real Coin reversal still needs review (for example, the player already
spent the coins).

## 2. Chargeback / refund signals from PayHere

A verified `status_code=-3` callback, or a Retrieval API result of
`REFUNDED`/`CHARGEBACKED`/`REFUND REQUESTED`/`REFUND PROCESSING`, does **not**
silently debit the wallet. It:

1. records a sanitized `payment_events` row,
2. moves a `COMPLETED` order to `REFUND_PENDING`,
3. leaves the wallet untouched until finance support reviews the case.

This avoids negative balances and avoids taking away an entitlement the player
may have already used.

## 3. Controlled refund workflow

Endpoint:

```http
POST /api/v1/admin/refunds
Authorization: Bearer <ADMIN|SUPER_ADMIN|FINANCE_SUPPORT token>
Content-Type: application/json

{
  "orderId": "ord_...",
  "reason": "PayHere dispute case / customer request reference"
}
```

Steps (in order):

1. Require a non-blank audit reason.
2. Require order status `COMPLETED` or `REFUND_PENDING`.
3. Require a PayHere order.
4. Create or reuse a unique `refund_cases` document (`REQUESTED`).
5. Resolve the PayHere `payment_id` from the order or the Retrieval API.
6. Call PayHere's Refund API **outside** any MongoDB transaction.
7. In one MongoDB transaction:
   - record the provider refund id/message,
   - if `wallet.realCoins >= coinsToReverse`, debit with ledger type `REFUND`
     and mark the order `REFUNDED`,
   - otherwise mark the case `REVIEW_REQUIRED` and the order `REFUND_PENDING`
     without touching the wallet.
8. Persist the final case status.

Refund case statuses:

| Status | Meaning |
|---|---|
| `REQUESTED` | Case persisted; provider call in progress or ambiguous |
| `PROVIDER_REFUNDED` | Provider accepted; DB reversal not yet final |
| `COMPLETED` | Provider refund + coin reversal both recorded |
| `REVIEW_REQUIRED` | Provider refund succeeded but coins were already spent |
| `FAILED` | Provider declined or the call failed; safe to retry |

Idempotency: one refund case per order (`orderId` unique). An in-flight or
completed case is returned as-is instead of issuing a second provider refund.
A `FAILED` case can be retried.

## 4. Reconciliation

Endpoint:

```http
POST /api/v1/admin/payments/{orderId}/reconcile
Authorization: Bearer <ADMIN|SUPER_ADMIN|FINANCE_SUPPORT token>
```

The backend queries PayHere's Retrieval API using the merchant OAuth token. It
never accepts a status supplied by the browser. Outcomes:

| PayHere status | Result |
|---|---|
| `RECEIVED` | Same verified fulfilment path as a webhook; credits once |
| `REFUNDED` | Marks `REFUND_PENDING` for review |
| `CHARGEBACKED` | Marks `REFUND_PENDING` for review |
| `REFUND REQUESTED` | Marks `REFUND_PENDING` for review |
| `REFUND PROCESSING` | Marks `REFUND_PENDING` for review |
| Unknown / blank | Returns the provider status without changing the order |

Use reconciliation when:

- the callback was lost or delayed,
- the provider reports paid but the order is still `PROCESSING`,
- a refund/chargeback appears in the PayHere portal but no callback arrived.

Reconciliation is idempotent: the event id and ledger reference uniqueness
prevent a second credit.

## 5. Audit and invariants

- `wallet_ledger` is append-only.
- A successful top-up has exactly one `TOPUP` entry keyed by
  `("PAYMENT_ORDER", orderId)`.
- A successful reversal has exactly one `REFUND` entry keyed by
  `("REFUND", orderId)`.
- Wallet balances never go below zero.
- Sum of ledger deltas equals the authoritative wallet balance, subject to
  explicitly recorded seed/adjustment entries.
- `refund_cases` preserves reason, actor, provider ids, amounts and timestamps.

## 6. Operational checklist

- [ ] Finance staff have `FINANCE_SUPPORT` (not `SUPER_ADMIN`) where possible.
- [ ] `PAYHERE_APP_ID`/`PAYHERE_APP_SECRET` are configured.
- [ ] PayHere API key domain/IP restrictions allow the backend.
- [ ] Every refund has a dispute/customer reference in `reason`.
- [ ] `REVIEW_REQUIRED` cases are reviewed for entitlement revocation or an
      alternative compensation decision.
- [ ] Reconciliation is run for stale `PROCESSING` orders before closing books.
