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

    # --default-character-set: client MySQL tự dò bảng mã của hệ điều hành.
    # Trên Windows ngôn ngữ tiếng Việt, GetACP() trả 1258 mà MySQL không có
    # bảng mã tên 'cp1258', nên nó in ra stderr:
    #     mysqladmin: Unknown OS character set 'cp1258'.
    #     mysqladmin: Switching to the default character set 'utf8mb4'.
    # Chỉ định thẳng utf8mb4 thì khỏi phải dò. Các script sql-*.ps1 khác đã
    # truyền cờ này từ trước, riêng chỗ này bị bỏ sót.
    #
    # $ErrorActionPreference: đây mới là thứ thực sự làm script chết. Dưới
    # 'Stop', PowerShell 5.1 biến MỘT DÒNG stderr bất kỳ của lệnh native đã
    # gộp bằng 2>&1 thành lỗi kết thúc (NativeCommandError) ngay tại chỗ --
    # kể cả khi lệnh đó thoát với mã 0. Nghĩa là cảnh báo vô hại ở trên cũng
    # đủ giết script trước khi Where-Object kịp lọc, và hàm này báo thất bại
    # dù MySQL đã tắt xong. Hạ về 'Continue' đúng một lời gọi này.
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $admin --default-character-set=utf8mb4 -u $user -h 127.0.0.1 -P 3306 shutdown 2>&1 |
            Where-Object {
                $_ -notmatch 'Using a password' -and
                $_ -notmatch 'Unknown OS character set' -and
                $_ -notmatch 'Switching to the default character set'
            } |
            ForEach-Object { Write-Host $_ }
    } finally {
        $ErrorActionPreference = $prevEap
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
