<#
.SYNOPSIS
  Sends a signed PayHere-style payment notification to your local backend, so you can test the
  PayHere fulfillment path without a public tunnel or a real payment.

.DESCRIPTION
  PayHere calls notify_url with a form-encoded POST that carries md5sig:
    md5sig = UPPER(md5(merchant_id + order_id + payhere_amount + payhere_currency + status_code + UPPER(md5(merchant_secret))))
  This script builds exactly that, using the merchant id and secret you configured for the backend
  (PAYHERE_MERCHANT_ID / PAYHERE_MERCHANT_SECRET). It is a testing aid only; real notifications come
  from PayHere's servers. status_code: 2 = success, 0 = pending, -1 = cancelled, -2 = failed, -3 = chargeback.

.EXAMPLE
  # 1) In the portal, start a PayHere top-up and copy the orderId (shown in the URL of /checkout/pay).
  # 2) Simulate PayHere confirming the payment for the 9.99 USD bundle:
  .\payhere-simulate-notify.ps1 -OrderId ord_xxx -Amount 9.99 -Currency USD -MerchantId 1211149 -MerchantSecret "your-secret"
#>
param(
    [Parameter(Mandatory = $true)] [string] $OrderId,
    [Parameter(Mandatory = $true)] [string] $Amount,
    [Parameter(Mandatory = $true)] [string] $MerchantId,
    [Parameter(Mandatory = $true)] [string] $MerchantSecret,
    [string] $Currency = "USD",
    [string] $StatusCode = "2",
    [string] $PaymentId = ("SIM" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()),
    [string] $BackendUrl = "http://localhost:8080/api/v1/payments/webhooks/payhere"
)

function Get-Md5Upper([string] $text) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $bytes = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text))
    return ([System.BitConverter]::ToString($bytes) -replace '-', '').ToUpper()
}

$secretHash = Get-Md5Upper $MerchantSecret
$md5sig = Get-Md5Upper ($MerchantId + $OrderId + $Amount + $Currency + $StatusCode + $secretHash)

$body = @{
    merchant_id      = $MerchantId
    order_id         = $OrderId
    payment_id       = $PaymentId
    payhere_amount   = $Amount
    payhere_currency = $Currency
    status_code      = $StatusCode
    md5sig           = $md5sig
    method           = "VISA"
    status_message   = "Simulated notification"
}

Write-Host "POST $BackendUrl  (order $OrderId, status_code $StatusCode, amount $Amount $Currency)"
try {
    $res = Invoke-WebRequest -Uri $BackendUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -UseBasicParsing
    Write-Host "HTTP $($res.StatusCode): $($res.Content)" -ForegroundColor Green
} catch {
    Write-Host "Request failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) { Write-Host $_.ErrorDetails.Message }
}
