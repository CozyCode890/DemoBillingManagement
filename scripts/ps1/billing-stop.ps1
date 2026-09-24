# =====================================================================
#  billing-stop.ps1 -- Dừng an toàn tiến trình Java của ứng dụng
# =====================================================================

$ErrorActionPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$targetClass = 'billing.Main'
$configPath = Join-Path $projectRoot 'config.properties'
if (Test-Path $configPath) {
    Get-Content $configPath | ForEach-Object {
        if ($_ -match '^\s*app\.main\.class\s*=\s*(.+)$') {
            $targetClass = $matches[1].Trim()
        }
    }
}

Write-Host "Dang kiem tra tien trinh ung dung Java ($targetClass)..." -ForegroundColor Cyan

$procs = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -and (
        $_.CommandLine -like "*$targetClass*" -or
        $_.CommandLine -like "*out;lib\**"
    )
}

if ($procs) {
    foreach ($p in $procs) {
        Stop-Process -Id $p.ProcessId -Force
        Write-Host "[OK] Da dung tien trinh Java (PID $($p.ProcessId))." -ForegroundColor Green
    }
} else {
    Write-Host "Khong co tien trinh ung dung nao dang chay." -ForegroundColor DarkGray
}
