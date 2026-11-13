# =====================================================================
#  sql-wipe-data.ps1 -- Xoa DU LIEU cua retail_billing, giu nguyen 16
#  BANG va moi ham / thu tuc. Dac ta: plan/16.
# ---------------------------------------------------------------------
#  Hai muc (plan/16 muc 3.1):
#    transactions  xoa 7 bang giao dich, giu 9 bang danh muc + tai khoan
#                  -> CALL sp_wipe_transactions()
#    all           xoa ca 16 bang, ke ca Account
#                  -> CALL sp_wipe_all_data()
#  Thu tu xoa KHONG nam o day ma o 04_routines.sql phan 6 (mot ban chep).
#
#  Cach dung:
#    .\scripts\ps1\sql-wipe-data.ps1 -Scope transactions
#    .\scripts\ps1\sql-wipe-data.ps1 -Scope all
#  Setup App truyen pham vi qua bien moi truong WIPE_SCOPE (giong
#  BILLING_LOGIN cua run.ps1). Tham so -Scope thang bien moi truong.
#
#  KHONG co pham vi mac dinh va KHONG hoi xac nhan: thieu pham vi la tu
#  choi. Mot script xoa du lieu ma tu doan "chac la xoa het" la mot cai
#  bay. Xac nhan la viec cua hop thoai trong Setup App; chay tay thi viec
#  phai go -Scope da la mot lan xac nhan.
#
#  Exit 0 = da xoa xong. Exit 1 = loi (MySQL tat, chua co database, mot
#  lenh DELETE that bai). Exit 2 = thieu / sai pham vi, KHONG xoa gi.
# =====================================================================

param(
    [string]$Scope = $env:WIPE_SCOPE
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# Ong pipe sang mysql phai la UTF-8 KHONG BOM. [Text.Encoding]::UTF8 co
# BOM, va Windows PowerShell 5.1 -- chinh la powershell.exe ma Setup App
# goi -- chen BOM do vao dau chuoi pipe sang lenh ngoai, nen mysql bao
# ERROR 1064 ngay dong 1. pwsh 7 khong chen, nen chay tay bang pwsh se
# khong thay loi nay (plan/16 muc 10.2).
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

# --- Buoc 0: pham vi -- kiem TRUOC khi cham toi MySQL ------------------
$procs = @{ 'transactions' = 'sp_wipe_transactions'; 'all' = 'sp_wipe_all_data' }
$key = "$Scope".Trim().ToLowerInvariant()
if (-not $procs.ContainsKey($key)) {
    Write-Host "[LOI] Thieu hoac sai pham vi xoa: '$Scope'. Khong xoa gi." -ForegroundColor Red
    Write-Host "      Cach dung:" -ForegroundColor Yellow
    Write-Host "        .\scripts\ps1\sql-wipe-data.ps1 -Scope transactions   # chi du lieu giao dich" -ForegroundColor Yellow
    Write-Host "        .\scripts\ps1\sql-wipe-data.ps1 -Scope all            # toan bo 16 bang" -ForegroundColor Yellow
    exit 2
}
$proc = $procs[$key]

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
    Write-Host "[LOI] MySQL chua chay. Bat MySQL o tab Chung, hoac chay:" -ForegroundColor Red
    Write-Host "    .\scripts\ps1\mysql-start.ps1" -ForegroundColor Yellow
    exit 1
}

# --- Bang dem so dong cua 16 bang -------------------------------------
# Dung COUNT(*) chu khong dung information_schema.TABLES.TABLE_ROWS: voi
# InnoDB cot do chi la UOC LUONG, va MySQL 8 con cache no 24 gio
# (information_schema_stats_expiry), nen ngay sau khi xoa no van hien so
# cu. Viet MOT lan, dung cho ca cot Truoc lan cot Sau.
# Day la ban chep chi de HIEN THI: lech thi bao cao thieu dong, du lieu
# khong sai. Them bang moi vao 01_schema.sql thi them ca o day (va o
# sql-wipe-data.sh) -- xem 04_routines.sql phan 6.
$dem = @'
          SELECT  1 AS Thu_tu, 'GIAO DICH' AS Nhom, 'Invoice' AS Bang, COUNT(*) AS So_dong FROM Invoice
UNION ALL SELECT  2, 'GIAO DICH', 'Invoice_Line',    COUNT(*) FROM Invoice_Line
UNION ALL SELECT  3, 'GIAO DICH', 'Return',          COUNT(*) FROM `Return`
UNION ALL SELECT  4, 'GIAO DICH', 'Payment',         COUNT(*) FROM Payment
UNION ALL SELECT  5, 'GIAO DICH', 'Cash_Payment',    COUNT(*) FROM Cash_Payment
UNION ALL SELECT  6, 'GIAO DICH', 'Card_Payment',    COUNT(*) FROM Card_Payment
UNION ALL SELECT  7, 'GIAO DICH', 'EWallet_Payment', COUNT(*) FROM EWallet_Payment
UNION ALL SELECT  8, 'DANH MUC',  'Shop',            COUNT(*) FROM Shop
UNION ALL SELECT  9, 'DANH MUC',  'Counter',         COUNT(*) FROM Counter
UNION ALL SELECT 10, 'DANH MUC',  'Cashier',         COUNT(*) FROM Cashier
UNION ALL SELECT 11, 'DANH MUC',  'Customer',        COUNT(*) FROM Customer
UNION ALL SELECT 12, 'DANH MUC',  'Product',         COUNT(*) FROM Product
UNION ALL SELECT 13, 'DANH MUC',  'Price_History',   COUNT(*) FROM Price_History
UNION ALL SELECT 14, 'DANH MUC',  'Promotion',       COUNT(*) FROM Promotion
UNION ALL SELECT 15, 'DANH MUC',  'Applies_To',      COUNT(*) FROM Applies_To
UNION ALL SELECT 16, 'DANH MUC',  'Account',         COUNT(*) FROM Account
'@

if ($key -eq 'all') {
    Write-Host "--- Xoa TOAN BO du lieu cua retail_billing (16 bang) ---" -ForegroundColor Cyan
} else {
    Write-Host "--- Xoa du lieu GIAO DICH cua retail_billing (7 bang) ---" -ForegroundColor Cyan
}
$env:MYSQL_PWD = $pw

try {
    # --- Buoc 1: database co ton tai? -----------------------------------
    # Hoi information_schema chu khong USE retail_billing: chua co database
    # thi cau nay van chay, va ta bao duoc mot loi de hieu.
    $hasDb = & $mysql -u $user -h 127.0.0.1 -P 3306 -N -B `
        -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'retail_billing';"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[LOI] Khong ket noi duoc MySQL bang tai khoan '$user' (xem config.properties)." -ForegroundColor Red
        exit 1
    }
    if ("$hasDb".Trim() -eq '0') {
        Write-Host "[LOI] Chua co database retail_billing -- khong co gi de xoa." -ForegroundColor Red
        Write-Host "      Bam 'Tao database': database moi tao von da TRONG (01 + 04, khong seed)." -ForegroundColor Yellow
        exit 1
    }

    # --- Buoc 2: hai thu tuc da co chua? --------------------------------
    # Database tao TRUOC plan/16 thi chua co. Tu nap lai 04_routines.sql
    # thay vi bat bam "Tao database" (thuc chat la bat xoa muc 'all' kem
    # theo routine). Cung ly do check-seed.ps1 tu nap 05_invariants.sql:
    # file do chi DROP / CREATE ham va thu tuc, khong cham mot dong du lieu.
    $found = & $mysql -u $user -h 127.0.0.1 -P 3306 -N -B `
        -e ("SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = 'retail_billing' " +
            "AND ROUTINE_TYPE = 'PROCEDURE' AND ROUTINE_NAME IN ('sp_wipe_transactions', 'sp_wipe_all_data');")
    if ("$found".Trim() -ne '2') {
        Write-Host "[!!] Database chua co sp_wipe_* (duoc tao truoc plan/16). Nap lai sql\04_routines.sql --" -ForegroundColor Yellow
        Write-Host "     file do chi tao lai ham / thu tuc, khong cham mot dong du lieu nao." -ForegroundColor Yellow
        Get-Content '.\sql\04_routines.sql' -Raw -Encoding UTF8 |
            & $mysql -u $user -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[LOI] Khong nap duoc sql\04_routines.sql." -ForegroundColor Red
            exit 1
        }
    }

    # --- Buoc 3: dem -> xoa -> dem lai, trong CUNG mot phien ------------
    # Bang tam tmp_truoc chi song trong phien nay, nen ca lo phai di qua
    # MOT lan goi mysql.
    $sql = "CREATE TEMPORARY TABLE tmp_truoc AS`n$dem;`n" +
           "CALL $proc();`n" +
           "SELECT t.Nhom, t.Bang, t.So_dong AS Truoc, s.So_dong AS Sau`n" +
           "FROM   tmp_truoc t`n" +
           "JOIN   ($dem) s ON s.Bang = t.Bang`n" +
           "ORDER  BY t.Thu_tu;`n"
    $sql | & $mysql -u $user -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 -t retail_billing
    if ($LASTEXITCODE -ne 0) { throw "Loi khi chay $proc" }
} finally {
    $env:MYSQL_PWD = $null
}

if ($key -eq 'all') {
    Write-Host "[OK] Da xoa toan bo du lieu. 16 bang va moi ham / thu tuc giu nguyen." -ForegroundColor Green
    Write-Host "     Bang Account dang trong: app billing chua dang nhap duoc cho toi khi seed them tai khoan." -ForegroundColor Yellow
} else {
    Write-Host "[OK] Da xoa du lieu giao dich. 9 bang danh muc va tai khoan giu nguyen." -ForegroundColor Green
}
Write-Host "Buoc tiep: chay seed cua ban (hoac 'Tao du lieu mau'), roi kiem bang .\scripts\ps1\check-seed.ps1" -ForegroundColor DarkGray
Write-Host "     R12 (91 ngay deu co hoa don) la cam ket rieng cua seed chinh thuc -- seed tu viet khong phu du thi R12 khac 0." -ForegroundColor DarkGray

exit 0
