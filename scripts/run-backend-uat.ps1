# =============================================================================
# EduFit Backend UAT Local Launcher (PowerShell)
# =============================================================================

[CmdletBinding()]
param (
    [string]$EnvFile,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
if (-not $EnvFile) {
    $EnvFile = "$RepoRoot\.env.uat.local"
}

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "       EduFit Backend - Supabase UAT Launcher     " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 1. Phat hien va cau hinh JDK 21
$JavaCandidates = @(
    $env:JAVA_HOME,
    "C:\Program Files\Java\jdk-21.0.10",
    "C:\Program Files\Java\latest",
    "$env:USERPROFILE\.jdks\ms-21.0.9"
)

$SelectedJavaHome = $null
foreach ($Candidate in $JavaCandidates) {
    if ($Candidate -and (Test-Path "$Candidate\bin\java.exe")) {
        $PrevEap = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        $VersionOutput = cmd.exe /c "`"$Candidate\bin\java.exe`" -version 2>&1"
        $ErrorActionPreference = $PrevEap
        if ($VersionOutput -match 'version "(21|22|23|24)') {
            $SelectedJavaHome = $Candidate
            break
        }
    }
}

if (-not $SelectedJavaHome) {
    Write-Error "Khong tim thay JDK 21+. Vui long cai dat JDK 21 hoac thiet lap bien JAVA_HOME."
    exit 1
}

$env:JAVA_HOME = $SelectedJavaHome
$env:PATH = "$SelectedJavaHome\bin;$env:PATH"
Write-Host "[OK] Da chon JDK: $SelectedJavaHome" -ForegroundColor Green

# 2. Kiem tra file cau hinh .env.uat.local
if (-not (Test-Path $EnvFile)) {
    Write-Host "[CANH BAO] Khong tim thay file: $EnvFile" -ForegroundColor Yellow
    Write-Host "Vui long sao chep file mau va dien thong tin Supabase UAT:" -ForegroundColor Yellow
    Write-Host "  Copy-Item .env.uat.example .env.uat.local" -ForegroundColor White
    Write-Host "Sau do dien DB_URL (hoac DB_HOST, DB_USER, DB_PASSWORD) vao .env.uat.local roi chay lai." -ForegroundColor Yellow
    exit 1
}

Write-Host "[OK] Doc cau hinh tu: $EnvFile" -ForegroundColor Green

# 3. Nap bien moi truong tu .env.uat.local vao process
$LoadedVars = @{}
Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and (-not $line.StartsWith("#"))) {
        $parts = $line -split "=", 2
        if ($parts.Length -eq 2) {
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
                $val = $val.Substring(1, $val.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($key, $val, "Process")
            $LoadedVars[$key] = $val
        }
    }
}

# Dam bao profile UAT duoc kich hoat
$env:SPRING_PROFILES_ACTIVE = "uat"

# 4. Kiem tra an toan truoc khi chay
$DbUrl = [Environment]::GetEnvironmentVariable("DB_URL")
$DbHost = [Environment]::GetEnvironmentVariable("DB_HOST")
$DbUser = [Environment]::GetEnvironmentVariable("DB_USERNAME")
if (-not $DbUser) { $DbUser = [Environment]::GetEnvironmentVariable("DB_USER") }
$DbPort = [Environment]::GetEnvironmentVariable("DB_PORT")
if (-not $DbPort) { $DbPort = "5432" }
$DbName = [Environment]::GetEnvironmentVariable("DB_NAME")
if (-not $DbName) { $DbName = "postgres" }

if (-not $DbUrl -and -not $DbHost) {
    Write-Error "Thieu cau hinh ket noi database! Can khai bao DB_URL hoac DB_HOST trong $EnvFile."
    exit 1
}

# Kiem tra an toan: Tuyet doi khong tro vao database Production trong launcher UAT
if (($DbUrl -match "prod|production") -or ($DbHost -match "prod|production")) {
    Write-Error "CANH BAO BAO MAT: DB_URL hoac DB_HOST chua tu khoa 'prod/production'. Launcher UAT tu choi ket noi!"
    exit 1
}

# Hien thi thong tin ket noi (an mat khau)
$DisplayTarget = if ($DbUrl) {
    $DbUrl -replace ':[^@/]+@', ':****@'
} else {
    "${DbUser}@${DbHost}:${DbPort}/${DbName}"
}

Write-Host "-------------------------------------------------" -ForegroundColor Cyan
Write-Host "  Moi truong:  UAT" -ForegroundColor Yellow
Write-Host "  Profile:     $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Yellow
Write-Host "  Muc tieu DB: $DisplayTarget" -ForegroundColor Yellow
Write-Host "  Flyway:      chi chay db/migration (KHONG nap seed demo)" -ForegroundColor Yellow
Write-Host "-------------------------------------------------" -ForegroundColor Cyan

if ($CheckOnly) {
    Write-Host "[OK] Kiem tra cau hinh va JDK thanh cong (CheckOnly)." -ForegroundColor Green
    exit 0
}

# 5. Khoi chay Backend voi profile uat
Set-Location "$RepoRoot\backend"
Write-Host "Dang khoi dong backend Spring Boot qua Maven Wrapper..." -ForegroundColor Cyan

& ".\mvnw.cmd" spring-boot:run -Dspring-boot.run.profiles=uat
