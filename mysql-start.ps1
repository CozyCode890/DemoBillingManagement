# =====================================================================
#  mysql-start.ps1 -- bật MySQL lên
# ---------------------------------------------------------------------
#  MySQL trên máy bạn được cài theo kiểu "portable" (giải nén từ file
#  ZIP), KHÔNG đăng ký thành Windows Service. Nghĩa là:
#
#    - Nó KHÔNG tự chạy khi bật máy.
#    - Mỗi lần muốn dùng app thì chạy script này trước.
#    - Đóng cửa sổ PowerShell này thì MySQL tắt theo.
#
#  Vì sao không cài thành service? Vì bước đó cần quyền Administrator.
#  Khi nào bạn muốn, xem hướng dẫn ở cuối README.md.
# =====================================================================

$ErrorActionPreference = 'Stop'

$base = Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64'
$data = Join-Path $base 'data'

if (-not (Test-Path "$base\bin\mysqld.exe")) {
    Write-Host "Khong tim thay MySQL tai: $base" -ForegroundColor Red
    Write-Host "Xem lai muc 'Cai dat' trong README.md" -ForegroundColor Red
    exit 1
}

# Da chay roi thi thoi
$running = Get-Process mysqld -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[OK] MySQL dang chay san roi (PID $($running.Id))." -ForegroundColor Green
    exit 0
}

Write-Host "Dang bat MySQL 8.0.45 tren cong 3306..." -ForegroundColor Cyan
Write-Host "(De cua so nay MO. Dong lai la MySQL tat.)" -ForegroundColor Yellow
Write-Host ""

& "$base\bin\mysqld.exe" "--basedir=$base" "--datadir=$data" --port=3306 --console
