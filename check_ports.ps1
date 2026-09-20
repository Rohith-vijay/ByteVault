$ports = @(
    @{Port=8888; Service="Config Server"},
    @{Port=8761; Service="Eureka Discovery Server"},
    @{Port=8080; Service="API Gateway"},
    @{Port=8081; Service="Auth Service"},
    @{Port=8082; Service="Order Service"},
    @{Port=8083; Service="User Service"},
    @{Port=8084; Service="Product Service"},
    @{Port=8085; Service="Inventory Service"},
    @{Port=8086; Service="Cart Service"},
    @{Port=8087; Service="Payment Service"},
    @{Port=8088; Service="Fulfillment Service"},
    @{Port=8089; Service="Shipping Service"},
    @{Port=8090; Service="Notification Service"},
    @{Port=8092; Service="Warehouse Service"},
    @{Port=8093; Service="Support Service"}
)

foreach ($p in $ports) {
    $line = netstat -ano | Select-String ":$($p.Port)\s+.*LISTENING" | Select-Object -First 1
    if ($line) {
        $parts = ($line -split '\s+') | Where-Object { $_ -ne "" }
        $pidVal = $parts[-1]
        Write-Host "Port $($p.Port) ($($p.Service)): LISTENING (PID: $pidVal)"
    } else {
        Write-Host "Port $($p.Port) ($($p.Service)): NOT RUNNING"
    }
}
