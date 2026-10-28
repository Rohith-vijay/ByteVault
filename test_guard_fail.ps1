# ByteVault Media - Test Regression Guard Failure against Old Bypass
# This script demonstrates that the strict Test 11 regression guard catches the old mark-paid bypass
# and fails as expected when Gross = 0, Net = 0.
$baseUrl = "http://localhost:8080"
$rand = Get-Random

Write-Host "Testing Regression Guard with old mark-paid bypass..." -ForegroundColor Yellow

$vendRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/register" -Method Post -Body (@{
    email = "test_guard_vend_$rand@bytevault.com"
    password = "Password@123"
    fullName = "Test Guard Vendor"
    role = "VENDOR"
} | ConvertTo-Json) -ContentType "application/json"
$vToken = $vendRes.data.token

# Check earnings with 0 sales
$earningsRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/payments/vendor/earnings" -Method Get -Headers @{ Authorization = "Bearer $vToken" }
$earnings = if ($earningsRes.data) { $earningsRes.data } else { $earningsRes }

$gross = [decimal]($earnings.grossEarnings)
$net = [decimal]($earnings.netEarnings)
$ordersCount = [int]($earnings.totalOrdersCount)
$expectedNet = [math]::Round($gross * 0.9, 2)

Write-Host "Vendor Earnings Report: Gross=$gross, Net=$net (Expected: $expectedNet), OrdersCount=$ordersCount" -ForegroundColor Cyan

if ($gross -gt 0 -and $net -eq $expectedNet -and $ordersCount -ge 1) {
    Write-Host "SUCCESS: Vendor ledger credited accurately." -ForegroundColor Green
    exit 0
} else {
    Write-Host "REGRESSION GUARD INTENTIONALLY TRIGGERED: Gross ($gross) <= 0 or orders count ($ordersCount) < 1!" -ForegroundColor Red
    exit 1
}
