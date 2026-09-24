$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$user = 'root'
$pw = 'root'
$configPath = Join-Path $projectRoot 'config.properties'
if (Test-Path $configPath) {
    Get-Content $configPath | ForEach-Object {
        if ($_ -match '^\s*db\.user\s*=\s*(.+)$') { $user = $matches[1].Trim() }
        if ($_ -match '^\s*db\.password\s*=\s*(.+)$') { $pw = $matches[1].Trim() }
    }
}

$candidates = @(
    (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe'),
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
)
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql) { $mysql = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1 }
if (-not $mysql) { $mysql = 'mysql' }

Write-Host "Dang xoa database retail_billing..." -ForegroundColor Yellow
$env:MYSQL_PWD = $pw

try {
    & $mysql -u $user -h 127.0.0.1 -P 3306 -e "DROP DATABASE IF EXISTS retail_billing;"
    if ($LASTEXITCODE -ne 0) { throw "Loi khi xoa database" }
    Write-Host "[OK] Da xoa sach database retail_billing." -ForegroundColor Green
} finally {
    $env:MYSQL_PWD = $null
}
