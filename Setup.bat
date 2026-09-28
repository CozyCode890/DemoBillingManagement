@echo off
rem =====================================================================
rem  Setup.bat -- Launcher 1-click cho Windows.
rem  Bam dup vao file nay de mo giao dien quan ly moi truong.
rem =====================================================================
setlocal
cd /d "%~dp0"

if not exist "%~dp0SetupApp.jar" goto :no_jar

rem Uu tien javaw trong PATH (khong hien cua so cmd den).
where javaw >nul 2>&1
if %errorlevel% equ 0 (
    start "" javaw -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
    exit /b 0
)

rem Du phong: lay javaw tu JAVA_HOME.
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\javaw.exe" (
    start "" "%JAVA_HOME%\bin\javaw.exe" -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
    exit /b 0
)

echo.
echo   [LOI] Khong tim thay Java (javaw) tren may nay.
echo.
echo   Cai JDK 21 bang lenh sau trong PowerShell:
echo       winget install EclipseAdoptium.Temurin.21.JDK
echo.
echo   Cai xong nho MO LAI cua so roi chay lai Setup.bat.
echo.
pause
exit /b 1

:no_jar
echo.
echo   [LOI] Khong tim thay SetupApp.jar ben canh file Setup.bat.
echo.
echo   Dong goi lai bang lenh:
echo       powershell -ExecutionPolicy Bypass -File scripts\ps1\build-setup.ps1
echo.
pause
exit /b 1
