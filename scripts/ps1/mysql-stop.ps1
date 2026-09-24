# =====================================================================
#  mysql-stop.ps1 -- tắt MySQL một cách êm đẹp
# ---------------------------------------------------------------------
#  Nên dùng script này thay vì tắt bằng Task Manager: mysqladmin sẽ ghi
#  nốt dữ liệu trong bộ nhớ xuống đĩa trước khi thoát.
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    Write-Host "MySQL khong chay." -ForegroundColor Yellow
    exit 0
}

# --- Tài khoản lấy từ config.properties ------------------------------
$user = 'root'
$pw   = 'root'
$configPath = Join-Path $projectRoot 'config.properties'
if (Test-Path $configPath) {
    Get-Content $configPath -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*db\.user\s*=\s*(.+)$')     { $user = $matches[1].Trim() }
        if ($_ -match '^\s*db\.password\s*=\s*(.+)$') { $pw   = $matches[1].Trim() }
    }
}

# --- Tìm mysqladmin.exe ----------------------------------------------
$candidates = @(
    (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysqladmin.exe'),
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe',
    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqladmin.exe'
)
$admin = (Get-Command mysqladmin -ErrorAction SilentlyContinue).Source
if (-not $admin) { $admin = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1 }

if ($admin) {
    # Mật khẩu đi qua biến môi trường MYSQL_PWD, không đưa vào dòng lệnh.
    $env:MYSQL_PWD = $pw
    try {
        & $admin -u $user -h 127.0.0.1 -P 3306 shutdown 2>&1 |
            Where-Object { $_ -notmatch 'Using a password' } |
            ForEach-Object { Write-Host $_ }
    } finally {
        $env:MYSQL_PWD = $null
    }
} else {
    Write-Host "[!!] Khong tim thay mysqladmin.exe, chuyen sang dung tien trinh mysqld." -ForegroundColor Yellow
    Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force
}

# --- Chờ tiến trình thoát hẳn ----------------------------------------
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Milliseconds 500
    if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
        Write-Host "[OK] Da tat MySQL." -ForegroundColor Green
        exit 0
    }
}

Write-Host "[LOI] MySQL van con chay. Kiem tra lai tai khoan trong config.properties." -ForegroundColor Red
exit 1
