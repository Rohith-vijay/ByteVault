param (
    [Parameter(Mandatory=$true, Position=0)]
    [string]$ServiceName,
    
    [Parameter(Mandatory=$false, Position=1)]
    [int]$Port = 0
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $rootDir "backend"
$logsDir = Join-Path $rootDir "logs"

if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

# 1. Load .env into the Process environment table safely
$envPath = Join-Path $rootDir ".env"
$dbPassword = ""
$cartDbPassword = ""
$gatewaySecret = "platform_default_gateway_shared_secret"
$jwtSecret = ""

if (Test-Path $envPath) {
    Get-Content $envPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $parts = $line.Split('=', 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim()
            [System.Environment]::SetEnvironmentVariable($k, $v, "Process")
            if ($k -eq "DB_PASSWORD") { $dbPassword = $v }
            if ($k -eq "CART_DB_PASSWORD") { $cartDbPassword = $v }
            if ($k -eq "GATEWAY_SECRET") { $gatewaySecret = $v }
            if ($k -eq "JWT_SECRET") { $jwtSecret = $v }
        }
    }
}

if (-not $dbPassword -and $env:DB_PASSWORD) { $dbPassword = $env:DB_PASSWORD }
if (-not $gatewaySecret -and $env:GATEWAY_SECRET) { $gatewaySecret = $env:GATEWAY_SECRET }

# 2. Map service name to default port if not provided
$defaultPorts = @{
    "discovery-server"    = 8761
    "identity-service"    = 8081
    "product-service"     = 8084
    "cart-service"        = 8086
    "order-service"       = 8087
    "payment-service"     = 8091
    "fulfillment-service" = 8088
    "notification-service"= 8090
    "api-gateway"         = 8080
}

if ($Port -le 0) {
    if ($defaultPorts.ContainsKey($ServiceName)) {
        $Port = $defaultPorts[$ServiceName]
    } else {
        throw "Port not specified and unknown default port for service '$ServiceName'."
    }
}

# 3. Locate JAR
$jarPath = Join-Path $backendDir "$ServiceName\target\$ServiceName-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    throw "Target JAR for '$ServiceName' not found at: $jarPath. Run 'mvn package -DskipTests' first."
}

# 4. Check if target port is already bound; if so, terminate existing process
$existingConns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
foreach ($conn in $existingConns) {
    Write-Host "[start-service] Port $Port is currently in use by PID $($conn.OwningProcess). Terminating..." -ForegroundColor Yellow
    Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# 5. Build JVM arguments with standard stack memory flags
$jvmArgs = @(
    "-Dserver.port=$Port",
    "-Xms64m",
    "-Xmx320m",
    "-XX:MetaspaceSize=64m",
    "-XX:MaxMetaspaceSize=180m",
    "-Xss384k",
    "-XX:+TieredCompilation",
    "-XX:TieredStopAtLevel=1"
)

# Apply datasource credentials
if ($ServiceName -eq "cart-service" -and $cartDbPassword) {
    $jvmArgs += "-Dspring.datasource.password=$cartDbPassword"
} elseif ($dbPassword -and $ServiceName -ne "discovery-server" -and $ServiceName -ne "api-gateway") {
    $jvmArgs += "-Dspring.datasource.password=$dbPassword"
}

if ($gatewaySecret) {
    $jvmArgs += "-Dapp.gateway.secret=$gatewaySecret"
}
if ($jwtSecret) {
    $jvmArgs += "-Dapp.jwt.secret=$jwtSecret"
}

$jvmArgs += "-Deureka.instance.instanceId=${ServiceName}:${Port}"
$jvmArgs += "-jar"
$jvmArgs += "`"$jarPath`""

$logPrefix = if ($Port -eq $defaultPorts[$ServiceName]) { $ServiceName } else { "$ServiceName-$Port" }
$outLog = Join-Path $logsDir "$logPrefix.log"
$errLog = Join-Path $logsDir "$logPrefix-error.log"

$argString = $jvmArgs -join ' '
$cmd = "cmd.exe /c `"java.exe $argString >> `"$outLog`" 2>> `"$errLog`"`""

Write-Host "[start-service] Launching $ServiceName on port $Port..." -ForegroundColor Cyan

$res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine = $cmd
    CurrentDirectory = $backendDir
}

if ($res.ReturnValue -ne 0) {
    throw "Failed to start $ServiceName via WMI. ReturnValue: $($res.ReturnValue)"
}

Start-Sleep -Seconds 2

Write-Host "[start-service] Successfully launched $ServiceName (PID: $($res.ProcessId)) on port $Port." -ForegroundColor Green

[pscustomobject]@{
    ServiceName = $ServiceName
    Port        = $Port
    ProcessId   = $res.ProcessId
    LogFile     = $outLog
}
