$env:PGPASSWORD = 'Rohith@358'
$psql = 'C:\Program Files\PostgreSQL\13\bin\psql.exe'

Write-Host "`n=== 1. IDENTITY_DB (Users & Credentials) ===" -ForegroundColor Cyan
& $psql -U postgres -h 127.0.0.1 -p 5432 -d identity_db -c "SELECT id, username, role, is_active FROM auth_credentials ORDER BY created_at DESC LIMIT 10;"

Write-Host "`n=== 2. PRODUCT_DB (Products & Categories) ===" -ForegroundColor Cyan
& $psql -U postgres -h 127.0.0.1 -p 5432 -d product_db -c "SELECT count(*) AS total_products, status FROM products GROUP BY status;"

Write-Host "`n=== 3. ORDER_DB (Customer Orders) ===" -ForegroundColor Cyan
& $psql -U postgres -h 127.0.0.1 -p 5432 -d order_db -c "SELECT count(*) AS total_orders, status FROM orders GROUP BY status;"

Write-Host "`n=== 4. PAYMENT_DB (Payment Transactions & Ledger) ===" -ForegroundColor Cyan
& $psql -U postgres -h 127.0.0.1 -p 5432 -d payment_db -c "SELECT count(*) AS total_transactions, status FROM payment_transactions GROUP BY status;"

Write-Host "`n=== 5. FULFILLMENT_DB (Digital Entitlements) ===" -ForegroundColor Cyan
& $psql -U postgres -h 127.0.0.1 -p 5432 -d fulfillment_db -c "SELECT count(*) AS total_entitlements, status FROM entitlements GROUP BY status;"

Write-Host "`n=== 6. CART_DB (Shopping Carts) ===" -ForegroundColor Cyan
& $psql -U postgres -h 127.0.0.1 -p 5432 -d cart_db -c "SELECT count(*) AS total_cart_items FROM cart_items;"
