# =====================================================================
#  check-seed.ps1 -- chot bang so cho DU LIEU (14 bat bien)
# ---------------------------------------------------------------------
#  Khoi 5 chot bon hop dong cua GIAO DIEN bang code (check-contracts.ps1).
#  Khoi 6 chot nam hop dong cua DU LIEU bang SQL: sql/05_invariants.sql.
#  Dac ta: plan/15 muc 3 va muc 4.7.
#
#  Chay duoc BAT CU LUC NAO, khong chi sau khi nap seed -- ke ca sau khi
#  nguoi dung da bam chuot sua/xoa du lieu bang giao dien. Do la diem khac
#  quan trong nhat so voi "kiem luc nap seed".
#
#  Yeu cau: MySQL dang chay va database retail_billing ton tai. Script TU
#  NAP sql/05_invariants.sql truoc khi goi -- xem khoi comment o buoc 1.
#
#  Exit 0 = ca 14 quy tac deu 0 vi pham. Exit 1 = co vi pham (hoac khong
#  nap duoc file). KHONG ghi mot dong DU LIEU nao vao database (chi tao lai
#  thu tuc sp_check_seed).
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# --- Thong tin dang nhap: lay tu config.properties nhu sql-seed-data.ps1 ---
$user = 'root'
$pw = 'root'
$configPath = Join-Path $projectRoot 'config.properties'
if (Test-Path $configPath) {
    Get-Content $configPath | ForEach-Object {
        if ($_ -match '^\s*db\.user\s*=\s*(.+)$') { $user = $matches[1].Trim() }
        if ($_ -match '^\s*db\.password\s*=\s*(.+)$') { $pw = $matches[1].Trim() }
    }
}

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

if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    Write-Host "MySQL chua chay. Mo mot cua so PowerShell khac va chay:" -ForegroundColor Red
    Write-Host "    .\scripts\ps1\mysql-start.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "--- Kiem 14 bat bien cua du lieu (sp_check_seed) ---" -ForegroundColor Cyan
$env:MYSQL_PWD = $pw

try {
    # --- Buoc 1: tu nap 05_invariants.sql --------------------------------
    # Vi sao nap moi lan chay thay vi doi nguoi dung nap tay:
    #   01_schema.sql mo dau bang DROP DATABASE, nen MOI lan chay db-setup
    #   hoac bam "Tao database" trong Setup App la sp_check_seed bien mat.
    #   De script tu nap thi no chay duoc sau BAT KY duong dung database
    #   nao -- va khong phai them mot buoc vao hai duong nap do (duong cua
    #   Setup App thi khoi 6 cam khong duoc sua -- plan/15 muc 4.1 diem 2).
    # File nay chi CREATE mot thu tuc va co DROP ... IF EXISTS o dau, nen
    # nap lai bao nhieu lan cung xong va khong cham mot dong du lieu nao.
    Get-Content (Join-Path $projectRoot 'sql\05_invariants.sql') -Raw -Encoding UTF8 |
        & $mysql -u $user -h 127.0.0.1 -P 3306 `
                 --default-character-set=utf8mb4 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[LOI] Khong nap duoc sql\05_invariants.sql." -ForegroundColor Red
        Write-Host "      Database retail_billing da duoc tao chua? Chay .\scripts\ps1\db-setup.ps1" -ForegroundColor Yellow
        exit 1
    }

    # --- Buoc 2: goi thu tuc ---------------------------------------------
    # Mot lan goi duy nhat: thu tuc tra ve BANG (in ra cho nguoi doc) va dat
    # @v (tong so vi pham). Dong TONG_VI_PHAM=n la cho script doc con so --
    # goi thu tuc hai lan chi de lay lai con so la chay R1/R2 hai lan.
    $sql = @'
CALL sp_check_seed(@v);
SELECT CONCAT('TONG_VI_PHAM=', @v) AS ket_qua;
'@
    $out = $sql | & $mysql -u $user -h 127.0.0.1 -P 3306 `
                    --default-character-set=utf8mb4 --table retail_billing 2>&1
    $code = $LASTEXITCODE
} finally {
    $env:MYSQL_PWD = $null
}

$out | ForEach-Object { Write-Host $_ }

if ($code -ne 0) {
    Write-Host ""
    Write-Host "[LOI] Khong chay duoc sp_check_seed." -ForegroundColor Red
    exit 1
}

$total = $null
foreach ($line in $out) {
    if ("$line" -match 'TONG_VI_PHAM=(\d+)') { $total = [int]$matches[1] }
}

Write-Host ""
if ($null -eq $total) {
    Write-Host "[LOI] Khong doc duoc tong so vi pham tu ket qua." -ForegroundColor Red
    exit 1
}
if ($total -eq 0) {
    Write-Host "[OK] Ca 14 bat bien deu dat (0 vi pham)." -ForegroundColor Green
    exit 0
}
Write-Host "[LOI] Co $total vi pham -- xem cac dong 'SAI' o bang tren." -ForegroundColor Red
Write-Host "      Cot Hop_dong chi ra muc nao cua plan/15 dang bi vi pham." -ForegroundColor Yellow
exit 1
