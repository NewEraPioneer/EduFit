$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$demoAsset = Join-Path $PSScriptRoot 'assets/defense-demo.pdf'
$demoRoots = @((Join-Path $repoRoot 'var/credentials'), (Join-Path $repoRoot 'backend/var/credentials'))
if ($env:STORAGE_CREDENTIALS_ROOT) { $demoRoots += $env:STORAGE_CREDENTIALS_ROOT }
foreach ($demoRoot in ($demoRoots | Select-Object -Unique)) {
    New-Item -ItemType Directory -Force -Path $demoRoot | Out-Null
    Copy-Item -LiteralPath $demoAsset -Destination (Join-Path $demoRoot 'defense-demo.pdf')
    Write-Output "Installed DEMO PDF in $demoRoot"
}
