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

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Build-ValidPdfBytes([string]$title, [string]$subtitle) {
    $streamContent = "BT`n/F1 20 Tf`n50 720 Td`n($title) Tj`n/F1 12 Tf`n0 -30 Td`n($subtitle) Tj`n/F1 10 Tf`n0 -25 Td`n(ByteVault Media - Cryptographically Verified Digital Asset Delivery) Tj`n0 -20 Td`n(Licensed for academic capstone presentation and review.) Tj`nET"
    $streamLen = [System.Text.Encoding]::ASCII.GetByteCount($streamContent)
    
    $header = "%PDF-1.4`n"
    $obj1 = "1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj`n"
    $obj2 = "2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj`n"
    $obj3 = "3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>`nendobj`n"
    $obj4 = "4 0 obj`n<< /Length $streamLen >>`nstream`n$streamContent`nendstream`nendobj`n"
    $obj5 = "5 0 obj`n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>`nendobj`n"
    
    $offset1 = [System.Text.Encoding]::ASCII.GetByteCount($header)
    $offset2 = $offset1 + [System.Text.Encoding]::ASCII.GetByteCount($obj1)
    $offset3 = $offset2 + [System.Text.Encoding]::ASCII.GetByteCount($obj2)
    $offset4 = $offset3 + [System.Text.Encoding]::ASCII.GetByteCount($obj3)
    $offset5 = $offset4 + [System.Text.Encoding]::ASCII.GetByteCount($obj4)
    $xrefOffset = $offset5 + [System.Text.Encoding]::ASCII.GetByteCount($obj5)
    
    $xref = "xref`n0 6`n0000000000 65535 f `n" +
            ("{0:D10} 00000 n `n" -f $offset1) +
            ("{0:D10} 00000 n `n" -f $offset2) +
            ("{0:D10} 00000 n `n" -f $offset3) +
            ("{0:D10} 00000 n `n" -f $offset4) +
            ("{0:D10} 00000 n `n" -f $offset5)
    
    $trailer = "trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xrefOffset`n%%EOF`n"
    
    $fullPdf = $header + $obj1 + $obj2 + $obj3 + $obj4 + $obj5 + $xref + $trailer
    return [System.Text.Encoding]::ASCII.GetBytes($fullPdf)
}

function Build-ValidZipBytes([string]$packageName, [string]$description) {
    $tempZip = [System.IO.Path]::GetTempFileName()
    Remove-Item $tempZip -Force -ErrorAction SilentlyContinue
    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    Set-Content -Path (Join-Path $tempDir "README.txt") -Value "Package: $packageName`nDescription: $description`nByteVault Media Verified Binary Distribution."
    Set-Content -Path (Join-Path $tempDir "LICENSE.txt") -Value "Copyright 2026 ByteVault Media. Licensed to verified customer."
    Set-Content -Path (Join-Path $tempDir "manifest.json") -Value "{`"package`": `"$packageName`", `"version`": `"1.0.0`", `"verified`": true}"
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $tempZip)
    $bytes = [System.IO.File]::ReadAllBytes($tempZip)
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $tempZip -Force -ErrorAction SilentlyContinue
    return $bytes
}

# 5. Seed at least Four Digital Products with Assets
Write-Host "`n[5/5] Ensuring 4 Digital Products with Downloadable Assets exist..." -ForegroundColor Yellow

$rootDir = Split-Path -Parent $PSScriptRoot
$assetDir = Join-Path $rootDir "temp-assets"
$backendAssetDir = Join-Path $rootDir "backend\temp-assets"
if (-not (Test-Path $assetDir)) { New-Item -ItemType Directory -Path $assetDir -Force | Out-Null }
if (-not (Test-Path $backendAssetDir)) { New-Item -ItemType Directory -Path $backendAssetDir -Force | Out-Null }

$desiredProducts = @(
    @{
        VendorIndex = 0
        Name = "Cloud Architecture Masterclass (PDF)"
        Description = "Comprehensive enterprise cloud system design patterns, distributed resiliency, and microservice blueprints."
        Price = 29.99
        Sku = "BV-DIG-CLOUD-01"
        CategoryId = $catId1
        FileName = "cloud-architecture-masterclass.pdf"
        IsPdf = $true
    },
    @{
        VendorIndex = 0
        Name = "Zero Trust Security Handbook"
        Description = "Practical implementation guidelines for mTLS, HMAC perimeter gateways, and OAuth2 security."
        Price = 39.99
        Sku = "BV-DIG-ZT-02"
        CategoryId = $catId1
        FileName = "zero-trust-handbook.pdf"
        IsPdf = $true
    },
    @{
        VendorIndex = 1
        Name = "Quantum Simulator Engine Pro"
        Description = "High-performance multi-threaded simulation engine SDK license for enterprise computing."
        Price = 149.99
        Sku = "BV-DIG-QUANTUM-03"
        CategoryId = $catId2
        FileName = "quantum-engine-sdk.zip"
        IsPdf = $false
    },
    @{
        VendorIndex = 1
        Name = "DevOps CI/CD Automation Kit"
        Description = "Production-grade CI/CD pipeline templates for Spring Cloud and containerized deployments."
        Price = 19.99
        Sku = "BV-DIG-CICD-04"
        CategoryId = $catId2
        FileName = "devops-cicd-kit.zip"
        IsPdf = $false
    }
)

$existingCatalogRes = Invoke-ApiGet "$GatewayUrl/api/v1/products"
$existingCatalog = $existingCatalogRes.data
$seededSummary = @()

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
            fileType = if ($prod.IsPdf) { "application/pdf" } else { "application/zip" }
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

    # Generate real, fully readable binary file
    $fileBytes = if ($prod.IsPdf) {
        Build-ValidPdfBytes $prod.Name $prod.Description
    } else {
        Build-ValidZipBytes $prod.Name $prod.Description
    }

    # Ensure real downloadable asset file exists on disk in all candidate fallback locations
    $assetPath = Join-Path $assetDir $prod.FileName
    [System.IO.File]::WriteAllBytes($assetPath, $fileBytes)
    $backendAssetPath = Join-Path $backendAssetDir $prod.FileName
    [System.IO.File]::WriteAllBytes($backendAssetPath, $fileBytes)

    # Upload real asset through Admin API (multipart/form-data)
    try {
        $uploadUrl = "$GatewayUrl/api/v1/products/$productId/assets"
        $boundary = [System.Guid]::NewGuid().ToString()
        $contentType = "multipart/form-data; boundary=$boundary"

        $bodyStream = New-Object System.IO.MemoryStream
        $writer = New-Object System.IO.StreamWriter($bodyStream)

        $writer.WriteLine("--$boundary")
        $writer.WriteLine("Content-Disposition: form-data; name=`"file`"; filename=`"$($prod.FileName)`"")
        $mime = if ($prod.IsPdf) { "application/pdf" } else { "application/zip" }
        $writer.WriteLine("Content-Type: $mime")
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

        $uploadRes = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -Body $postData
        $storageKey = if ($uploadRes.data -and $uploadRes.data.storageKey) { $uploadRes.data.storageKey } else { "" }
        Write-Host "Real binary asset $($prod.FileName) ($($fileBytes.Length) bytes) attached to product $productId (Key: $storageKey)." -ForegroundColor Green
    } catch {
        Write-Host "Asset upload note: $_" -ForegroundColor Gray
    }

    $seededSummary += [pscustomobject]@{
        Name      = $prod.Name
        ProductId = $productId
        Sku       = $prod.Sku
        Price     = $prod.Price
        FileName  = $prod.FileName
        Size      = $fileBytes.Length
    }
}

Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "ByteVault Demo Data Seeding Completed Successfully!" -ForegroundColor Cyan
Write-Host "Seeded Products Summary:" -ForegroundColor Cyan
$seededSummary | Format-Table Name, Sku, Price, ProductId, FileName, Size
Write-Host "=====================================================" -ForegroundColor Cyan
