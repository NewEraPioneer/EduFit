# =============================================================================
# EduFit Frontend Local Launcher (PowerShell)
# =============================================================================

[CmdletBinding()]
param (
    [int]$Port = 3000,
    [string]$BackendUrl = "https://edufit-255v.onrender.com"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$FrontendDir = Join-Path $RepoRoot "frontend"

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "       EduFit Frontend - Local Dev Launcher      " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 1. Kiem tra va khoi tao frontend/.env.local neu chua co
$EnvLocalFile = Join-Path $FrontendDir ".env.local"
if (-not (Test-Path $EnvLocalFile)) {
    Write-Host "[INFO] Khoi tao $EnvLocalFile voi BACKEND_URL=$BackendUrl..." -ForegroundColor Yellow
    Set-Content -Path $EnvLocalFile -Value "BACKEND_URL=$BackendUrl`n" -Encoding utf8
} else {
    Write-Host "[OK] Da tim thay: $EnvLocalFile" -ForegroundColor Green
}

# 2. Tu dong kiem tra va xu ly chung chi Avast Antivirus (neu co)
try {
    $AvastCert = Get-ChildItem Cert:\LocalMachine\Root -ErrorAction SilentlyContinue | Where-Object { $_.Subject -match "Avast" } | Select-Object -First 1
    if ($AvastCert) {
        $PemPath = Join-Path $FrontendDir "avast-ca.pem"
        $Bytes = $AvastCert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert)
        $PemContent = "-----BEGIN CERTIFICATE-----`r`n" + [System.Convert]::ToBase64String($Bytes, [System.Base64FormattingOptions]::InsertLineBreaks) + "`r`n-----END CERTIFICATE-----`r`n"
        Set-Content -Path $PemPath -Value $PemContent -Encoding ascii
        $env:NODE_EXTRA_CA_CERTS = $PemPath
        Write-Host "[OK] Phat hien Avast Web Shield: Da nap chung chi tuong thich cho Node.js ($PemPath)" -ForegroundColor Green
    }
} catch {
    Write-Warning "Khong the kiem tra chung chi Avast: $_"
}

# 3. Kiem tra ket noi den Backend Render
Write-Host "[INFO] Kiem tra ket noi den Backend ($BackendUrl)..." -ForegroundColor Cyan
try {
    $HealthRes = Invoke-RestMethod -Uri "$BackendUrl/actuator/health" -TimeoutSec 10 -ErrorAction Stop
    Write-Host "[OK] Backend dang hoat dong tot (Status: $($HealthRes.status))" -ForegroundColor Green
} catch {
    Write-Warning "Khong the ket noi truc tiep den Backend qua Actuator Health: $_"
    Write-Warning "Vui long kiem tra lai trang thai service tren Render Dashboard."
}

# 4. Khoi dong Next.js
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Dang khoi dong Next.js tai http://localhost:$Port ..." -ForegroundColor Yellow
Write-Host "=================================================" -ForegroundColor Cyan

Set-Location $FrontendDir
& npm run dev -- -p $Port
