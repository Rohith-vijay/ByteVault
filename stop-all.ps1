# ByteVault Media - Stop All Backend & Frontend Services
$ErrorActionPreference = "SilentlyContinue"

# Kill any process listening on the specific ByteVault ports
$ports = @(5173, 8761, 8080, 8081, 8082, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8091, 8092, 8093)
foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        if ($conn.OwningProcess -gt 0) {
            Write-Host "Releasing port $port (PID: $($conn.OwningProcess))..."
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "All ByteVault backend ports released and ready for STS." -ForegroundColor Green
