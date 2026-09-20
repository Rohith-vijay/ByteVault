param (
    [string[]]$ServiceNames
)

$ErrorActionPreference = "Continue"
$backend = "D:\ProductManagementSystem\backend"

$allServices = @{
    "discovery-server" = @{ Port = 8761; Jar = "$backend\discovery-server\target\discovery-server-1.0.0-SNAPSHOT.jar" };
    "auth-service"     = @{ Port = 8081; Jar = "$backend\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar" };
    "user-service"     = @{ Port = 8082; Jar = "$backend\user-service\target\user-service-1.0.0-SNAPSHOT.jar" };
    "product-service"  = @{ Port = 8084; Jar = "$backend\product-service\target\product-service-1.0.0-SNAPSHOT.jar" };
    "inventory-service"= @{ Port = 8085; Jar = "$backend\inventory-service\target\inventory-service-1.0.0-SNAPSHOT.jar" };
    "cart-service"     = @{ Port = 8086; Jar = "$backend\cart-service\target\cart-service-1.0.0-SNAPSHOT.jar" };
    "order-service"    = @{ Port = 8087; Jar = "$backend\order-service\target\order-service-1.0.0-SNAPSHOT.jar" };
    "fulfillment-service" = @{ Port = 8088; Jar = "$backend\fulfillment-service\target\fulfillment-service-1.0.0-SNAPSHOT.jar" };
    "shipping-service" = @{ Port = 8089; Jar = "$backend\shipping-service\target\shipping-service-1.0.0-SNAPSHOT.jar" };
    "notification-service" = @{ Port = 8090; Jar = "$backend\notification-service\target\notification-service-1.0.0-SNAPSHOT.jar" };
    "payment-service"  = @{ Port = 8091; Jar = "$backend\payment-service\target\payment-service-1.0.0-SNAPSHOT.jar" };
    "warehouse-service"= @{ Port = 8092; Jar = "$backend\warehouse-service\target\warehouse-service-1.0.0-SNAPSHOT.jar" };
    "support-service"  = @{ Port = 8093; Jar = "$backend\support-service\target\support-service-1.0.0-SNAPSHOT.jar" };
    "api-gateway"      = @{ Port = 8080; Jar = "$backend\api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar" }
}

$startedProcesses = @()
$namesList = @()
foreach ($s in $ServiceNames) {
    $namesList += ($s -split ',')
}

foreach ($name in $namesList) {
    $name = $name.Trim()
    if ([string]::IsNullOrWhiteSpace($name)) { continue }
    if (-not $allServices.ContainsKey($name)) {
        Write-Warning "Unknown service: $name"
        continue
    }

    $svc = $allServices[$name]
    Write-Host "`n>>> Starting $name on port $($svc.Port)..." -ForegroundColor Cyan
    $p = Start-Process -FilePath "java" -ArgumentList "-jar `"$($svc.Jar)`"" -PassThru -WindowStyle Hidden
    $startedProcesses += @{ Name = $name; Port = $svc.Port; Process = $p }
}

Write-Host "`nWaiting for services to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 25

$results = @()

foreach ($item in $startedProcesses) {
    $port = $item.Port
    $name = $item.Name
    $uiUrl = "http://localhost:$port/swagger-ui/index.html"
    $docsUrl = "http://localhost:$port/v3/api-docs"

    $uiStatus = "FAILED"
    $docsStatus = "FAILED"
    $apiTitle = "N/A"

    try {
        $uiResp = Invoke-WebRequest -Uri $uiUrl -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        $uiStatus = "HTTP $($uiResp.StatusCode)"
    } catch {
        try {
            $altUrl = "http://localhost:$port/swagger-ui.html"
            $altResp = Invoke-WebRequest -Uri $altUrl -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
            $uiStatus = "HTTP $($altResp.StatusCode) (via /swagger-ui.html)"
        } catch {
            $uiStatus = "ERR: $($_.Exception.Message)"
        }
    }

    try {
        $docsResp = Invoke-RestMethod -Uri $docsUrl -TimeoutSec 5 -ErrorAction Stop
        $docsStatus = "HTTP 200"
        if ($docsResp.info -and $docsResp.info.title) {
            $apiTitle = $docsResp.info.title
        }
    } catch {
        $docsStatus = "ERR: $($_.Exception.Message)"
    }

    $results += [PSCustomObject]@{
        Service = $name
        Port = $port
        SwaggerUI = $uiStatus
        OpenApiDocs = $docsStatus
        Title = $apiTitle
    }
}

Write-Host "`n=== SWAGGER VERIFICATION RESULTS ===" -ForegroundColor Green
$results | Format-Table -AutoSize

Write-Host "`nStopping batch processes..." -ForegroundColor Yellow
foreach ($item in $startedProcesses) {
    try {
        Stop-Process -Id $item.Process.Id -Force -ErrorAction SilentlyContinue
    } catch {}
}
Write-Host "Batch processes terminated." -ForegroundColor Green
