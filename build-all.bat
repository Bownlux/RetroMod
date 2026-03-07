@echo off
REM ============================================================================
REM RetroMod Multi-Version Build Script (Windows)
REM Copyright (c) 2026 RevivalSMP. MIT License.
REM
REM Builds RetroMod for ALL loaders and ALL supported MC versions:
REM   - Fabric (1.14 through 1.21.11)
REM   - Forge (1.12.2 through 1.21.11)
REM   - NeoForge (1.20.1 through 1.21.11)
REM   - CLI tool (standalone)
REM ============================================================================

setlocal enabledelayedexpansion

set VERSION=1.0.0-beta.1
set MC_VERSIONS=1.12.2 1.13 1.13.1 1.13.2 1.14 1.14.1 1.14.2 1.14.3 1.14.4 1.15 1.15.1 1.15.2 1.16 1.16.1 1.16.2 1.16.3 1.16.4 1.16.5 1.17 1.17.1 1.18 1.18.1 1.18.2 1.19 1.19.1 1.19.2 1.19.3 1.19.4 1.20 1.20.1 1.20.2 1.20.3 1.20.4 1.20.5 1.20.6 1.21 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11
set LOADERS=fabric forge neoforge

echo ============================================
echo   RetroMod Multi-Version Build Script
echo   Version: %VERSION%
echo   MIT License - RevivalSMP
echo ============================================
echo.

REM Check for Maven
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven not found!
    echo Install: https://maven.apache.org/install.html
    exit /b 1
)

REM Check for --skip-build flag (used when called from Maven)
set SKIP_BUILD=0
if "%1"=="--skip-build" set SKIP_BUILD=1

if %SKIP_BUILD%==0 (
    echo [Step 1] Building base JAR with Maven...
    call mvn clean package -DskipTests -q
    if %ERRORLEVEL% neq 0 (
        echo ERROR: Maven build failed!
        exit /b 1
    )
)

REM Find the shaded JAR (with all dependencies bundled)
set SHADED_JAR=target\retromod-%VERSION%-all.jar
if not exist "%SHADED_JAR%" (
    echo ERROR: Shaded JAR not found at %SHADED_JAR%
    echo Make sure maven-shade-plugin ran successfully.
    exit /b 1
)

echo   Shaded JAR: %SHADED_JAR% (with bundled dependencies)

REM Create output directories
mkdir dist\Fabric 2>nul
mkdir dist\Forge 2>nul
mkdir dist\NeoForge 2>nul
mkdir dist\CLI 2>nul

echo.
echo [Step 2] Creating CLI tool...

copy /Y "%SHADED_JAR%" "dist\CLI\retromod-%VERSION%-cli.jar" >nul
echo   Created: dist\CLI\retromod-%VERSION%-cli.jar

echo.
echo [Step 3] Creating loader-specific JARs...

set TOTAL=0
set FAILED=0

for %%L in (%LOADERS%) do (
    echo.
    if "%%L"=="fabric" echo Building Fabric JARs...
    if "%%L"=="forge" echo Building Forge JARs...
    if "%%L"=="neoforge" echo Building NeoForge JARs...

    for %%V in (%MC_VERSIONS%) do (
        call :create_mod_jar %%L %%V
    )
)

echo.
echo [Step 4] Done!
echo.
echo ============================================
echo   Build Complete!
echo ============================================
echo.

REM Count JARs
set /a JAR_COUNT=0
for /r dist %%f in (*.jar) do set /a JAR_COUNT+=1
echo   Total JARs: %JAR_COUNT%

if %FAILED% gtr 0 (
    echo   WARNING: %FAILED% JAR(s) failed to build
)

echo.
echo Output in dist\ folder
echo.
goto :eof

:create_mod_jar
set LOADER=%1
set MC_VERSION=%2

REM Set loader directory name
set LOADER_DIR=
if "%LOADER%"=="fabric" set LOADER_DIR=Fabric
if "%LOADER%"=="forge" set LOADER_DIR=Forge
if "%LOADER%"=="neoforge" set LOADER_DIR=NeoForge

REM Skip Fabric for versions before 1.14 (Fabric didn't exist yet)
if "%LOADER%"=="fabric" (
    echo %MC_VERSION% | findstr /b "1.12" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.13" >nul && goto :eof
)

REM Skip NeoForge for versions before 1.20.1 (NeoForge didn't exist yet)
if "%LOADER%"=="neoforge" (
    echo %MC_VERSION% | findstr /b "1.12" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.13" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.14" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.15" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.16" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.17" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.18" >nul && goto :eof
    echo %MC_VERSION% | findstr /b "1.19" >nul && goto :eof
    if "%MC_VERSION%"=="1.20" goto :eof
)

set OUTPUT_NAME=retromod-%VERSION%+%MC_VERSION%.jar
mkdir "dist\%LOADER_DIR%\%MC_VERSION%" 2>nul

REM Create temp directory
set TEMP_DIR=%TEMP%\retromod-build-%LOADER%-%MC_VERSION%
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

REM Extract shaded JAR (includes all dependencies like ASM, Gson, TOML4J)
cd /d "%TEMP_DIR%"
jar xf "%~dp0%SHADED_JAR%" 2>nul
if %ERRORLEVEL% neq 0 (
    echo   FAILED: %MC_VERSION%
    set /a FAILED+=1
    cd /d "%~dp0"
    rmdir /s /q "%TEMP_DIR%" 2>nul
    goto :eof
)

REM Remove other loaders' files based on target loader
if "%LOADER%"=="fabric" (
    del /q "META-INF\neoforge.mods.toml" 2>nul
    del /q "META-INF\mods.toml" 2>nul
    del /q "pack.mcmeta" 2>nul
)
if "%LOADER%"=="forge" (
    del /q "fabric.mod.json" 2>nul
    del /q "META-INF\neoforge.mods.toml" 2>nul
)
if "%LOADER%"=="neoforge" (
    del /q "fabric.mod.json" 2>nul
    del /q "META-INF\mods.toml" 2>nul
)

REM Update manifest
mkdir META-INF 2>nul
(
echo Manifest-Version: 1.0
echo Implementation-Title: RetroMod
echo Implementation-Version: %VERSION%
echo RetroMod-Target-MC: %MC_VERSION%
echo RetroMod-Loader: %LOADER%
echo Automatic-Module-Name: retromod
) > META-INF\MANIFEST.MF

REM Repackage
jar cfm "%~dp0dist\%LOADER_DIR%\%MC_VERSION%\%OUTPUT_NAME%" META-INF\MANIFEST.MF . 2>nul
if %ERRORLEVEL% neq 0 (
    echo   FAILED: %MC_VERSION%
    set /a FAILED+=1
) else (
    echo   Created: %MC_VERSION%
    set /a TOTAL+=1
)

cd /d "%~dp0"
rmdir /s /q "%TEMP_DIR%" 2>nul
goto :eof
