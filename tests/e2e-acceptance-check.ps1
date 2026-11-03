# ---------------------------------------------------------------------------
# End-to-end acceptance verification for the Railbound portal.
#
# Default mode exercises the REAL PayHere webhook contract by posting a
# correctly signed application/x-www-form-urlencoded callback to the backend
# (no PayHere network access required). It therefore needs the same
# merchant id/secret the backend was started with:
#
#   $env:PAYHERE_MERCHANT_ID="1211149"
#   $env:PAYHERE_MERCHANT_SECRET="sandbox_secret_xxx"
#   .\tests\e2e-acceptance-check.ps1
#
# Add -UseMock to use the development mock provider instead (requires
# APP_PAYMENT_MOCK_ENABLED=true on the backend).
# ---------------------------------------------------------------------------
param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$MerchantId = $env:PAYHERE_MERCHANT_ID,
    [string]$MerchantSecret = $env:PAYHERE_MERCHANT_SECRET,
    [switch]$UseMock
)

$ErrorActionPreference = "Stop"

function Get-Md5Hex([string]$inputText) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($inputText)
        return ([System.BitConverter]::ToString($md5.ComputeHash($bytes)) -replace '-', '').ToUpper()
    } finally {
        $md5.Dispose()
    }
}

function Wait-OrderCompleted([string]$orderId, [hashtable]$headers, [int]$attempts = 20) {
    for ($i = 0; $i -lt $attempts; $i++) {
        $order = Invoke-RestMethod -Uri "$BaseUrl/payments/orders/$orderId" -Headers $headers
        if ($order.status -eq "COMPLETED") { return $order }
        Start-Sleep -Milliseconds 500
    }
    throw "Order $orderId did not reach COMPLETED"
}

Write-Host "=========================================================="
Write-Host "  RUNNING RAILBOUND ACCEPTANCE GATE VERIFICATION           "
Write-Host "=========================================================="

# 1. Public catalog
Write-Host "`n[1/7] Testing public coin bundles endpoint..."
$bundles = Invoke-RestMethod -Uri "$BaseUrl/coin-bundles" -Method Get
Write-Host "Found $($bundles.Count) active bundles."
if ($bundles.Count -lt 6) { throw "Expected 6 seed bundles" }
Write-Host "OK Bundles Gate Passed"

# 2. Fresh player identity (unique per run => deterministic wallet assertions)
Write-Host "`n[2/7] Creating a fresh Nakama-style player session..."
$nakamaId = [guid]::NewGuid().ToString()
$authBody = @{
    nakamaUserId = $nakamaId
    displayName  = "AcceptanceRunner"
    role         = "PLAYER"
} | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$BaseUrl/auth/mock-token" -Method Post -Body $authBody -ContentType "application/json"
$token = $authRes.token
$userId = $authRes.user.userId
$headers = @{ "Authorization" = "Bearer $token" }
Write-Host "Authenticated player: $userId (Nakama ID: $nakamaId)"
Write-Host "OK Player Identity Gate Passed"

# 3. Top-up checkout
Write-Host "`n[3/7] Creating top-up checkout for bundle_rc_1000..."
$provider = if ($UseMock) { "mock" } else { "payhere" }
$checkoutBody = @{ bundleSku = "bundle_rc_1000"; provider = $provider } | ConvertTo-Json
$checkoutHeaders = $headers + @{ "Idempotency-Key" = "e2e_" + [guid]::NewGuid().ToString("N") }
$checkoutRes = Invoke-RestMethod -Uri "$BaseUrl/payments/checkout-sessions" -Method Post -Headers $checkoutHeaders `
    -Body $checkoutBody -ContentType "application/json"
$orderId = $checkoutRes.orderId
Write-Host "Created order $orderId ($($checkoutRes.coinsToCredit) RC, $($checkoutRes.amountMinor / 100) $($checkoutRes.currency)) via $($checkoutRes.provider)"
if ($checkoutRes.coinsToCredit -ne 1150) { throw "Expected 1150 coins in snapshot" }
if ($checkoutRes.amountMinor -ne 999) { throw "Expected 999 minor units (9.99 USD)" }

# 3b. Verify the server-signed PayHere form (hash must match the official formula)
if (-not $UseMock) {
    Write-Host "`n[3b/7] Verifying PayHere hosted checkout form and hash..."
    $tokenMatch = [regex]::Match($checkoutRes.checkoutUrl, "token=([^&]+)")
    if (-not $tokenMatch.Success) { throw "Checkout URL did not contain a checkout token" }
    $hosted = Invoke-RestMethod -Uri "$BaseUrl/payments/hosted/$orderId`?token=$($tokenMatch.Groups[1].Value)" -Method Get
    if ($hosted.actionUrl -notmatch "payhere\.lk/pay/checkout") { throw "Unexpected PayHere action URL: $($hosted.actionUrl)" }
    if ($hosted.fields.order_id -ne $orderId) { throw "Hosted form order_id mismatch" }
    if ($hosted.fields.amount -ne "9.99") { throw "Hosted form amount mismatch: $($hosted.fields.amount)" }
    if ($hosted.fields.currency -ne "USD") { throw "Hosted form currency mismatch" }
    $secretHash = Get-Md5Hex $MerchantSecret
    $expectedHash = Get-Md5Hex ("{0}{1}{2}{3}{4}" -f $MerchantId, $orderId, "9.99", "USD", $secretHash)
    if ($hosted.fields.hash -ne $expectedHash) { throw "Hosted form hash mismatch" }
    Write-Host "OK PayHere Form + Hash Gate Passed"
}

# 4. Fulfill through the authoritative path
Write-Host "`n[4/7] Posting signed payment callback and waiting for fulfillment..."
if ($UseMock) {
    Invoke-RestMethod -Uri "$BaseUrl/payments/mock-complete/$orderId" -Method Post | Out-Null
} else {
    if ([string]::IsNullOrWhiteSpace($MerchantId) -or [string]::IsNullOrWhiteSpace($MerchantSecret)) {
        throw "PAYHERE_MERCHANT_ID and PAYHERE_MERCHANT_SECRET are required (or pass -UseMock)."
    }
    $amount = "9.99"
    $currency = "USD"
    $statusCode = "2"
    $paymentId = "e2e" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $secretHash = Get-Md5Hex $MerchantSecret
    $md5sig = Get-Md5Hex ("{0}{1}{2}{3}{4}{5}" -f $MerchantId, $orderId, $amount, $currency, $statusCode, $secretHash)

    $callback = @{
        merchant_id      = $MerchantId
        order_id         = $orderId
        payment_id       = $paymentId
        payhere_amount   = $amount
        payhere_currency = $currency
        status_code      = $statusCode
        method           = "VISA"
        md5sig           = $md5sig
    }
    Invoke-RestMethod -Uri "$BaseUrl/payments/webhooks/payhere" -Method Post -Body $callback `
        -ContentType "application/x-www-form-urlencoded" | Out-Null
}
$order = Wait-OrderCompleted $orderId $headers

$wallet = Invoke-RestMethod -Uri "$BaseUrl/wallet/me" -Method Get -Headers $headers
Write-Host "Authoritative wallet balance after top-up: $($wallet.realCoins) RC"
if ($wallet.realCoins -ne 1150) { throw "Expected 1150 coins, got $($wallet.realCoins)" }
Write-Host "OK 1150 Real Coin Top-Up Gate Passed"

# 5. Duplicate callback replay
Write-Host "`n[5/7] Replaying the identical callback (must not double-credit)..."
if (-not $UseMock -and $callback) {
    Invoke-RestMethod -Uri "$BaseUrl/payments/webhooks/payhere" -Method Post -Body $callback `
        -ContentType "application/x-www-form-urlencoded" | Out-Null
}
$walletAfterReplay = Invoke-RestMethod -Uri "$BaseUrl/wallet/me" -Method Get -Headers $headers
if ($walletAfterReplay.realCoins -ne 1150) { throw "Duplicate callback double-credited the wallet" }
Write-Host "OK Idempotency Gate Passed"

# 6. Atomic armory purchase
Write-Host "`n[6/7] Purchasing Tactical ACOG (450 RC -> 700 RC remaining)..."
$purchaseBody = @{ itemSku = "att_scope_tactical_acog" } | ConvertTo-Json
$purchaseRes = Invoke-RestMethod -Uri "$BaseUrl/shop/purchases" -Method Post -Headers $headers `
    -Body $purchaseBody -ContentType "application/json"
Write-Host "Spent $($purchaseRes.coinsSpent) RC, new balance $($purchaseRes.newBalance) RC"
if ($purchaseRes.newBalance -ne 700) { throw "Expected 700 coins remaining, got $($purchaseRes.newBalance)" }
Write-Host "OK Armory Purchase Gate Passed"

# 7. Entitlement
Write-Host "`n[7/7] Verifying inventory entitlement..."
$inv = Invoke-RestMethod -Uri "$BaseUrl/inventory/me" -Method Get -Headers $headers
$acogOwned = $inv | Where-Object { $_.itemSku -eq "att_scope_tactical_acog" }
if (-not $acogOwned) { throw "ACOG entitlement missing from inventory" }
Write-Host "OK Entitlement Gate Passed"

Write-Host "`n=========================================================="
Write-Host "  ALL ACCEPTANCE CRITERIA VERIFIED                          "
Write-Host "=========================================================="
