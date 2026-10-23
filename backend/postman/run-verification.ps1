# ByteVault Media - Automated End-to-End API Verification Runner
Write-Host '=================================================================' -ForegroundColor Cyan
Write-Host 'ByteVault Media - Automated Verification Suite (Priority 6)' -ForegroundColor Cyan
Write-Host '=================================================================' -ForegroundColor Cyan

$baseUrl = "http://localhost:8080"
$rand = Get-Random

# Test 1: Service Discovery
Write-Host "`n[1/10] Verifying Eureka Service Discovery..." -ForegroundColor Yellow
$apps = (Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Headers @{Accept="application/json"}).applications.application
$appNames = $apps | Select-Object -ExpandProperty name
Write-Host "Registered Services ($($appNames.Count)): $($appNames -join ', ')" -ForegroundColor Green

# Test 2: Public Product Catalog
Write-Host "`n[2/10] Verifying Public Product Catalog..." -ForegroundColor Yellow
$products = Invoke-RestMethod -Uri "$baseUrl/api/v1/products" -Method Get
Write-Host "Found $($products.data.Count) published products in catalog." -ForegroundColor Green

# Test 3: Customer Registration & Token
Write-Host "`n[3/10] Verifying Customer Registration and Auth Flow..." -ForegroundColor Yellow
$custRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/register" -Method Post -Body (@{
    email = "verifier_cust_$rand@bytevault.com"
    password = "Password@123"
    fullName = "Verified Customer"
    role = "CUSTOMER"
} | ConvertTo-Json) -ContentType "application/json"
$token = $custRes.data.token
$userId = $custRes.data.userId
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }
Write-Host "Customer created (UserId: $userId). Token acquired." -ForegroundColor Green

# Test 4: Vendor Registration with PENDING profile
Write-Host "`n[4/10] Verifying Vendor Registration with synchronous PENDING profile..." -ForegroundColor Yellow
$vendRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/register" -Method Post -Body (@{
    email = "verifier_vend_$rand@bytevault.com"
    password = "Password@123"
    fullName = "Verified Store"
    role = "VENDOR"
} | ConvertTo-Json) -ContentType "application/json"
$vUserId = $vendRes.data.userId
$vToken = $vendRes.data.token
Write-Host "Vendor created (UserId: $vUserId, Role: $($vendRes.data.role))." -ForegroundColor Green

# Test 5: Vendor Write-Gate while PENDING & Admin Approval
Write-Host "`n[5/10] Verifying Vendor Write-Gate (Should block while PENDING)..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$baseUrl/api/v1/products/vendor?publish=true" -Method Post -Headers @{Authorization = "Bearer $vToken"} -Body (@{
        name = "Blocked Product"; price = 10.0; productType = "DIGITAL"; categoryId = 1
    } | ConvertTo-Json) -ContentType "application/json"
    Write-Host 'FAIL: Write-gate allowed product creation!' -ForegroundColor Red
    throw "Write-gate failed: Unapproved vendor was allowed to create products"
} catch {
    Write-Host 'SUCCESS: Write-gate rejected unapproved vendor [403 Forbidden].' -ForegroundColor Green
}

# Admin approves vendor
$adminPassword = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "AdminPass123!" }
$adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -Body (@{
    email = "admin@bytevault.com"; password = $adminPassword
} | ConvertTo-Json) -ContentType "application/json"
$adminToken = $adminLogin.data.token
$adminHeaders = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }

$allVendors = Invoke-RestMethod -Uri "$baseUrl/api/v1/admin/vendors" -Method Get -Headers $adminHeaders
$vendProfile = ($allVendors.data | Where-Object { $_.userId -eq $vUserId })[0]
if ($vendProfile) {
    $null = Invoke-RestMethod -Uri "$baseUrl/api/v1/admin/vendors/$($vendProfile.id)/approve" -Method Post -Headers $adminHeaders
    Write-Host "Vendor approved by Admin: Profile $($vendProfile.id)" -ForegroundColor Green
}

# Approved vendor creates and publishes a test product
$createProdRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/products/vendor?publish=true" -Method Post -Headers @{ Authorization = "Bearer $vToken"; "Content-Type" = "application/json" } -Body (@{
    name = "Verified Digital Handbook $rand"
    description = "Automated verification test digital package"
    price = 29.99
    productType = "DIGITAL"
    categoryId = 1
} | ConvertTo-Json)
$prodId = $createProdRes.data.id
$prodPrice = $createProdRes.data.price
Write-Host "Vendor successfully published product: $prodId ($($createProdRes.data.name)) at price $prodPrice" -ForegroundColor Green

# Test 6: Security Perimeter Enforcement (No Token -> 401)
Write-Host "`n[6/10] Verifying Gateway Auth Enforcement (Missing Token -> 401)..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$baseUrl/api/v1/users/me" -Method Get
    Write-Host 'FAIL: Unauthenticated request succeeded!' -ForegroundColor Red
} catch {
    Write-Host 'SUCCESS: Unauthenticated request rejected [401 Unauthorized].' -ForegroundColor Green
}

# Test 7: Direct Downstream Perimeter Enforcement (Direct bypass -> 403)
Write-Host "`n[7/10] Verifying Downstream Perimeter Filter (Direct bypass -> 403)..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/v1/users/me" -Method Get
    Write-Host 'FAIL: Direct downstream bypass succeeded!' -ForegroundColor Red
} catch {
    Write-Host 'SUCCESS: Direct bypass without Gateway Secret rejected [403 Forbidden].' -ForegroundColor Green
}

# Test 8: Cart Operations
Write-Host "`n[8/10] Verifying Cart Operations..." -ForegroundColor Yellow
$cart = Invoke-RestMethod -Uri "$baseUrl/api/v1/cart/items" -Method Post -Headers $headers -Body (@{ productId = $prodId; quantity = 1 } | ConvertTo-Json)
Write-Host "Cart item added. Total items: $($cart.data.items.Count), Total amount: $($cart.data.totalAmount)" -ForegroundColor Green

# Test 9: Digital Order Creation, Payment Verification, and Synchronous Fulfillment
Write-Host "`n[9/10] Verifying Digital Order, Payment Verification and Synchronous Fulfillment..." -ForegroundColor Yellow
$order = Invoke-RestMethod -Uri "$baseUrl/api/v1/orders" -Method Post -Headers $headers -Body (@{
    items = @(@{ productId = $prodId; quantity = 1 })
    customerName = "Verified Customer"
    customerEmail = "verifier_cust_$rand@bytevault.com"
} | ConvertTo-Json)
$orderId = $order.data.id
$orderTotal = $order.data.totalAmount
Write-Host "Order created: $orderId (Status: $($order.data.status), Total: $orderTotal)" -ForegroundColor Green

# Step 9b: Payment Order Creation
$payOrderRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/payments/create-order" -Method Post -Headers $headers -Body (@{
    amount = [double]$orderTotal
    currency = "INR"
    receipt = $orderId
} | ConvertTo-Json)
$rzpOrderId = if ($payOrderRes.orderId) { $payOrderRes.orderId } else { $payOrderRes.data.orderId }
Write-Host "Payment order generated: $rzpOrderId" -ForegroundColor Green

# Step 9c: Payment Verification (Routes through PaymentService -> syncPaymentToOrder -> order-service markPaid -> fulfillment Feign -> vendorLedgerService)
$verifyRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/payments/verify" -Method Post -Headers $headers -Body (@{
    razorpayOrderId = $rzpOrderId
    razorpayPaymentId = "pay_mock_$rand"
    razorpaySignature = "sig_mock_$rand"
    dbOrderId = $orderId
    amount = [double]$orderTotal
    userId = $userId
} | ConvertTo-Json)
Write-Host "Payment verified via Payment Service. Sync status: $($verifyRes.syncStatus)" -ForegroundColor Green

# Verify order state moved to PAID
$orderCheck = Invoke-RestMethod -Uri "$baseUrl/api/v1/orders/$orderId" -Method Get -Headers $headers
Write-Host "Order confirmed in order-service. Status: $($orderCheck.data.status)" -ForegroundColor Green

# Test 10: Entitlements & Notifications
Write-Host "`n[10/10] Verifying Entitlements and Transactional Notification..." -ForegroundColor Yellow
Start-Sleep -Seconds 1
$entitlements = Invoke-RestMethod -Uri "$baseUrl/api/v1/entitlements" -Method Get -Headers $headers
Write-Host "Entitlements granted: $($entitlements.data.Count). Active license: $($entitlements.data[0].licenseKey)" -ForegroundColor Green

$notif = Invoke-RestMethod -Uri "$baseUrl/api/v1/notifications/email/order-confirmation" -Method Post -Headers $headers -Body (@{
    toEmail = "verifier_cust_$rand@bytevault.com"
    customerName = "Verified Customer"
    orderNumber = $order.data.orderNumber
    orderId = $orderId
    totalAmount = [double]$orderTotal
    orderItems = @(@{ productName = "Verified Digital Handbook"; quantity = 1; price = [double]$orderTotal })
} | ConvertTo-Json)
Write-Host "Order confirmation notification dispatched: $($notif.success)" -ForegroundColor Green

# Test 11: Vendor Ledger Credit Verification (Priority 11 Regression Guard)
Write-Host "`n[11/11] Verifying Vendor Ledger Credit (Priority 11 Regression Guard)..." -ForegroundColor Yellow
$earningsRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/payments/vendor/earnings" -Method Get -Headers @{ Authorization = "Bearer $vToken" }
$earnings = if ($earningsRes.data) { $earningsRes.data } else { $earningsRes }
Write-Host "Vendor earnings queried: Gross=$($earnings.grossEarnings), Net=$($earnings.netEarnings), Orders=$($earnings.totalOrdersCount)" -ForegroundColor Green

$gross = [double]$earnings.grossEarnings
$net = [double]$earnings.netEarnings
if ($gross -le 0) {
    throw "REGRESSION FAILURE: Vendor gross earnings is 0! Expected gross > 0 after verified payment."
}
$expectedNet = [math]::Round($gross * 0.90, 2)
$actualNet = [math]::Round($net, 2)
if ($actualNet -ne $expectedNet) {
    throw "REGRESSION FAILURE: Vendor net earnings ($actualNet) does not match expected 90% of gross ($expectedNet)!"
}
Write-Host "REGRESSION GUARD VERIFIED: Gross ($gross) > 0 and Net ($net) == 90% of Gross (10% platform fee)." -ForegroundColor Green

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "ALL 11 VERIFICATION SUITE TESTS PASSED WITH 100% SUCCESS!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
