# =====================================================================
#  mysql-start.ps1 -- khoi dong MySQL Server chay nen tren cong 3306
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64'
$data = Join-Path $base 'data'

if (-not (Test-Path "$base\bin\mysqld.exe")) {
    Write-Host "[LOI] Khong tim thay MySQL tai: $base" -ForegroundColor Red
    exit 1
}

$running = Get-Process mysqld -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[OK] MySQL Server dang chay san (PID $($running.Id))." -ForegroundColor Green
    exit 0
}

Write-Host "Dang khoi dong MySQL 8.0.45 tren cong 3306..." -ForegroundColor Cyan
Start-Process "$base\bin\mysqld.exe" -ArgumentList "--basedir=`"$base`"", "--datadir=`"$data`"", "--port=3306" -WindowStyle Hidden

# Doi cong 3306 san sang
$ready = $false
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    if (Get-Process mysqld -ErrorAction SilentlyContinue) {
        $ready = $true
        break
    }
}

if ($ready) {
    Write-Host "[OK] MySQL Server da khoi dong thanh cong tren cong 3306." -ForegroundColor Green
} else {
    Write-Host "[!!] MySQL khoi dong chua thanh cong. Kiem tra lai thu muc data." -ForegroundColor Yellow
}
