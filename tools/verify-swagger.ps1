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

$rootDir = Split-Path -Parent $PSScriptRoot
$evidenceDir = Join-Path $rootDir "docs\evidence"
if (-not (Test-Path $evidenceDir)) { New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null }
$logFile = Join-Path $evidenceDir "swagger-verification-20261026.log"

function Log-Msg([string]$msg, [string]$color = "White") {
    Write-Host $msg -ForegroundColor $color
    $msg | Out-File -FilePath $logFile -Append -Encoding UTF8
}

if (Test-Path $logFile) { Remove-Item $logFile -Force }

Log-Msg "==========================================================" "Cyan"
Log-Msg " BYTEVAULT MEDIA - SWAGGER & OPENAPI DOCS VERIFICATION" "Cyan"
Log-Msg " Execution Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" "Cyan"
Log-Msg " Gateway Base URL: http://localhost:8080" "Cyan"
Log-Msg "==========================================================" "Cyan"

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
        Log-Msg " [PASS] $($status) - $($ep.Name): $($ep.Url) $detail" "Green"
    } catch {
        $allPassed = $false
        Log-Msg " [FAIL] $($ep.Name): $($ep.Url) - $($_.Exception.Message)" "Red"
    }
}

Log-Msg "==========================================================" "Cyan"
if ($allPassed) {
    Log-Msg " ALL 8 SWAGGER & OPENAPI ENDPOINTS VERIFIED SUCCESSFULLY (HTTP 200)" "Green"
    Log-Msg " Evidence saved to: $logFile" "Green"
    exit 0
} else {
    Log-Msg " SOME ENDPOINTS FAILED VERIFICATION" "Red"
    exit 1
}
