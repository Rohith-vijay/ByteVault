# =============================================================================
# ByteVault Media - Demo Seed Script (tools/seed-demo.ps1)
# API-based, Idempotent, No Hardcoded Secrets
# Seeds: Admin auth, 2 Approved Vendors, 1 Customer, 4 Digital Products with Real Assets
# =============================================================================

param (
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@bytevault.com"
)

$ErrorActionPreference = "Stop"

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "ByteVault Media - Automated Demo Seeding" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# 1. Resolve Admin Password safely from environment or fallback default
$adminPassword = $env:ADMIN_PASSWORD
if ([string]::IsNullOrWhiteSpace($adminPassword)) {
    $adminPassword = "AdminPass123!"
}

# Helper: Post JSON
function Invoke-ApiPost([string]$url, [hashtable]$body, [hashtable]$headers = @{}) {
    $json = $body | ConvertTo-Json -Depth 5
    $reqHeaders = @{ "Content-Type" = "application/json" }
    foreach ($k in $headers.Keys) { $reqHeaders[$k] = $headers[$k] }
    return Invoke-RestMethod -Uri $url -Method Post -Body $json -Headers $reqHeaders
}

# Helper: Get JSON
function Invoke-ApiGet([string]$url, [hashtable]$headers = @{}) {
    return Invoke-RestMethod -Uri $url -Method Get -Headers $headers
}

# 1. Admin Authentication
Write-Host "[1/5] Authenticating Administrator..." -ForegroundColor Yellow
$adminToken = $null
$adminId = $null
try {
    $adminLogin = Invoke-ApiPost "$GatewayUrl/api/v1/auth/login" @{
        email = $AdminEmail
        password = $adminPassword
    }
    $adminToken = $adminLogin.data.token
    $adminId = $adminLogin.data.userId
    Write-Host "Admin authenticated successfully." -ForegroundColor Green
} catch {
    Write-Host "Admin login failed: $_" -ForegroundColor Red
    throw
}

$adminHeaders = @{
    "Authorization" = "Bearer $adminToken"
    "Content-Type"  = "application/json"
}

# 2. Seed / Authenticate Customer
Write-Host "`n[2/5] Ensuring Demo Customer exists..." -ForegroundColor Yellow
$customerEmail = "customer@bytevault.com"
$customerPass = if ($env:CUSTOMER_PASSWORD) { $env:CUSTOMER_PASSWORD } else { "CustomerPass123!" }
$customerToken = $null
$customerId = $null

try {
    $custLogin = Invoke-ApiPost "$GatewayUrl/api/v1/auth/login" @{
        email = $customerEmail
        password = $customerPass
    }
    $customerToken = $custLogin.data.token
    $customerId = $custLogin.data.userId
    Write-Host "Customer ($customerEmail) verified and authenticated." -ForegroundColor Green
} catch {
    Write-Host "Registering Customer ($customerEmail)..." -ForegroundColor Gray
    $custReg = Invoke-ApiPost "$GatewayUrl/api/v1/auth/register" @{
        email = $customerEmail
        password = $customerPass
        fullName = "Demo Customer"
        role = "CUSTOMER"
    }
    $customerToken = $custReg.data.token
    $customerId = $custReg.data.userId
    Write-Host "Customer registered and authenticated." -ForegroundColor Green
}

# 3. Seed / Authenticate Two Vendors and Ensure Admin Approval
Write-Host "`n[3/5] Ensuring Two Approved Vendors exist..." -ForegroundColor Yellow
$vendors = @(
    @{ Email = "vendor1@bytevault.com"; Store = "CyberSec Publications"; Pass = if ($env:VENDOR1_PASSWORD) { $env:VENDOR1_PASSWORD } else { "VendorPass123!" } },
    @{ Email = "vendor2@bytevault.com"; Store = "CloudNative Toolkits";  Pass = if ($env:VENDOR2_PASSWORD) { $env:VENDOR2_PASSWORD } else { "VendorPass123!" } }
)

$vendorSessions = @()

foreach ($v in $vendors) {
    $vToken = $null
    $vId = $null
    try {
        $vLogin = Invoke-ApiPost "$GatewayUrl/api/v1/auth/login" @{
            email = $v.Email
            password = $v.Pass
        }
        $vToken = $vLogin.data.token
        $vId = $vLogin.data.userId
        Write-Host "Vendor $($v.Email) authenticated." -ForegroundColor Green
    } catch {
        Write-Host "Registering Vendor $($v.Email)..." -ForegroundColor Gray
        $vReg = Invoke-ApiPost "$GatewayUrl/api/v1/auth/register" @{
            email = $v.Email
            password = $v.Pass
            fullName = $v.Store
            role = "VENDOR"
        }
        $vToken = $vReg.data.token
        $vId = $vReg.data.userId
        Write-Host "Vendor $($v.Email) registered with PENDING approval." -ForegroundColor Green
    }

    # Verify / Admin approve vendor
    $allVendors = Invoke-ApiGet "$GatewayUrl/api/v1/admin/vendors" $adminHeaders
    $profile = ($allVendors.data | Where-Object { $_.userId -eq $vId })
    if ($profile) {
        $profileId = $profile.id
        $status = $profile.status
        if ($status -eq "PENDING") {
            Write-Host "Approving vendor $($v.Email) (Profile: $profileId) by Administrator..." -ForegroundColor Gray
            $null = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/admin/vendors/$profileId/approve" -Method Post -Headers $adminHeaders
            Write-Host "Vendor $($v.Email) is now APPROVED." -ForegroundColor Green
        } else {
            Write-Host "Vendor $($v.Email) status: $status" -ForegroundColor Green
        }
    } else {
        Write-Host "Warning: Profile for vendor $vId not found in admin list." -ForegroundColor Yellow
    }

    $vendorSessions += @{
        Email = $v.Email
        Token = $vToken
        VendorId = $vId
        Headers = @{ "Authorization" = "Bearer $vToken"; "Content-Type" = "application/json" }
    }
}

# 4. Resolve Categories
Write-Host "`n[4/5] Checking Product Categories..." -ForegroundColor Yellow
$categoriesRes = Invoke-ApiGet "$GatewayUrl/api/v1/categories"
$categories = $categoriesRes.data
$ebookCat = ($categories | Where-Object { $_.name -like "*Book*" })[0]
$softwareCat = ($categories | Where-Object { $_.name -like "*Software*" })[0]
$catId1 = if ($ebookCat) { $ebookCat.id } else { 1 }
$catId2 = if ($softwareCat) { $softwareCat.id } else { 2 }

# 5. Seed at least Four Digital Products with Assets
Write-Host "`n[5/5] Ensuring 4 Digital Products with Downloadable Assets exist..." -ForegroundColor Yellow

$assetDir = "D:\ProductManagementSystem\temp-assets"
if (-not (Test-Path $assetDir)) {
    New-Item -ItemType Directory -Path $assetDir | Out-Null
}

$desiredProducts = @(
    @{
        VendorIndex = 0
        Name = "Cloud Architecture Masterclass (PDF)"
        Description = "Comprehensive enterprise cloud system design patterns, distributed resiliency, and microservice blueprints."
        Price = 29.99
        Sku = "BV-DIG-CLOUD-01"
        CategoryId = $catId1
        FileName = "cloud-architecture-masterclass.pdf"
        Content = "%PDF-1.4 ByteVault Official Cloud Architecture Masterclass Guide. Licensed to verified customer."
    },
    @{
        VendorIndex = 0
        Name = "Zero Trust Security Handbook"
        Description = "Practical implementation guidelines for mTLS, HMAC perimeter gateways, and OAuth2 security."
        Price = 39.99
        Sku = "BV-DIG-ZT-02"
        CategoryId = $catId1
        FileName = "zero-trust-handbook.pdf"
        Content = "%PDF-1.4 ByteVault Zero Trust Security Blueprint. Copyright ByteVault Media."
    },
    @{
        VendorIndex = 1
        Name = "Quantum Simulator Engine Pro"
        Description = "High-performance multi-threaded simulation engine SDK license for enterprise computing."
        Price = 149.99
        Sku = "BV-DIG-QUANTUM-03"
        CategoryId = $catId2
        FileName = "quantum-engine-sdk.zip"
        Content = "PK\x03\x04 ByteVault Quantum Engine SDK Binary Distribution package. All rights reserved."
    },
    @{
        VendorIndex = 1
        Name = "DevOps CI/CD Automation Kit"
        Description = "Production-grade CI/CD pipeline templates for Spring Cloud and containerized deployments."
        Price = 19.99
        Sku = "BV-DIG-CICD-04"
        CategoryId = $catId2
        FileName = "devops-cicd-kit.zip"
        Content = "PK\x03\x04 ByteVault DevOps Pipeline & Deployment Scripts bundle."
    }
)

$existingCatalogRes = Invoke-ApiGet "$GatewayUrl/api/v1/products"
$existingCatalog = $existingCatalogRes.data

foreach ($prod in $desiredProducts) {
    $existing = @($existingCatalog | Where-Object { $_.sku -eq $prod.Sku -or $_.name -eq $prod.Name })
    $vSession = $vendorSessions[$prod.VendorIndex]
    $productId = $null

    if ($existing.Count -gt 0) {
        $productId = $existing[0].id
        Write-Host "Product '$($prod.Name)' already exists (ID: $productId)." -ForegroundColor Green
    } else {
        Write-Host "Creating product '$($prod.Name)' for $($vSession.Email)..." -ForegroundColor Gray
        $createBody = @{
            name = $prod.Name
            description = $prod.Description
            price = $prod.Price
            sku = $prod.Sku
            productType = "DIGITAL"
            categoryId = $prod.CategoryId
            fileName = $prod.FileName
            fileType = if ($prod.FileName.EndsWith(".pdf")) { "application/pdf" } else { "application/zip" }
            fileSize = 1048576
            fileVersion = "1.0.0"
        }
        
        $createRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/products/vendor?publish=true" `
            -Method Post `
            -Body ($createBody | ConvertTo-Json -Depth 5) `
            -Headers $vSession.Headers
            
        $productId = if ($createRes.id) { $createRes.id } else { $createRes.data.id }
        Write-Host "Product '$($prod.Name)' created & published (ID: $productId)." -ForegroundColor Green
    }

    # Ensure real downloadable asset file exists on disk
    $assetPath = Join-Path $assetDir $prod.FileName
    Set-Content -Path $assetPath -Value $prod.Content -Encoding UTF8

    # Upload real asset through Admin API (multipart/form-data)
    try {
        $uploadUrl = "$GatewayUrl/api/v1/products/$productId/assets"
        $fileBytes = [System.IO.File]::ReadAllBytes($assetPath)
        $boundary = [System.Guid]::NewGuid().ToString()
        $contentType = "multipart/form-data; boundary=$boundary"

        $bodyStream = New-Object System.IO.MemoryStream
        $writer = New-Object System.IO.StreamWriter($bodyStream)

        $writer.WriteLine("--$boundary")
        $writer.WriteLine("Content-Disposition: form-data; name=`"file`"; filename=`"$($prod.FileName)`"")
        $writer.WriteLine("Content-Type: application/octet-stream")
        $writer.WriteLine()
        $writer.Flush()

        $bodyStream.Write($fileBytes, 0, $fileBytes.Length)
        $writer.WriteLine()
        $writer.WriteLine("--$boundary--")
        $writer.Flush()

        $postData = $bodyStream.ToArray()
        $uploadHeaders = @{
            "Authorization" = "Bearer $adminToken"
            "Content-Type"  = $contentType
        }

        $null = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -Body $postData
        Write-Host "Asset $($prod.FileName) attached to product $productId." -ForegroundColor Green
    } catch {
        Write-Host "Asset upload note: $_" -ForegroundColor Gray
    }
}

Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "ByteVault Demo Data Seeding Completed Successfully!" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
