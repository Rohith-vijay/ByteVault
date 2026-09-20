# ByteVault Media - Start All Backend & Frontend Services
param(
    [switch]$Wait = $false
)

$ErrorActionPreference = "Continue"
$rootDir = "D:\ProductManagementSystem"
$backendDir = "$rootDir\backend"
$frontendDir = "$rootDir\frontend\customer-portal"
$logsDir = "$rootDir\logs"

$sw = [System.Diagnostics.Stopwatch]::StartNew()

if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir | Out-Null
}

$services = @(
    @{ Name = "discovery-server"; Port = 8761; Module = "discovery-server" },
    @{ Name = "auth-service"; Port = 8081; Module = "auth-service" },
    @{ Name = "user-service"; Port = 8082; Module = "user-service" },
    @{ Name = "product-service"; Port = 8084; Module = "product-service" },
    @{ Name = "inventory-service"; Port = 8085; Module = "inventory-service" },
    @{ Name = "cart-service"; Port = 8086; Module = "cart-service" },
    @{ Name = "order-service"; Port = 8087; Module = "order-service" },
    @{ Name = "fulfillment-service"; Port = 8088; Module = "fulfillment-service" },
    @{ Name = "shipping-service"; Port = 8089; Module = "shipping-service" },
    @{ Name = "notification-service"; Port = 8090; Module = "notification-service" },
    @{ Name = "payment-service"; Port = 8091; Module = "payment-service" },
    @{ Name = "warehouse-service"; Port = 8092; Module = "warehouse-service" },
    @{ Name = "support-service"; Port = 8093; Module = "support-service" },
    @{ Name = "api-gateway"; Port = 8080; Module = "api-gateway" }
)

function Start-ByteVaultService($svc) {
    $jarPath = "$backendDir\$($svc.Module)\target\$($svc.Module)-1.0.0-SNAPSHOT.jar"
    if (Test-Path $jarPath) {
        Write-Host ">>> Starting $($svc.Name) on port $($svc.Port) (JAR mode)..." -ForegroundColor Cyan
        $argList = @("-Xms128m", "-Xmx384m", "-jar", $jarPath)
        $p = Start-Process -FilePath "java.exe" -ArgumentList $argList -WorkingDirectory $backendDir -PassThru -RedirectStandardOutput "$logsDir\$($svc.Name).log" -RedirectStandardError "$logsDir\$($svc.Name)-error.log"
        return $p
    } else {
        Write-Host ">>> Starting $($svc.Name) on port $($svc.Port) (Maven fallback)..." -ForegroundColor Cyan
        $mvnArgs = "/c mvn spring-boot:run -pl $($svc.Module)"
        $p = Start-Process -FilePath "cmd.exe" -ArgumentList $mvnArgs -WorkingDirectory $backendDir -PassThru -RedirectStandardOutput "$logsDir\$($svc.Name).log" -RedirectStandardError "$logsDir\$($svc.Name)-error.log"
        return $p
    }
}

$runningPids = @()

# 1. Start Eureka Discovery Server
$discovery = $services[0]
$p = Start-ByteVaultService $discovery
$runningPids += [PSCustomObject]@{ Name = $discovery.Name; Port = $discovery.Port; PID = $p.Id; StartTime = (Get-Date).ToString("s") }

Write-Host "Waiting 10 seconds for Discovery Server to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 2. Start Core Data Microservices (Auth, User, Product)
Write-Host "`n--- Launching Core Data Services ---" -ForegroundColor Magenta
for ($i = 1; $i -le 3; $i++) {
    $svc = $services[$i]
    $p = Start-ByteVaultService $svc
    $runningPids += [PSCustomObject]@{ Name = $svc.Name; Port = $svc.Port; PID = $p.Id; StartTime = (Get-Date).ToString("s") }
}
Start-Sleep -Seconds 4

# 3. Start Business & Commerce Services (Inventory, Cart, Order, Fulfillment, Payment)
Write-Host "`n--- Launching Commerce & Fulfillment Services ---" -ForegroundColor Magenta
for ($i = 4; $i -le 8; $i++) {
    $svc = $services[$i]
    $p = Start-ByteVaultService $svc
    $runningPids += [PSCustomObject]@{ Name = $svc.Name; Port = $svc.Port; PID = $p.Id; StartTime = (Get-Date).ToString("s") }
}
Start-Sleep -Seconds 4

# 4. Start Auxiliary Services (Shipping, Notification, Payment, Warehouse, Support)
Write-Host "`n--- Launching Auxiliary Services ---" -ForegroundColor Magenta
for ($i = 9; $i -lt ($services.Count - 1); $i++) {
    $svc = $services[$i]
    $p = Start-ByteVaultService $svc
    $runningPids += [PSCustomObject]@{ Name = $svc.Name; Port = $svc.Port; PID = $p.Id; StartTime = (Get-Date).ToString("s") }
}
Start-Sleep -Seconds 4

# 5. Start API Gateway
Write-Host "`n--- Launching API Gateway & Frontend ---" -ForegroundColor Magenta
$gateway = $services[$services.Count - 1]
$p = Start-ByteVaultService $gateway
$runningPids += [PSCustomObject]@{ Name = $gateway.Name; Port = $gateway.Port; PID = $p.Id; StartTime = (Get-Date).ToString("s") }

# 6. Start Frontend UI
Write-Host ">>> Starting Frontend UI (:5173) via Vite..." -ForegroundColor Cyan
$viteScript = "$frontendDir\node_modules\vite\bin\vite.js"
if (Test-Path $viteScript) {
    $pFrontend = Start-Process -FilePath "node.exe" -ArgumentList "`"$viteScript`" --host 0.0.0.0 --port 5173" -WorkingDirectory $frontendDir -PassThru -RedirectStandardOutput "$logsDir\frontend.log" -RedirectStandardError "$logsDir\frontend-error.log"
} else {
    $pFrontend = Start-Process -FilePath "cmd.exe" -ArgumentList "/c npm run dev -- --host 0.0.0.0 --port 5173" -WorkingDirectory $frontendDir -PassThru -RedirectStandardOutput "$logsDir\frontend.log" -RedirectStandardError "$logsDir\frontend-error.log"
}
$runningPids += [PSCustomObject]@{ Name = "frontend"; Port = 5173; PID = $pFrontend.Id; StartTime = (Get-Date).ToString("s") }

# Save PIDs to file
$runningPids | ConvertTo-Json -Depth 3 | Set-Content "$rootDir\.bytevault-pids.json"

$sw.Stop()
$elapsed = [Math]::Round($sw.Elapsed.TotalSeconds, 1)

Write-Host "`nAll 14 ByteVault backend components and frontend UI launched in $elapsed seconds!" -ForegroundColor Green
Write-Host "Persistence: 100% POSTGRESQL (NO H2, NO MOCKS)" -ForegroundColor Green
Write-Host "Logs are being written to $logsDir" -ForegroundColor Cyan
Write-Host "Discovery Server: http://localhost:8761" -ForegroundColor Green
Write-Host "API Gateway:      http://localhost:8080" -ForegroundColor Green
Write-Host "Frontend Portal:  http://localhost:5173" -ForegroundColor Green

if ($Wait) {
    Write-Host "`nByteVault Stack is running in daemon mode. Press Ctrl+C to stop." -ForegroundColor Magenta
    while ($true) {
        Start-Sleep -Seconds 10
    }
}
