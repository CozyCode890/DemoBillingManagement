# =====================================================================
#  db-setup.ps1 -- tạo database + nạp dữ liệu mẫu
# ---------------------------------------------------------------------
#  CẢNH BÁO: file 01_schema.sql bắt đầu bằng DROP DATABASE retail_billing.
#  Nếu bạn đã có database trùng tên thì nó sẽ bị XÓA TRẮNG.
#  Các database khác trên máy không bị ảnh hưởng.
#
#  Yêu cầu: MySQL phải đang chạy (mở cửa sổ khác chạy .\mysql-start.ps1)
# =====================================================================

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# --- Tim mysql.exe ----------------------------------------------------
$candidates = @(
    (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe'),
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
)
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql) { $mysql = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1 }
if (-not $mysql) {
    Write-Host "Khong tim thay mysql.exe. Xem muc 'Cai dat' trong README.md" -ForegroundColor Red
    exit 1
}
Write-Host "Dung: $mysql" -ForegroundColor DarkGray

# --- Kiem tra server da chay chua ------------------------------------
if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    Write-Host "MySQL chua chay. Mo mot cua so PowerShell khac va chay:" -ForegroundColor Red
    Write-Host "    .\mysql-start.ps1" -ForegroundColor Yellow
    exit 1
}

# --- Thong tin dang nhap ---------------------------------------------
$user = Read-Host "Ten dang nhap MySQL (Enter = root)"
if ([string]::IsNullOrWhiteSpace($user)) { $user = 'root' }

$pw = Read-Host "Mat khau (Enter = root)"
if ([string]::IsNullOrWhiteSpace($pw)) { $pw = 'root' }

# Dua mat khau qua bien moi truong MYSQL_PWD thay vi tham so -p<pass>.
# Ly do: tham so dong lenh hien ra trong Task Manager, bien moi truong thi khong.
$env:MYSQL_PWD = $pw

function Invoke-SqlFile($path, $label) {
    Write-Host "--- $label ---" -ForegroundColor Cyan
    Get-Content $path -Raw -Encoding UTF8 |
        & $mysql -u $user -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) {
        Write-Host "$label that bai." -ForegroundColor Red
        $env:MYSQL_PWD = $null
        exit 1
    }
}

Invoke-SqlFile '.\sql\01_schema.sql' 'Buoc 1/2: tao 15 bang'
Invoke-SqlFile '.\sql\02_seed.sql'   'Buoc 2/2: nap du lieu mau'

$env:MYSQL_PWD = $null

Write-Host ""
Write-Host "[OK] Database 'retail_billing' da san sang." -ForegroundColor Green
Write-Host "Kiem tra lai dong db.password trong config.properties truoc khi chay .\run.ps1" -ForegroundColor Yellow
