# RAILBOUND BATTLE ROYALE — PAYMENT & ASSET PORTAL
## Complete Implementation Instructions (Web Portal + Unity/Nakama Integration)

**Status:** Implementation blueprint, not completed software  
**Source:** `payment-gateway_implementation.pdf`, *Railbound Battle Royale: Payment Gateway & Real Coin Top-Up Architecture*, version 1.0, five pages.  
**Extended scope:** Player-facing web portal, admin publishing console, MongoDB implementation, APIs, news synchronization, testing and optional DevOps. These extensions are proposed designs, not claims made by the PDF.

> **MANDATORY STORAGE RULE:** Put project source, MongoDB data, Docker/WSL distributions and images, caches, logs, builds, downloads and configurable tool storage on **D:**, not C:. Some Windows/IDE system activity may still write to C:; audit and relocate configurable locations before running heavy installations. Project root: `D:\MyProjects\railbound-asset-portal\`.
>
> **MANDATORY CODE QUALITY RULE:** Add useful explanatory comments to code files wherever possible, especially for business rules, security decisions, payment state transitions, MongoDB transactions and Unity integration. Avoid comments that merely repeat code.
>
> **PAYMENT SAFETY RULE:** Build and test with a mock/sandbox provider first. Never put payment credentials in the client, never accept a client-reported payment success, and never handle raw card details in the app.

---

## 1. Product definition and scope

Create a fully functioning player web portal and admin console integrated with the **existing Railbound Battle Royale Unity 2023.1 + Nakama/REST game**. The web portal is **not itself an API**: it is a frontend, backend API, database, payment integration, game adapter and content release system.

### Primary user journeys
1. **Real Coin top-up:** Player signs in using the existing game identity, chooses a coin bundle on the web or Unity shop, completes hosted payment, backend verifies the gateway webhook, credits wallet exactly once and informs the player.
2. **Spend Real Coins:** Player selects an eligible catalog item, server checks the wallet and ownership, atomically deducts coins and grants the asset, and Unity displays/equips the owned item.
3. **Publish a new asset:** Content manager creates an item and uploads preview/content metadata; authorized administrator validates and publishes; web catalog and in-game news reflect the release; compatible game clients can use the item when its actual game-engine content is available.
4. **Recover:** Offline/reconnected clients fetch current wallet, inventory and news; retries never double-credit or double-charge.

### Currency policy from the PDF
- **Real Coins:** Paid premium currency; **implement now**; only the server can update its balance.
- **Game Coins:** Progression/earned soft currency; **defer acquisition and spending** while reserving schema/API extensibility.
- **Important:** Fiat currency (USD, etc.) and Real Coins are separate units. No client may submit an authoritative coin quantity, fiat amount, discount or wallet balance.
- For fairness, consider keeping gameplay-impacting equipment earnable through gameplay; do not make competitive advantage dependent on spending. Display full bundle totals and prices without manipulative urgency or misleading value claims.

### Initial catalog and bundle seed data (from PDF, subject to business approval)

| Bundle SKU | Base | Bonus | Total Real Coins | USD price |
|---|---:|---:|---:|---:|
| bundle_rc_100 | 100 | 0 | 100 | 0.99 |
| bundle_rc_500 | 500 | 50 | 550 | 4.99 |
| bundle_rc_1000 | 1000 | 150 | 1150 | 9.99 |
| bundle_rc_2500 | 2500 | 500 | 3000 | 24.99 |
| bundle_rc_5000 | 5000 | 1200 | 6200 | 49.99 |
| bundle_rc_10000 | 10000 | 3000 | 13000 | 99.99 |

| Item SKU | Type | Real Coin price |
|---|---|---:|
| skin_op_shadow_operative | Operator skin | 1200 |
| skin_op_desert_nomad | Operator skin | 850 |
| att_scope_tactical_acog | Attachment | 450 |
| att_barrel_suppressor_carbon | Attachment | 350 |
| att_mag_extended_drum | Attachment | 400 |
| att_grip_vertical_ergonomic | Attachment | 250 |
| skin_wp_gold_damascus | Weapon camouflage | 650 |

These are **seed examples**, not final commercial pricing. The PDF identifies Stripe, PayPal, Xsolla, Steam MicroTxn and a mock provider as options; provider fees and availability in that PDF are historical planning figures, **not verified current quotes**. Check current merchant-country eligibility, platform/store policies, age and payment requirements before choosing production providers. For Steam-distributed builds, confirm Steam's rules before offering external purchase paths.

---

## 2. Technology and architecture

| Layer | Selected technology | Why |
|---|---|---|
| Web player portal + admin console | Next.js, TypeScript, Tailwind CSS | Pages, responsive forms, server-rendered catalog |
| Core backend | Java, Spring Boot, Spring Security | Authoritative rules, REST, payments, permissions |
| Database | MongoDB replica set (local single-node replica set for development) | Flexible assets + ACID transactions for wallet/order/ledger |
| Game backend | Existing Nakama + existing game services | Preserve player identity and authoritative game state |
| Unity client | Existing Unity 2023.1 / Reach UI | Lobby, shop, wallet HUD, news and equip integration |
| Media and bundles | S3-compatible object storage + optional CDN | Store thumbnails, manifests, engine-compatible bundles |
| Notifications | REST refresh + Nakama events/WebSocket | Robust offline recovery with optional live updates |
| Payment | Mock provider, then one approved hosted-checkout provider | Secure fiat-to-Real-Coin conversion |
| API specification | OpenAPI 3 | Shared contract for Next.js, Spring Boot, Unity/Nakama |
| Optional ops | Docker, GitHub Actions, OpenTelemetry | Reproducible builds and diagnostics |

### Logical architecture

```text
Player Web (Next.js) -----------+
Admin Web (Next.js) ------------+---- HTTPS ---> Spring Boot API
Unity Lobby/Shop (REST) --------+                   |  |  |  |
Nakama (trusted adapter) --------------------------+  |  |  |
                                                     |  |  +--> Payment gateway
                                                     |  +-----> Object storage/CDN
                                                     +--------> MongoDB replica set
                                                Events -> Nakama notifications
```

**Authority boundary:** Existing Nakama player identity is authoritative. Spring Boot must validate tokens via the appropriate trusted identity integration and map a stable Nakama user ID to portal records. Never trust a `user_id` submitted in the browser body. Nakama may remain authoritative for match/gameplay state; establish **one** authoritative wallet/entitlement writer. Do **not** independently increment the same wallet in MongoDB and Nakama. If Nakama wallet is already the system of record, implement a reliable Nakama RPC adapter and use its atomic wallet operations, with an idempotent transaction record and reconciliation. Otherwise make MongoDB authoritative and expose read-only synchronized projections to Nakama. **Choose and document one approach before coding.** This blueprint defaults to **MongoDB as the wallet system of record**.

**Monetary flows:** Hosted payment -> fiat order -> wallet credit -> optional separate in-game shop purchase -> item entitlement. A top-up is **not** automatically an item purchase. An optional future direct-fiat asset purchase must have a separate order type and fulfillment path.

---

## 3. Repository and D-drive setup

```text
D:\MyProjects\railbound-asset-portal\
  instructions.md
  docs\                     # SRS, ADRs, API spec, diagrams, runbooks
  frontend\                 # Next.js player + admin routes
  backend\                  # Spring Boot modular monolith
  game-integration\         # Unity C# scripts, Nakama adapter contracts
  infrastructure\           # compose, scripts, deployment templates
  tests\                    # E2E, contract, load, fixtures
  local-data\               # local MongoDB volume and mock-provider data
  local-logs\
```

- Configure Node/npm/pnpm cache, Maven repository, MongoDB `dbPath`, temp directories, IDE caches, Docker data-root/WSL VHDX where supported to use D:.
- Do not commit `.env`, credentials, private keys, logs, binary bundles, `node_modules`, `target`, `.next` or database files.
- Create `.env.example` files with placeholders; store actual secrets in a secure secret manager in production.
- Check Git status and disk usage after each phase.

---

## 4. MongoDB data model and constraints

**Collections** (each record uses stable IDs, UTC timestamps, schema validation and required indexes):

1. `users`: `userId`, `nakamaUserId` (unique), `displayName`, `role`, `status`, `createdAt`. Do not duplicate game passwords.
2. `wallets`: `userId` (unique), `realCoins` (integer >= 0), `gameCoins` (reserved), `version`, `updatedAt`.
3. `coin_bundles`: `sku` (unique), `baseCoins`, `bonusCoins`, `totalCoins`, `fiatAmountMinor`, `currency`, `active`, `version`.
4. `payment_orders`: `orderId` (unique), `userId`, `bundleSku`, **immutable price/coin snapshot**, `provider`, `providerSessionId` (unique sparse), `status`, `expiresAt`, `completedAt`.
5. `payment_events`: `provider`, `providerEventId` (unique compound), `orderId`, `receivedAt`, `verifiedAt`, `processingStatus`; store only minimal sanitized event details.
6. `wallet_ledger`: append-only `entryId`, `userId`, `type` (`TOPUP`, `PURCHASE`, `REFUND`, `REVERSAL`, `ADMIN_ADJUSTMENT`), signed `delta`, `balanceAfter`, `referenceType`, `referenceId`, `createdAt`; unique reference key per operation.
7. `shop_items`: `sku` (unique), `category`, `title`, `description`, `realCoinPrice`, `availability`, `compatibility`, `previewMedia`, `currentContentVersion`, `publicationStatus`.
8. `item_versions`: `itemSku`, `version`, `minimumClientVersion`, `bundleKey`, `sha256`, `validationStatus`, `releaseId`.
9. `inventory_entitlements`: `userId`, `itemSku`, `acquiredAt`, `sourcePurchaseId`, `state` (`ACTIVE`, `REVOKED`), `equippedSlot`; unique (`userId`, `itemSku`) for permanent non-stackable items.
10. `shop_purchases`: `purchaseId`, `userId`, `itemSku`, `priceSnapshot`, `status`, `idempotencyKey`, `createdAt`; unique (`userId`, `idempotencyKey`).
11. `announcements`: `announcementId`, `title`, `body`, `imageKey`, `linkedItemSku`, `status`, `publishAt`, `expiresAt`, `audience`, `createdBy`.
12. `content_releases`: `releaseId`, `items`, `manifestVersion`, `targetClientVersions`, `status`, `approvedBy`, `publishedAt`.
13. `notifications`: `notificationId`, `userId`, `type`, `payload`, `readAt`, `createdAt`.
14. `audit_logs`: actor, action, target, before/after sanitized summaries, timestamp, correlation ID.
15. `outbox_events`: event ID, type, payload, status, retry count, createdAt; transactional event delivery.
16. `refund_cases`: original order, provider refund ID, refund/reversal status, reason, audit references.
17. `feature_flags`: flag name, audience, enabled state, effective dates.

**Indexes:** `users.nakamaUserId` unique; `wallets.userId` unique; `coin_bundles.sku` unique; `shop_items.sku` unique; `payment_orders.orderId` unique; (`payment_events.provider`, `payment_events.providerEventId`) unique; (`inventory_entitlements.userId`, `inventory_entitlements.itemSku`) unique for permanent items; (`shop_purchases.userId`, `shop_purchases.idempotencyKey`) unique; `announcements(status,publishAt)`; `wallet_ledger(userId,createdAt)`; `payment_orders(userId,createdAt)`.

**Transaction rules:** Use a MongoDB replica set and Spring MongoTransactionManager. A verified top-up transaction updates the order, credits the wallet, appends one ledger entry and creates an outbox event atomically. For shop purchases, use conditional atomic wallet update (`realCoins >= price`), unique ownership constraints, ledger insert and purchase state update within a transaction. Handle transaction retry and duplicate-key exceptions correctly. No negative balances, no floating-point currency, no mutable historical ledger entries.

**Example wallet document:**

```json
{"userId":"nakama-user-123","realCoins":1150,"gameCoins":0,"version":4,"updatedAt":"2026-10-28T00:00:00Z"}
```

**Example immutable top-up snapshot:**

```json
{"orderId":"ord-uuid","userId":"nakama-user-123","bundleSku":"bundle_rc_1000","coinsToCredit":1150,"amountMinor":999,"currency":"USD","provider":"mock","status":"PENDING"}
```

---

## 5. Identity, roles and security

- Integrate the web login with existing Nakama/game identity through a documented, secure identity flow. Do not invent a second password database unless the game has no identity service.
- Validate issuer, audience, signature, expiration and scopes of accepted access tokens. Use short-lived tokens, refresh/session protections, secure HttpOnly cookies for browser sessions and CSRF protections where relevant.
- Roles: `PLAYER`, `CONTENT_MANAGER`, `ADMIN`, `FINANCE_SUPPORT`, `SUPER_ADMIN`; least privilege, no public admin registration.
- Authorize every object by the authenticated user; the server derives user ID from the token, not from request JSON.
- Add rate limits for auth, checkout creation, purchases and support actions; audit sensitive administrative actions.
- Provider webhook endpoints are publicly reachable but require **provider-specific cryptographic signature verification** on the exact raw request body and relevant timestamp/replay protections. Do not assume every provider uses the same HMAC scheme.
- No raw card numbers, CVV, payment credentials or secrets in frontend, Unity, MongoDB, logs or Git.
- Hosted checkout reduces card-data exposure but **does not automatically guarantee zero PCI obligations**. Verify applicable compliance and platform policies.
- Secure media upload with type/size validation, malware scanning where applicable, private staging, and signed upload/download URLs.
- If selling to minors, enforce applicable guardian/age rules, clear pricing, purchase confirmation and appropriate refund/support disclosures. No dark patterns or pressure tactics.

---

## 6. Payment gateway architecture and state machines

### Top-up checkout workflow

1. Authenticated player selects `bundle_rc_1000` in web portal or Unity.
2. Client calls `POST /api/v1/payments/checkout-sessions` with **only** `bundleSku`, selected supported provider and idempotency key.
3. Backend derives player ID from token; reads active bundle and snapshots **1150 coins / USD 9.99** from trusted DB configuration.
4. Backend creates `PENDING` order and hosted provider session; persist provider session ID. Recover safely if session creation times out.
5. Client opens provider-hosted checkout; Unity uses external browser / platform-approved overlay.
6. Provider sends signed webhook to backend. Verify signature and cross-check provider order/session ID, currency, amount, payment status and expected merchant account.
7. Within one MongoDB transaction: mark order `COMPLETED`, credit wallet once, append ledger, insert outbox event. Duplicate events are acknowledged without additional credit.
8. Background outbox worker sends Nakama/WebSocket event; browser/Unity can also poll order status and wallet.
9. A redirect success page **never** grants coins. On webhook delay, show `PROCESSING` and poll.
10. Reconciliation job identifies paid-but-unfulfilled, stale pending and provider-refunded orders.

**State machine:** `CREATED -> PENDING -> PROCESSING -> COMPLETED`; alternate terminal paths `FAILED`, `CANCELLED`, `EXPIRED`; `COMPLETED -> REFUND_PENDING -> REFUNDED` handled as a separate refund lifecycle. Reject invalid backwards transitions. Use a provider event ID plus internal order/ledger uniqueness, not only a short-lived nonce.

### Real Coin item purchase workflow

1. Player sends `itemSku` and idempotency key, not price or wallet balance.
2. Server verifies published availability, version compatibility, account status, eligibility and not already owned.
3. In a MongoDB transaction, conditionally decrement balance by trusted price, create inventory entitlement, record purchase and negative ledger entry, emit outbox event.
4. Return authoritative new balance and entitlement. On retries, return original purchase result.
5. Game server checks entitlement before equip; game client only renders permitted visuals.

**Refunds/chargebacks:** Define policies for fiat refunds, Real Coin reversal, spent coins and already-granted items. Do not silently create negative balances. Route complex reversals to reviewed, auditable compensation workflows. Reconcile provider records regularly.

---

## 7. API contract — implement with OpenAPI and DTO validation

Prefix `/api/v1`. Require player token unless marked public or trusted provider callback. Every list is paginated; all errors return structured code, message, correlation ID and appropriate HTTP status. Use stable request/response DTOs and API versioning.

### Auth and account
| Method | Path | Access / action |
|---|---|---|
| GET | `/auth/me` | Player; session identity |
| POST | `/auth/logout` | Player; revoke/clear session |
| POST | `/auth/refresh` | Authorized session refresh, if architecture uses it |
| GET | `/players/me` | Player profile |
| GET | `/players/me/notifications` | Own notifications |
| PATCH | `/players/me/notifications/{id}/read` | Mark own notification read |

### Wallet and bundles
| Method | Path | Access / action |
|---|---|---|
| GET | `/coin-bundles` | Public/Player; active priced bundles |
| GET | `/wallet/me` | Player; authoritative Real Coin balance |
| GET | `/wallet/me/ledger` | Player; paginated transactions |
| POST | `/payments/checkout-sessions` | Player; create hosted checkout |
| GET | `/payments/orders/{orderId}` | Owning player; poll verified status |
| GET | `/payments/orders` | Player; own top-up history |
| POST | `/payments/webhooks/{provider}` | Provider callback; verified signature, not player JWT |
| POST | `/admin/payments/{orderId}/reconcile` | Finance admin; controlled reconciliation |
| POST | `/admin/refunds` | Finance admin; authorized refund workflow |

### Shop, inventory, equip
| Method | Path | Access / action |
|---|---|---|
| GET | `/shop/items` | Published item catalog |
| GET | `/shop/items/{sku}` | Item detail and compatibility |
| GET | `/shop/categories` | Categories |
| POST | `/shop/purchases` | Player; atomic coin purchase |
| GET | `/shop/purchases` | Player; own history |
| GET | `/inventory/me` | Player; owned entitlements |
| GET | `/inventory/me/{sku}` | Player; ownership status |
| POST | `/inventory/me/equip` | Player; authenticated server-validated equip |
| POST | `/inventory/me/unequip` | Player; unequip |

### News, releases, client synchronization
| Method | Path | Access / action |
|---|---|---|
| GET | `/news` | Published news, paging and version filters |
| GET | `/news/{id}` | Announcement detail |
| GET | `/game/catalog` | Game-compatible catalog |
| GET | `/game/content-manifest` | Signed/versioned manifest metadata |
| GET | `/game/releases/latest` | Latest compatible release |
| GET | `/game/config` | Safe public game config/feature flags |
| GET | `/game/sync` | Authenticated wallet + entitlements + news version cursor |
| POST | `/internal/entitlements/check` | **Service-to-service only**, trusted Nakama/game backend |

### Admin content and operations
| Method | Path | Access / action |
|---|---|---|
| GET/POST | `/admin/items` | List drafts/create item |
| GET/PATCH | `/admin/items/{sku}` | Inspect/edit item |
| POST | `/admin/items/{sku}/versions` | Register content version |
| POST | `/admin/items/{sku}/validate` | Compatibility validation |
| POST | `/admin/items/{sku}/publish` | Controlled publication |
| POST | `/admin/items/{sku}/archive` | Retire from sale, preserve ownership |
| POST | `/admin/uploads/presign` | Secure staged upload URL |
| GET/POST | `/admin/announcements` | Manage news |
| PATCH | `/admin/announcements/{id}` | Edit draft |
| POST | `/admin/announcements/{id}/publish` | Publish news |
| GET/POST | `/admin/releases` | Manage content releases |
| POST | `/admin/releases/{id}/approve` | Approve release |
| POST | `/admin/releases/{id}/publish` | Publish release |
| GET | `/admin/players` | Authorized player lookup |
| GET | `/admin/orders` | Payment/order oversight |
| GET | `/admin/audit-logs` | Restricted audit access |
| GET | `/admin/analytics/summary` | Authorized dashboard metrics |

### Example requests

```http
POST /api/v1/payments/checkout-sessions
Authorization: Bearer <token>
Idempotency-Key: 8f1a...unique...
Content-Type: application/json

{"bundleSku":"bundle_rc_1000","provider":"mock"}
```

```json
{"orderId":"ord-uuid","status":"PENDING","checkoutUrl":"https://example.invalid/hosted-checkout"}
```

```http
POST /api/v1/shop/purchases
Authorization: Bearer <token>
Idempotency-Key: a7c4...unique...
Content-Type: application/json

{"itemSku":"att_scope_tactical_acog"}
```

```json
{"purchaseId":"purchase-uuid","itemSku":"att_scope_tactical_acog","coinsSpent":450,"newBalance":700,"ownership":"ACTIVE"}
```

**Legacy compatibility:** The PDF calls these operations `/api/payment/create-session`, `/api/payment/webhook/stripe`, `/api/payment/status/{orderId}`, `/api/shop/purchase` or Nakama RPC equivalents. Implement a thin compatibility adapter only if existing Unity code already depends on these routes; keep a single underlying business implementation.

---

## 8. Frontend page specification (Next.js)

**Player routes:** `/`, `/login`, `/store`, `/store/[sku]`, `/top-up`, `/checkout/return`, `/wallet`, `/inventory`, `/purchase-history`, `/news`, `/news/[id]`, `/profile`, `/support`.

**Admin routes:** `/admin`, `/admin/items`, `/admin/items/new`, `/admin/items/[sku]`, `/admin/releases`, `/admin/news`, `/admin/coin-bundles`, `/admin/orders`, `/admin/refunds`, `/admin/players`, `/admin/audit`, `/admin/analytics`.

**UI requirements:**
- Header shows authenticated username and **server-sourced** Real Coin balance; soft Game Coin placeholder remains disabled until implemented.
- Top-up cards show fiat price, base/bonus/total coins, currency and clear purchase confirmation.
- Store cards show images, category, Real Coin price, ownership and compatibility; disable unavailable/incompatible purchases.
- Item detail supports preview and confirmation, but **actual Unity 3D previews** need game-engine assets or a separately supported web preview pipeline.
- Show pending/failed/successful checkout clearly; success only after server confirms payment.
- Admin asset wizard: metadata -> upload -> version/compatibility -> validate -> review -> publish -> news.
- Responsive layout, keyboard accessibility, loading/error/empty states, pagination, audit-friendly confirmation screens.
- Frontend API client should handle authorization, retry-safe reads and correlation IDs; never hold gateway secrets or database credentials.

---

## 9. Unity + Nakama integration specification

The PDF names existing Unity/Reach UI integration points. Preserve their intent and integrate through the game's actual interfaces:

| Component | Suggested location (PDF) | Responsibility |
|---|---|---|
| `LobbyUIController.cs` | Existing lobby | Real Coin HUD, '+' top-up entry, wallet refresh |
| `PaymentManager.cs` | `Assets/Project/Scripts/Economy/` | Checkout request, `Application.OpenURL`, status polling, refresh notifications |
| `LobbyShopUI.cs` | Same economy directory | Top-Up/Armory tabs, previews, purchase confirmation, owned/equipped states |
| `ArmoryEquipBridge.cs` | Same economy directory | Map verified entitlement to permitted in-game visual attachment/skin |
| `CharacterSkinRetargeter.cs` | Existing integration | Apply compatible character cosmetics |
| `Weapon.cs` | Existing weapon scripts | Equip compatible, server-authorized attachments |
| `GameLobby.unity` | Existing scene | HUD integration |
| `Shop Screen.prefab` | Existing prefab | Shop modal presentation |

**Implementation tasks:**
1. Inventory existing Unity scripts, Nakama RPCs and identity tokens; do not overwrite existing files blindly.
2. Build a C# API client with typed DTOs, timeouts, cancellation and structured error handling.
3. Wire lobby '+' to top-up UI; launch approved hosted checkout with `Application.OpenURL` (or platform-compliant overlay).
4. On return, poll order status; do not grant coins locally. Handle app backgrounding and resumed sessions.
5. Fetch wallet and inventory on login, after purchase and after reconnect; subscribe to Nakama notifications if available.
6. News screen calls `/game/sync` or `/news`; live events merely prompt a refresh.
7. Equip bridge requests authoritative entitlement check; game server rejects unauthorized equipment and invalid compatibility.
8. Keep in-game catalog and website catalog synchronized by SKU/version, not by matching display names.
9. Test WebGL, desktop and intended storefront/platform restrictions separately.

### Actual new content versus catalog metadata
Publishing MongoDB metadata **cannot create a 3D model or make Unity load it**. For assets not already bundled in the client, implement Unity-compatible Addressables/AssetBundles (or an approved equivalent), build pipeline, versioned manifest, checksum validation, secure object storage, compatible download, caching and fallback. Admin publication must fail if required client content is missing or incompatible. Server-side game logic/weapon behavior changes may require a game update and independent deployment. The portal is a release control plane, not an automatic game-engine content authoring tool.

### News publication flow

```text
Admin publishes approved release
 -> MongoDB release + announcement committed
 -> outbox event emitted
 -> Nakama notification to online clients
 -> Unity refreshes GET /game/sync or GET /news
 -> news card appears
 -> offline clients fetch same news at next login
```

---

## 10. Backend package structure

```text
backend/src/main/java/com/railbound/portal/
  config/ security/ common/
  identity/ wallet/ ledger/ bundles/
  payments/ refunds/ shop/ inventory/
  assets/ releases/ announcements/
  notifications/ gameintegration/
  audit/ analytics/
```

Each feature uses `controller/`, `service/`, `repository/`, `model/`, `dto/`, and where useful `mapper/`, `events/`, `validation/`. Controllers only validate/route requests; services own transactions and business rules; repositories own persistence. Centralize exception handling, API error responses and correlation IDs. Separate payment-provider interface from Stripe/PayPal/Xsolla/mock adapters so providers can be swapped without rewriting wallet logic.

**Recommended interfaces:** `PaymentGatewayAdapter`, `CheckoutService`, `WebhookVerificationService`, `WalletService`, `LedgerService`, `ShopPurchaseService`, `EntitlementService`, `ContentReleaseService`, `GameNotificationPublisher`, `IdentityAdapter`.

**Do not** run external provider network calls inside long-lived database transactions. Use durable state and reconciliation for network failure recovery.

---

## 11. Development phases with acceptance gates

### Phase 0 — Discover existing game and write decisions
- Inspect Unity/Nakama authentication, inventory, wallet, asset formats, game server authority and release process.
- Decide canonical player ID, wallet authority, entitlement authority, supported platforms and payment provider eligibility.
- Produce SRS, UML use cases, ER-style MongoDB model, sequence diagrams, OpenAPI draft and threat model.
- **Gate:** game team signs off authority boundaries and platform constraints.

### Phase 1 — D-drive setup and foundation
- Create repository, backend, frontend, `.env.example`, local MongoDB replica set, mock payment adapter and CI test skeleton.
- Seed bundle/item examples and indexes; add schema validation.
- **Gate:** app boots locally; health endpoint and DB transaction smoke test pass.

### Phase 2 — Shared authentication
- Integrate Nakama identity, player sessions, role checks, protected admin routes and audit logging.
- **Gate:** normal player cannot call admin routes or access another player's wallet.

### Phase 3 — Wallet, ledger and mock top-up
- Implement wallet API, immutable ledger, order creation, mock checkout, signed test callback, idempotent credit and reconciliation.
- **Gate:** 1,000+150 bundle credits **exactly 1150** once; duplicate callbacks produce no extra credit.

### Phase 4 — Web portal and Unity top-up UI
- Implement player top-up page, wallet/history, Unity top bar '+' and modal, checkout-return polling, accessible error states.
- **Gate:** test player tops up via web and Unity displays authoritative new balance.

### Phase 5 — Real Coin armory shop
- Implement catalog, ownership checks, atomic coin deduction, entitlements, inventory, equip authorization, web/Unity shop.
- **Gate:** starting 1150, purchasing the 450-coin ACOG yields balance **700**, one entitlement and one ledger debit; concurrent attempts cannot overspend.

### Phase 6 — Admin portal and publishing
- Implement admin CRUD, staged uploads, content versions, compatibility validation, approvals, releases and announcements.
- **Gate:** draft is invisible; approved published item appears in web store and in-game news; archived items disappear from sale but remain in prior owner inventory.

### Phase 7 — Actual Unity content delivery
- Implement content bundle build, storage, manifest, checksum, client compatibility and cache recovery if the game needs runtime-delivered assets.
- **Gate:** compatible client loads valid content; incompatible client receives safe unavailable state.

### Phase 8 — Real sandbox payment provider
- Confirm merchant eligibility and platform rules; implement one provider adapter, hosted checkout, webhook verification, retry and refund paths.
- **Gate:** sandbox end-to-end payment credits once; forged/duplicate/out-of-order webhook tests pass.

### Phase 9 — Testing, hardening and observability
- Integration, contract, concurrency, security, load, recovery, audit and backup-restore tests; metrics and alerting.
- **Gate:** documented security and recovery checklist passes.

### Phase 10 — Optional DevOps and deployment
- Docker Compose for local stack; GitHub Actions lint/test/build; staging and production separation; secret manager; backups; TLS; monitoring; deployment approval and rollback.
- **Gate:** reproducible staging deployment and verified restore/reconciliation procedures.

---

## 12. Mandatory tests and edge cases

**Payments:** duplicate webhook; forged signature; wrong amount/currency; unknown order; webhook before browser redirect; delayed webhook; provider timeout; checkout expiration; retry after network loss; out-of-order events; two simultaneous top-ups; refund/chargeback; provider reconciliation.

**Wallet/shop:** insufficient balance; exactly sufficient balance; simultaneous purchases; repeat idempotency key; already-owned non-stackable item; disabled/unpublished item; stale price; revoked account; game/web concurrent purchase; transaction retry; integer overflow bounds; wallet/ledger invariant checks.

**Content/game:** draft news hidden; scheduled release; incompatible Unity version; missing asset bundle; invalid checksum; offline news catch-up; WebSocket reconnect; catalog cache invalidation; equip spoofing; game server ownership enforcement; content rollback.

**Auth/admin:** expired token; privilege escalation; IDOR; CSRF; upload abuse; replayed webhook; rate limit; compromised admin session; audit immutability and sensitive data masking.

**Acceptance invariants:**
- Sum of ledger deltas reconciles to authoritative wallet balance, subject to initial seed and explicit adjustments.
- Every completed top-up has one and only one credit ledger entry.
- Every successful non-stackable purchase has one debit and one entitlement.
- No user can spend coins without server authorization.
- No published asset is advertised as playable unless game compatibility requirements are met.
- The same player sees consistent wallet and inventory across web, Unity and Nakama.

---

## 13. Use case coverage

**Player:** sign in/out; view wallet; browse bundles; create checkout; view payment status; view ledger; browse/filter catalog; view item detail; purchase with Real Coins; see insufficient funds; view/equip/unequip owned items; read game news; receive notifications; view order history; contact support; handle refund status.

**Content manager:** create/edit draft; upload previews and content; register asset version; validate compatibility; create announcement; submit for approval; view release status.

**Admin:** approve/publish/archive asset; schedule release; manage categories and bundles; view players and orders; inspect analytics; view audit logs; manage authorized roles and feature flags.

**Finance support:** reconcile orders; review disputes; initiate approved refunds/reversals; export authorized audit records.

**Game backend:** validate identity; fetch authoritative wallet and entitlements; enforce equip authorization; retrieve catalog/news/manifests; process live update notification; recover missed updates.

**System:** verify payment signatures; deduplicate callbacks; enforce atomic transactions; process outbox; retry failures; reconcile payments; backup and restore; alert on ledger discrepancies.

---

## 14. Optional DevOps implementation

- **Local:** Docker Compose for backend, frontend, MongoDB replica set, mock gateway and optional Redis; persistent volumes on D:.
- **CI:** GitHub Actions runs Java/TypeScript lint, unit tests, MongoDB integration tests, API contract tests, dependency scanning and container build.
- **CD:** deploy to staging automatically after passing checks; production requires approval and database migration/rollback plan.
- **Secrets:** secret manager; rotate webhook keys and DB credentials; use separate sandbox and production identities.
- **Monitoring:** request latency, checkout conversion, webhook processing lag, failed credit jobs, outbox queue age, wallet/ledger mismatch, asset publication errors and Unity sync failures.
- **Recovery:** automated database backups, tested restores, replay-safe outbox workers and payment-provider reconciliation.
- **Kubernetes/Terraform:** optional later, not prerequisites for MVP.

---

## 15. Documentation deliverables

Maintain these alongside code:

```text
docs/
  01-requirements.md
  02-architecture.md
  03-data-model.md
  04-api-contract.yaml
  05-payment-sequence.md
  06-wallet-ledger-invariants.md
  07-unity-nakama-integration.md
  08-content-release-pipeline.md
  09-security-threat-model.md
  10-test-plan.md
  11-deployment-runbook.md
  12-refunds-reconciliation.md
  adr/
```

Add README setup commands, sample sanitized fixtures, architecture decisions, endpoint ownership, API error examples and troubleshooting guidance. **Code comments must explain the purpose and risks of important logic.** Never include actual payment credentials or private user data in documentation.

---

## 16. Definition of done: first complete demonstration

The MVP is complete only when an authenticated Railbound player can:

1. Sign into the web portal using the existing game account.
2. View current Real Coin balance (Game Coins deferred).
3. Select the PDF's `bundle_rc_1000` example and complete a **mock/sandbox** USD 9.99 checkout.
4. See exactly **1150 Real Coins** after verified webhook fulfillment.
5. Buy `att_scope_tactical_acog` for **450 Real Coins** and see **700 remaining**.
6. See the ACOG entitlement in web inventory and Unity, with game-server ownership checks.
7. See a newly published admin announcement in the Unity lobby news UI, including after reconnect.
8. Observe that duplicate webhooks, duplicate purchases, spoofed client balances and unauthorized admin actions are rejected.

**First concrete coding step:** inspect the existing Unity/Nakama identity and inventory interfaces, then create the MongoDB replica set, wallet/ledger schemas and mock top-up endpoint on D:. This establishes the authority and transaction rules before UI work.

---

## 17. Source alignment and decisions needing confirmation

**Directly derived from the five-page PDF:** Unity 2023.1 + Nakama/REST + Reach UI; Real Coins active / Game Coins deferred; example coin bundles and item catalog; checkout -> verified webhook -> atomic wallet credit -> notification; relational wallet/order/ledger/inventory concepts; Unity `PaymentManager.cs`, `LobbyShopUI.cs`, `ArmoryEquipBridge.cs`; four original roadmap phases.

**Design extensions proposed for the requested portal:** Next.js + Spring Boot + MongoDB replica set, web player/admin experiences, richer collections and API catalog, content releases, news publishing, durable outbox, refunds, DevOps and expanded tests.

**Confirm before implementation:** (1) Nakama identity/token mechanism; (2) whether Nakama already owns wallet or inventory; (3) Unity runtime asset delivery capabilities; (4) deployment storefronts and payment-provider eligibility; (5) whether item purchases are Real-Coin-only or also directly fiat; (6) refund/chargeback and age-appropriate commerce policies. Do not assume these details are specified in the PDF.
