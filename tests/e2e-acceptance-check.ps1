# End-to-End Acceptance Verification Script for Railbound Battle Royale Portal
$ErrorActionPreference = "Stop"

$BaseUrl = "http://localhost:8080/api/v1"

Write-Host "=========================================================="
Write-Host "  RUNNING RAILBOUND ACCEPTANCE GATE VERIFICATION           "
Write-Host "=========================================================="

# Test 1: Bundles Endpoint
Write-Host "`n[1/5] Testing public coin bundles endpoint..."
$bundles = Invoke-RestMethod -Uri "$BaseUrl/coin-bundles" -Method Get
Write-Host "Found $($bundles.Count) active bundles."
if ($bundles.Count -lt 6) { throw "Expected 6 seed bundles" }
Write-Host "✓ Bundles Gate Passed"

# Test 2: Auth & Token Generation
Write-Host "`n[2/5] Testing Nakama player identity session..."
$authBody = @{
    nakamaUserId = "00000000-0000-0000-0000-000000000001"
    displayName = "ViperActual"
    role = "PLAYER"
} | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$BaseUrl/auth/mock-token" -Method Post -Body $authBody -ContentType "application/json"
$token = $authRes.token
$userId = $authRes.user.userId
Write-Host "Authenticated player: $userId (Nakama ID: $($authRes.user.nakamaUserId))"
$headers = @{
    "Authorization" = "Bearer $token"
}
Write-Host "✓ Player Identity Gate Passed"

# Test 3: Top-up Checkout & HMAC-Signed Mock Completion
Write-Host "`n[3/5] Testing top-up checkout & single-credit fulfillment (1000+150 = 1150 RC)..."
$checkoutBody = @{
    bundleSku = "bundle_rc_1000"
    provider = "mock"
} | ConvertTo-Json
$checkoutRes = Invoke-RestMethod -Uri "$BaseUrl/payments/checkout-sessions" -Method Post -Headers $headers -Body $checkoutBody -ContentType "application/json"
$orderId = $checkoutRes.orderId
Write-Host "Created top-up order: $orderId ($($checkoutRes.coinsToCredit) coins, `$$($checkoutRes.amountMinor/100) USD)"

# Trigger mock completion
$completeRes = Invoke-RestMethod -Uri "$BaseUrl/payments/mock-complete/$orderId" -Method Post
$wallet = Invoke-RestMethod -Uri "$BaseUrl/wallet/me" -Method Get -Headers $headers
Write-Host "Authoritative wallet balance after top-up: $($wallet.realCoins) RC"
if ($wallet.realCoins -ne 1150) { throw "Expected 1150 coins, got $($wallet.realCoins)" }
Write-Host "✓ 1150 Real Coin Top-Up Gate Passed"

# Test 4: Armory Purchase of ACOG (450 coins)
Write-Host "`n[4/5] Testing atomic purchase of Tactical ACOG (450 RC -> 700 RC remaining)..."
$purchaseBody = @{
    itemSku = "att_scope_tactical_acog"
} | ConvertTo-Json
$purchaseRes = Invoke-RestMethod -Uri "$BaseUrl/shop/purchases" -Method Post -Headers $headers -Body $purchaseBody -ContentType "application/json"
Write-Host "Purchase successful! Spent: $($purchaseRes.coinsSpent) RC, New Balance: $($purchaseRes.newBalance) RC"
if ($purchaseRes.newBalance -ne 700) { throw "Expected 700 coins remaining, got $($purchaseRes.newBalance)" }
Write-Host "✓ Armory Purchase Gate Passed (Balance: 700 RC)"

# Test 5: Entitlements Verification
Write-Host "`n[5/5] Verifying inventory entitlement..."
$inv = Invoke-RestMethod -Uri "$BaseUrl/inventory/me" -Method Get -Headers $headers
$acogOwned = $inv | Where-Object { $_.itemSku -eq "att_scope_tactical_acog" }
if (-not $acogOwned) { throw "ACOG entitlement missing from inventory" }
Write-Host "ACOG entitlement verified in player inventory!"
Write-Host "✓ Entitlements Verification Gate Passed"

Write-Host "`n=========================================================="
Write-Host "  ALL ACCEPTANCE CRITERIA FROM INSTRUCTIONS.MD VERIFIED!  "
Write-Host "=========================================================="
