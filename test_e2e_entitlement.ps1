# 1. Login as customer
$loginBody = '{"email":"customer@bytevault.com","password":"Password@123"}'
$loginHeaders = @{ "Content-Type" = "application/json" }
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method POST -Headers $loginHeaders -Body $loginBody
$token = $loginRes.data.token
$userId = $loginRes.data.userId
Write-Host "Logged in as customer. UserId: $userId"

$authHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# 2. Create Order with Digital Product (Cloud Architecture Handbook)
$orderBody = @"
{
    "items": [
        {
            "productId": "bcea9a84-25ce-4e46-a195-db7193a5ffb4",
            "quantity": 1
        }
    ],
    "customerEmail": "customer@bytevault.com",
    "customerName": "Customer User",
    "shippingAddress": ""
}
"@

$orderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method POST -Headers $authHeaders -Body $orderBody
$orderId = $orderRes.data.id
$totalAmount = $orderRes.data.totalAmount
Write-Host "Created Order: $orderId, Total: $totalAmount, Type: $($orderRes.data.orderType)"

# 3. Simulate payment verification
$paymentBody = @"
{
    "razorpayOrderId": "order_mock_$([Guid]::NewGuid().ToString().Substring(0,8))",
    "razorpayPaymentId": "pay_test_$([Guid]::NewGuid().ToString().Substring(0,8))",
    "razorpaySignature": "sig_mock_valid",
    "dbOrderId": "$orderId",
    "userId": "$userId",
    "amount": $totalAmount
}
"@

$payRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/verify" -Method POST -Headers $authHeaders -Body $paymentBody
Write-Host "Payment Verify Status: $($payRes.status), syncStatus: $($payRes.syncStatus)"

# 4. Check Entitlements
$entRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/entitlements" -Method GET -Headers $authHeaders
Write-Host "Entitlements Count: $($entRes.data.Count)"
foreach ($e in $entRes.data) {
    Write-Host " - Entitlement for: $($e.title) [status=$($e.status), licenseKey=$($e.licenseKey)]"
}

# 5. Get Download URL
$dlRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/downloads/bcea9a84-25ce-4e46-a195-db7193a5ffb4" -Method GET -Headers $authHeaders
Write-Host "Download URL: $($dlRes.data.downloadUrl)"
