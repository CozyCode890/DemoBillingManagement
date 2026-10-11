# =====================================================================
#  check-contracts.ps1 -- kiem bon hop dong cua tang giao dien bang code
# ---------------------------------------------------------------------
#  Kiem nhung thu "sai thi khong ai thay": doc so tien / so luong, cong
#  thuc giam gia, hop dong tien mat, va ma QR.
#  Dac ta: plan/14 muc 3 va docs/05 muc 7.
#
#  CHI cac kich ban KHONG can bam chuot. Cac kich ban tuong tac Swing
#  van phai kiem bang tay -- xem plan/14 muc 9.3.
#
#  Yeu cau: MySQL dang chay, database retail_billing co du lieu seed.
#  Co ghi vao database: lap 3 hoa don that roi XOA chung. Khong cham seed.
#
#  Exit 0 = tat ca dat. Exit 1 = co phep kiem sai (in ra dong FAIL).
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot

# --- 0. Kiem tra dieu kien tien quyet --------------------------------
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "Chua co JDK. Chay:  winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Red
    exit 1
}
$jar = Get-ChildItem (Join-Path $projectRoot 'lib\mysql-connector-j-*.jar') -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Host "Thieu driver MySQL trong lib\. Chay:  .\scripts\ps1\setup.ps1" -ForegroundColor Red
    exit 1
}
if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    Write-Host "MySQL chua chay. Mo mot cua so PowerShell khac va chay:" -ForegroundColor Red
    Write-Host "    .\scripts\ps1\mysql-start.ps1" -ForegroundColor Yellow
    exit 1
}

# --- 1. Bien dich app vao out\ ---------------------------------------
# ContractCheck goi vao NewInvoicePanel.splitPayment (package-private) nen
# no phai duoc bien dich NGOAI src\ roi dat canh out\ tren classpath.
Write-Host "Dang bien dich app..." -ForegroundColor Cyan
$javaFiles = Get-ChildItem -Path (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]envsetup[\\/]' }

$sources = Join-Path $env:TEMP 'billing-check-sources.txt'
$sourceLines = $javaFiles | ForEach-Object {
    $rel = $_.FullName.Substring($projectRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    "`"$rel`""
}
[System.IO.File]::WriteAllLines($sources, [string[]]$sourceLines, (New-Object System.Text.UTF8Encoding $false))

& javac -encoding UTF-8 -nowarn -d out -cp "lib\*" "@$sources"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[LOI] Bien dich app that bai." -ForegroundColor Red
    exit 1
}

# --- 2. Bien dich chuong trinh kiem ----------------------------------
Write-Host "Dang bien dich tools\check\ContractCheck.java..." -ForegroundColor Cyan
$checkOut = Join-Path $projectRoot 'out-check'
& javac -encoding UTF-8 -nowarn -d $checkOut -cp "out;lib\*" 'tools/check/ContractCheck.java'
if ($LASTEXITCODE -ne 0) {
    Write-Host "[LOI] Bien dich chuong trinh kiem that bai." -ForegroundColor Red
    exit 1
}

# --- 3. Chay -----------------------------------------------------------
# -Djava.awt.headless=true la BAT BUOC: ContractCheck phan biet "tu choi"
# voi "nhan gia tri" bang cach bat HeadlessException tu JOptionPane.
# Bo tham so nay di thi cac phep kiem duong "tu choi" se treo o mot hop
# thoai modal khong ai bam (xem javadoc cua ContractCheck).
Write-Host "Dang chay cac phep kiem..." -ForegroundColor Cyan
Write-Host ""
& java "-Dfile.encoding=UTF-8" "-Djava.awt.headless=true" -cp "out;lib\*;$checkOut" billing.ui.ContractCheck
$code = $LASTEXITCODE

Write-Host ""
if ($code -eq 0) {
    Write-Host "[OK] Tat ca phep kiem deu dat." -ForegroundColor Green
} else {
    Write-Host "[LOI] Co phep kiem sai -- xem cac dong FAIL o tren." -ForegroundColor Red
}
exit $code
