# =====================================================================
#  mysql-stop.ps1 -- tắt MySQL một cách êm đẹp
# ---------------------------------------------------------------------
#  Nên dùng script này thay vì tắt bằng Task Manager: mysqladmin sẽ ghi
#  nốt dữ liệu trong bộ nhớ xuống đĩa trước khi thoát.
# =====================================================================

$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot

$base = Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64'

if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    Write-Host "MySQL khong chay." -ForegroundColor Yellow
    exit 0
}

& "$base\bin\mysqladmin.exe" -u root -proot -h 127.0.0.1 -P 3306 shutdown 2>&1 |
    Where-Object { $_ -notmatch 'Using a password' }

Start-Sleep -Seconds 2
if (Get-Process mysqld -ErrorAction SilentlyContinue) {
    Write-Host "[!!] MySQL van con chay." -ForegroundColor Yellow
} else {
    Write-Host "[OK] Da tat MySQL." -ForegroundColor Green
}
