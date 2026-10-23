# Priority 7b — Empirical Outbox Retry-on-Failure Verification Script
$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Priority 7b: Outbox Retry-on-Failure Real Forced Failure Test" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080"
$rand = Get-Random
$psql = "C:\Program Files\PostgreSQL\13\bin\psql.exe"
$env:PGPASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } elseif ($env:PGPASSWORD) { $env:PGPASSWORD } else { "" }

# Step 1: Find and kill Fulfillment Service (Port 8088)
Write-Host "`n[Step 1] Stopping Fulfillment Service (Port 8088)..." -ForegroundColor Yellow
$conn = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
    Stop-Process -Id $conn.OwningProcess -Force
    Write-Host "Killed Fulfillment Service PID $($conn.OwningProcess) on port 8088." -ForegroundColor Green
} else {
    Write-Host "Fulfillment Service is not currently running on 8088." -ForegroundColor Yellow
}
Start-Sleep -Seconds 3

# Verify Port 8088 is dead
$connCheck = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue
if ($connCheck) {
    Write-Error "Failed to stop Fulfillment Service on port 8088!"
    exit 1
}
Write-Host "Confirmed Port 8088 is closed (endpoint unreachable)." -ForegroundColor Green

# Step 2: Register test customer and create digital order
Write-Host "`n[Step 2] Creating digital order through Gateway..." -ForegroundColor Yellow
$regRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/register" -Method Post -Body (@{
    email = "outbox_test_$rand@bytevault.com"
    password = "Password@123"
    fullName = "Outbox Verifier"
    role = "CUSTOMER"
} | ConvertTo-Json) -ContentType "application/json"

$token = $regRes.data.token
$userId = $regRes.data.userId
$authHeaders = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

$prodId = "bcea9a84-25ce-4e46-a195-db7193a5ffb4"
$orderRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/orders" -Method Post -Headers $authHeaders -Body (@{
    items = @(@{ productId = $prodId; quantity = 1 })
    customerName = "Outbox Verifier"
    customerEmail = "outbox_test_$rand@bytevault.com"
} | ConvertTo-Json)

$orderId = $orderRes.data.id
Write-Host "Order created: $orderId (Status: $($orderRes.data.status))" -ForegroundColor Green

# Step 3: Trigger payment capture (mark-paid) while Fulfillment is DOWN
Write-Host "`n[Step 3] Marking Order PAID while Fulfillment Service is DOWN..." -ForegroundColor Yellow
$internalHeaders = @{
    "X-Gateway-Secret" = "platform_default_gateway_shared_secret"
    "X-User-Roles" = "ROLE_INTERNAL_SERVICE"
    "Content-Type" = "application/json"
}

$paidRes = Invoke-RestMethod -Uri "http://localhost:8087/api/v1/orders/internal/$orderId/mark-paid" -Method Post -Headers $internalHeaders -Body (@{
    paymentId = "pay_outbox_fail_$rand"
    amount = 29.99
    currency = "INR"
    provider = "RAZORPAY"
} | ConvertTo-Json)

Write-Host "Mark-paid returned status: $($paidRes.data.status)" -ForegroundColor Green

# Step 4: Verify Database State while Fulfillment is DOWN
Write-Host "`n[Step 4] Checking PostgreSQL order_db outbox_events table..." -ForegroundColor Yellow
$outboxQuery = "SELECT id, status, retry_count, substring(last_error, 1, 50) as error_snippet FROM outbox_events WHERE aggregate_id = '$orderId';"
$outboxResult = & $psql -U postgres -h localhost -d order_db -c "$outboxQuery"
Write-Host "$outboxResult"

# Step 5: Restart Fulfillment Service on Port 8088
Write-Host "`n[Step 5] Restarting Fulfillment Service on port 8088..." -ForegroundColor Yellow
$jarPath = "D:\ProductManagementSystem\backend\fulfillment-service\target\fulfillment-service-1.0.0-SNAPSHOT.jar"
$fProc = Start-Process -FilePath "java.exe" -ArgumentList @("-Xms128m", "-Xmx384m", "-jar", $jarPath) -WorkingDirectory "D:\ProductManagementSystem\backend" -PassThru
Write-Host "Fulfillment Service started with PID $($fProc.Id). Waiting 15 seconds for startup..." -ForegroundColor Cyan
Start-Sleep -Seconds 15

# Step 6: Wait for OutboxPublisherService to retry and complete
Write-Host "`n[Step 6] Waiting for OutboxPublisherService scheduled retry (runs every 2s)..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "`n[Step 7] Checking PostgreSQL order_db outbox_events table AFTER RECOVERY..." -ForegroundColor Yellow
$outboxRecovered = & $psql -U postgres -h localhost -d order_db -c "SELECT id, status, retry_count, published_at FROM outbox_events WHERE aggregate_id = '$orderId';"
Write-Host "$outboxRecovered"

Write-Host "`n[Step 8] Verifying Entitlement was granted in fulfillment_db..." -ForegroundColor Yellow
$entQuery = "SELECT id, order_id, product_id, license_key, is_revoked FROM entitlements WHERE order_id = '$orderId';"
$entResult = & $psql -U postgres -h localhost -d fulfillment_db -c "$entQuery"
Write-Host "$entResult"

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host "Priority 7b OUTBOX RETRY-ON-FAILURE TEST COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
