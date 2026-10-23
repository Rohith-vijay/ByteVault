$ErrorActionPreference = "Continue"

$endpoints = @(
    @{ Name = "Gateway Swagger UI"; Url = "http://localhost:8080/swagger-ui.html" },
    @{ Name = "Identity Service API Docs"; Url = "http://localhost:8080/v3/api-docs/identity-service" },
    @{ Name = "Product Service API Docs"; Url = "http://localhost:8080/v3/api-docs/product-service" },
    @{ Name = "Order Service API Docs"; Url = "http://localhost:8080/v3/api-docs/order-service" },
    @{ Name = "Cart Service API Docs"; Url = "http://localhost:8080/v3/api-docs/cart-service" },
    @{ Name = "Payment Service API Docs"; Url = "http://localhost:8080/v3/api-docs/payment-service" },
    @{ Name = "Fulfillment Service API Docs"; Url = "http://localhost:8080/v3/api-docs/fulfillment-service" },
    @{ Name = "Notification Service API Docs"; Url = "http://localhost:8080/v3/api-docs/notification-service" }
)

Write-Host "=========================================================="
Write-Host " BYTEVAULT MEDIA - SWAGGER & OPENAPI DOCS VERIFICATION"
Write-Host "=========================================================="

$allPassed = $true

foreach ($ep in $endpoints) {
    try {
        $res = Invoke-WebRequest -Uri $ep.Url -Method Get -TimeoutSec 10 -UseBasicParsing
        $status = $res.StatusCode
        $content = $res.Content
        $detail = ""
        if ($ep.Url -like "*api-docs*") {
            $json = $content | ConvertFrom-Json -ErrorAction SilentlyContinue
            if ($json -and $json.info) {
                $detail = "(Title: '$($json.info.title)', Version: '$($json.info.version)')"
            }
        } else {
            $detail = "(HTML bytes: $($content.Length))"
        }
        Write-Host " [PASS] $($status) - $($ep.Name): $($ep.Url) $detail" -ForegroundColor Green
    } catch {
        $allPassed = $false
        Write-Host " [FAIL] $($ep.Name): $($ep.Url) - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "=========================================================="
if ($allPassed) {
    Write-Host " ALL SWAGGER & OPENAPI ENDPOINTS VERIFIED SUCCESSFULLY (HTTP 200)" -ForegroundColor Green
    exit 0
} else {
    Write-Host " SOME ENDPOINTS FAILED VERIFICATION" -ForegroundColor Red
    exit 1
}
