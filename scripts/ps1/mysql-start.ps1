# =====================================================================
#  mysql-start.ps1 -- khởi động MySQL Server chạy nền trên cổng 3306
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# --- Kiểm tra cổng 3306 đã nhận kết nối chưa -------------------------
function Test-MySqlPort {
    param([int]$TimeoutMs = 800)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', 3306, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne($TimeoutMs, $false) -and $client.Connected) {
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

if (Test-MySqlPort) {
    $pids = (Get-Process mysqld -ErrorAction SilentlyContinue | ForEach-Object { $_.Id }) -join ', '
    if ($pids) {
        Write-Host "[OK] MySQL Server dang chay san tren cong 3306 (PID $pids)." -ForegroundColor Green
    } else {
        Write-Host "[OK] Cong 3306 da co dich vu MySQL lang nghe." -ForegroundColor Green
    }
    exit 0
}

# --- Tìm mysqld.exe --------------------------------------------------
$candidates = @(
    (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysqld.exe'),
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe',
    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe'
)
$mysqld = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $mysqld) {
    $mysqld = (Get-Command mysqld -ErrorAction SilentlyContinue).Source
}

# --- Nếu MySQL được cài dạng Windows Service thì ưu tiên dùng service -
$service = Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue |
    Where-Object { $_.Status -ne 'Running' } | Select-Object -First 1
if (-not $mysqld -and $service) {
    Write-Host "Dang khoi dong dich vu Windows: $($service.Name)..." -ForegroundColor Cyan
    try {
        Start-Service $service.Name
    } catch {
        Write-Host "[LOI] Khong khoi dong duoc dich vu $($service.Name): $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "      Dich vu Windows thuong doi quyen Administrator." -ForegroundColor Yellow
        exit 1
    }
}
elseif ($mysqld) {
    $base = Split-Path -Parent (Split-Path -Parent $mysqld)
    $data = Join-Path $base 'data'
    if (-not (Test-Path $data)) {
        Write-Host "[LOI] Khong tim thay thu muc data: $data" -ForegroundColor Red
        Write-Host "      Xem muc 'Cai dat MySQL' trong README.md." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Dang khoi dong MySQL tren cong 3306..." -ForegroundColor Cyan
    Write-Host "  mysqld : $mysqld" -ForegroundColor DarkGray
    Write-Host "  datadir: $data" -ForegroundColor DarkGray
    Start-Process $mysqld `
        -ArgumentList "--basedir=`"$base`"", "--datadir=`"$data`"", "--port=3306" `
        -WindowStyle Hidden
}
else {
    Write-Host "[LOI] Khong tim thay mysqld.exe. Da tim o:" -ForegroundColor Red
    $candidates | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
    Write-Host "      Xem muc 'Cai dat MySQL' trong README.md." -ForegroundColor Yellow
    exit 1
}

# --- Chờ cổng 3306 thật sự sẵn sàng (tối đa 20 giây) -----------------
for ($i = 1; $i -le 20; $i++) {
    Start-Sleep -Milliseconds 700
    if (Test-MySqlPort) {
        Write-Host "[OK] MySQL Server da san sang tren cong 3306." -ForegroundColor Green
        exit 0
    }
    Write-Host "  ... dang doi MySQL san sang ($i/20)" -ForegroundColor DarkGray
}

Write-Host "[LOI] MySQL chua nhan ket noi tren cong 3306 sau 14 giay." -ForegroundColor Red
Write-Host "      Kiem tra file .err trong thu muc data de biet nguyen nhan." -ForegroundColor Yellow
exit 1
