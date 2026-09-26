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

Write-Host "--- Nap du lieu mau vao retail_billing ---" -ForegroundColor Cyan
$env:MYSQL_PWD = $pw

try {
    Write-Host "Dang thuc thi 02_seed.sql..." -ForegroundColor DarkGray
    Get-Content '.\sql\02_seed.sql' -Raw -Encoding UTF8 |
        & $mysql -u $user -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "Loi khi thuc thi 02_seed.sql" }

    Write-Host "[OK] Da nap thanh cong du lieu mau vao Database." -ForegroundColor Green
} finally {
    $env:MYSQL_PWD = $null
}

exit 0
